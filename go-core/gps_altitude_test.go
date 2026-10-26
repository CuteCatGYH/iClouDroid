package icloudcore

import (
	"encoding/base64"
	"testing"

	"howett.net/plist"
)

// `{GPS}` altitude is a **magnitude plus a sign flag**, not a signed number.
//
//	Altitude     float   non-negative height in metres
//	AltitudeRef  int     0 = above sea level, 1 = below
//
// Verified against a native Apple record, where `Altitude` decodes as `float` and `AltitudeRef`
// as `int`. Writing the signed value directly and dropping the ref loses the sign for anything at
// or below sea level: the Dead Sea (-430 m) reads back as +430 m.

// intFromPlist accepts whichever integer width the plist decoder produces for a small int.
func intFromPlist(t *testing.T, v interface{}) uint64 {
	t.Helper()
	switch n := v.(type) {
	case uint64:
		return n
	case int64:
		return uint64(n)
	case int:
		return uint64(n)
	default:
		t.Fatalf("expected an integer, got %T", v)
		return 0
	}
}

// gpsFrom renders metadata and returns the decoded `{GPS}` dictionary.
func gpsFrom(t *testing.T, altitude float64) map[string]interface{} {
	t.Helper()

	meta := sampleUploadMetadata()
	meta.Altitude = &altitude

	enc, ok := buildMediaMetaDataEnc(meta)
	if !ok {
		t.Fatal("buildMediaMetaDataEnc returned ok=false")
	}
	raw, err := base64.StdEncoding.DecodeString(enc)
	if err != nil {
		t.Fatalf("decode: %v", err)
	}
	var decoded map[string]interface{}
	if _, err := plist.Unmarshal(raw, &decoded); err != nil {
		t.Fatalf("parse: %v", err)
	}
	gps, ok := decoded["{GPS}"].(map[string]interface{})
	if !ok {
		t.Fatalf("missing {GPS}; keys = %v", keysOf(decoded))
	}
	return gps
}

func TestGpsAltitudeAboveSeaLevel(t *testing.T) {
	gps := gpsFrom(t, 89.0)

	alt, ok := gps["Altitude"].(float64)
	if !ok {
		t.Fatalf("Altitude is %T, want float64", gps["Altitude"])
	}
	if alt != 89.0 {
		t.Errorf("Altitude = %v, want 89", alt)
	}
	if ref := intFromPlist(t, gps["AltitudeRef"]); ref != 0 {
		t.Errorf("AltitudeRef = %d, want 0 (above sea level)", ref)
	}
}

func TestGpsAltitudeBelowSeaLevel(t *testing.T) {
	// Dead Sea shoreline. The caller has already folded TAG_GPS_ALTITUDE_REF into a negative
	// value, so this is what reaches the builder.
	gps := gpsFrom(t, -430.5)

	alt, _ := gps["Altitude"].(float64)
	if alt != 430.5 {
		t.Errorf("Altitude = %v, want 430.5 (magnitude, not signed)", alt)
	}
	if ref := intFromPlist(t, gps["AltitudeRef"]); ref != 1 {
		t.Errorf("AltitudeRef = %d, want 1 (below sea level)", ref)
	}
}

func TestGpsAltitudeZeroIsAbove(t *testing.T) {
	// Sea level itself is ref 0, not 1.
	gps := gpsFrom(t, 0)

	if alt, _ := gps["Altitude"].(float64); alt != 0 {
		t.Errorf("Altitude = %v, want 0", alt)
	}
	if ref := intFromPlist(t, gps["AltitudeRef"]); ref != 0 {
		t.Errorf("AltitudeRef = %d, want 0 for sea level", ref)
	}
}

// TestLocationEncKeepsSignedAltitude pins the asymmetry between the two payloads.
//
// `{GPS}` splits the sign into a ref, but `CLLocation.altitude` (which is what `locationEnc`
// carries) is a plain signed double - negative below sea level. Normalising that one the same way
// would make every below-sea-level capture report a positive altitude to CoreLocation.
func TestLocationEncKeepsSignedAltitude(t *testing.T) {
	enc := BuildLocationEnc(-33.8688, 151.2093, -15.5, 0, 0, 0, 0, 1750000000000)
	if enc == "" {
		t.Fatal("BuildLocationEnc refused a valid location")
	}
	raw, err := base64.StdEncoding.DecodeString(enc)
	if err != nil {
		t.Fatalf("decode: %v", err)
	}
	var got map[string]interface{}
	if _, err := plist.Unmarshal(raw, &got); err != nil {
		t.Fatalf("parse: %v", err)
	}
	alt, _ := got["alt"].(float64)
	if alt != -15.5 {
		t.Errorf("locationEnc alt = %v, want -15.5 (signed)", alt)
	}
}
