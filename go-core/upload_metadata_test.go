package icloudcore

import (
	"encoding/base64"
	"os"
	"strings"
	"testing"

	"howett.net/plist"
)

// Regression guards for ISS-040.
//
// Uploaded assets were undisplayable in iCloud web / macOS Photos / iOS (grey tile, then an
// exclamation mark) while every resource downloaded fine and our own client rendered them
// normally. Two independent causes are covered here:
//
//  1. `mediaMetaDataEnc` was produced by a hand-rolled binary-plist encoder whose object
//     references were off by one and whose offset table used a 1-byte width. Apple's `plutil`
//     rejected the result outright.
//  2. `resOriginalFileType` / `resJPEG*FileType` were written as MIME types
//     (`image/heic`, `image/jpeg`) where Apple stores UTIs (`public.heic`, `public.jpeg`).

func sampleUploadMetadata() UploadMetadata {
	fn := 1.7
	et := 0.0099913239954161
	iso := 250
	fl := 6.25
	fl35 := 69
	lat := 35.95524
	lon := 120.17133833333334
	alt := 12.5

	return UploadMetadata{
		AssetDate:            1793013550085,
		OriginalCreationDate: 1793013550085,
		Width:                4000,
		Height:               3000,
		Orientation:          6,
		ItemType:             "public.heic",
		Make:                 "samsung",
		Model:                "Galaxy Z Fold8 Ultra",
		Software:             "F9760ZCU3AZI9",
		LensModel:            "Galaxy Z Fold8 Ultra Wide-angle lens 6.250mm f/1.70",
		FNumber:              &fn,
		ExposureTime:         &et,
		Iso:                  &iso,
		FocalLength:          &fl,
		FocalLength35:        &fl35,
		Altitude:             &alt,
		DateTimeOriginal:     "2026:10:26 19:19:10",
		Lat:                  &lat,
		Lon:                  &lon,
	}
}

// TestBuildMediaMetaDataEncRoundTrip is the direct guard for the corrupt-plist defect.
//
// The old encoder "succeeded" too - it only fell apart under a real parser, and even then
// python's plistlib returned confidently-wrong data rather than raising. Decoding with the same
// library and asserting the *structure* is therefore the only assertion that catches it.
func TestBuildMediaMetaDataEncRoundTrip(t *testing.T) {
	meta := sampleUploadMetadata()

	enc, ok := buildMediaMetaDataEnc(meta)
	if !ok {
		t.Fatal("buildMediaMetaDataEnc returned ok=false for a fully populated metadata set")
	}

	raw, err := base64.StdEncoding.DecodeString(enc)
	if err != nil {
		t.Fatalf("output is not valid base64: %v", err)
	}
	if string(raw[:8]) != "bplist00" {
		t.Fatalf("expected binary plist magic bplist00, got %q", raw[:8])
	}

	var decoded map[string]interface{}
	if _, err := plist.Unmarshal(raw, &decoded); err != nil {
		t.Fatalf("Apple-style parser rejected our plist: %v", err)
	}

	tiff, ok := decoded["{TIFF}"].(map[string]interface{})
	if !ok {
		t.Fatalf("missing {TIFF} dictionary; top-level keys = %v", keysOf(decoded))
	}
	if got := tiff["Make"]; got != "samsung" {
		t.Errorf("{TIFF}.Make = %v, want samsung", got)
	}
	if got := tiff["Model"]; got != "Galaxy Z Fold8 Ultra" {
		t.Errorf("{TIFF}.Model = %v, want Galaxy Z Fold8 Ultra", got)
	}
	if got := toInt(tiff["Orientation"]); got != 6 {
		t.Errorf("{TIFF}.Orientation = %v, want 6", tiff["Orientation"])
	}

	exif, ok := decoded["{Exif}"].(map[string]interface{})
	if !ok {
		t.Fatalf("missing {Exif} dictionary; top-level keys = %v", keysOf(decoded))
	}
	if got := exif["DateTimeOriginal"]; got != "2026:10:26 19:19:10" {
		t.Errorf("{Exif}.DateTimeOriginal = %v", got)
	}
	if got := toFloat64(exif["FNumber"]); got < 1.69 || got > 1.71 {
		t.Errorf("{Exif}.FNumber = %v, want ~1.7", exif["FNumber"])
	}

	// ISOSpeedRatings must be an ARRAY. Apple writes `[250]`, and a scalar is a shape
	// mismatch that a lenient reader would not notice.
	isoRaw, ok := exif["ISOSpeedRatings"]
	if !ok {
		t.Fatal("{Exif}.ISOSpeedRatings missing")
	}
	isoSlice, ok := isoRaw.([]interface{})
	if !ok {
		t.Fatalf("{Exif}.ISOSpeedRatings must be an array, got %T (%v)", isoRaw, isoRaw)
	}
	if len(isoSlice) != 1 || toInt(isoSlice[0]) != 250 {
		t.Errorf("{Exif}.ISOSpeedRatings = %v, want [250]", isoSlice)
	}

	gps, ok := decoded["{GPS}"].(map[string]interface{})
	if !ok {
		t.Fatalf("missing {GPS} dictionary; top-level keys = %v", keysOf(decoded))
	}
	if got := gps["LatitudeRef"]; got != "N" {
		t.Errorf("{GPS}.LatitudeRef = %v, want N", got)
	}
	if got := gps["LongitudeRef"]; got != "E" {
		t.Errorf("{GPS}.LongitudeRef = %v, want E", got)
	}
}

// TestBuildMediaMetaDataEncLargePayload targets the second defect of the old encoder: its
// offset table was written with a hard-coded 1-byte width, so every payload above 255 bytes had
// silently truncated offsets. A realistic EXIF block is ~1.4 KB, i.e. always in the broken range.
func TestBuildMediaMetaDataEncLargePayload(t *testing.T) {
	meta := sampleUploadMetadata()
	meta.Model = "Galaxy Z Fold8 Ultra Ultra Wide-angle lens with a deliberately very long name"
	meta.LensModel = meta.Model + " and then some more text to push the payload well past 255 bytes"

	enc, ok := buildMediaMetaDataEnc(meta)
	if !ok {
		t.Fatal("buildMediaMetaDataEnc returned ok=false")
	}
	raw, err := base64.StdEncoding.DecodeString(enc)
	if err != nil {
		t.Fatalf("not valid base64: %v", err)
	}
	if len(raw) <= 255 {
		t.Fatalf("test payload is only %d bytes; it must exceed 255 to exercise the old bug", len(raw))
	}

	var decoded map[string]interface{}
	if _, err := plist.Unmarshal(raw, &decoded); err != nil {
		t.Fatalf("payload of %d bytes failed to parse: %v", len(raw), err)
	}
}

// TestBuildMediaMetaDataEncOmitsWhenEmpty pins the "omit rather than corrupt" contract: missing
// metadata degrades gracefully (no camera block in the info panel), whereas a malformed block
// can take the whole asset down on Apple's side.
func TestBuildMediaMetaDataEncOmitsWhenEmpty(t *testing.T) {
	if _, ok := buildMediaMetaDataEnc(UploadMetadata{Width: 100, Height: 100}); ok {
		t.Fatal("buildMediaMetaDataEnc must report ok=false when there is no real EXIF content")
	}
}

// TestExtractRecordErrors covers the "HTTP 200 but the record failed" case that used to be
// recorded as SUCCESS in `upload_queue`.
func TestExtractRecordErrors(t *testing.T) {
	t.Run("clean response", func(t *testing.T) {
		body := `{"records":[{"recordName":"A","recordType":"CPLMaster"}]}`
		if got := extractRecordErrors(body); got != "" {
			t.Fatalf("expected no error, got %q", got)
		}
	})

	t.Run("per-record server error", func(t *testing.T) {
		body := `{"records":[{"recordName":"A","recordType":"CPLMaster","serverErrorCode":"BAD_REQUEST","reason":"Invalid field"}]}`
		got := extractRecordErrors(body)
		if got == "" {
			t.Fatal("expected a per-record error to be surfaced")
		}
		if !strings.Contains(got, "BAD_REQUEST") || !strings.Contains(got, "CPLMaster") {
			t.Fatalf("error message lost detail: %q", got)
		}
	})

	t.Run("unparseable body is not an error", func(t *testing.T) {
		// AGENTS 7.1: never infer failure from raw text.
		if got := extractRecordErrors("<html>502 Bad Gateway</html>"); got != "" {
			t.Fatalf("unparseable body must not be reported as a record error, got %q", got)
		}
	})
}

// TestDumpPlistForExternalVerify writes the encoded payload so it can be checked with Apple's
// own `plutil`. Kept because the previous encoder passed every in-process check and only failed
// under an external parser - in-process round-trips were not enough then, and they are not
// enough now.
//
//	PLIST_OUT=/tmp/probe.plist go test ./... -run TestDumpPlistForExternalVerify
//	plutil -p /tmp/probe.plist
func TestDumpPlistForExternalVerify(t *testing.T) {
	out := os.Getenv("PLIST_OUT")
	if out == "" {
		t.Skip("PLIST_OUT not set")
	}
	enc, ok := buildMediaMetaDataEnc(sampleUploadMetadata())
	if !ok {
		t.Fatal("buildMediaMetaDataEnc returned ok=false")
	}
	raw, err := base64.StdEncoding.DecodeString(enc)
	if err != nil {
		t.Fatalf("not valid base64: %v", err)
	}
	if err := os.WriteFile(out, raw, 0o600); err != nil {
		t.Fatalf("write %s: %v", out, err)
	}
	t.Logf("wrote %d bytes to %s", len(raw), out)
}

func keysOf(m map[string]interface{}) []string {
	out := make([]string, 0, len(m))
	for k := range m {
		out = append(out, k)
	}
	return out
}

func toInt(v interface{}) int64 {
	switch n := v.(type) {
	case int64:
		return n
	case int:
		return int64(n)
	case uint64:
		return int64(n)
	case float64:
		return int64(n)
	}
	return -9999
}

func toFloat64(v interface{}) float64 {
	switch n := v.(type) {
	case float64:
		return n
	case int64:
		return float64(n)
	case int:
		return float64(n)
	}
	return -9999
}
