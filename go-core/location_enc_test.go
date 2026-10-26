package icloudcore

import (
	"encoding/base64"
	"testing"
	"time"

	"howett.net/plist"
)

// appleLocationEnc is a real `CPLAsset.locationEnc` copied from a genuine Apple upload
// (`IMG_2123.HEIC`). It is the ground truth for both the key set and the value types - in
// particular `timestamp`, which is a plist DATE rather than a string.
const appleLocationEnc = "YnBsaXN0MDDYAQIDBAUGBwgJCgsMCg0OD1Zjb3Vyc2VVc3BlZWRTYWx0U2xvbld2ZXJ0QWNjU2xhdFl0aW1lc3RhbXBXaG9yekFj" +
	"YyNALjSuQF7DSyMAAAAAAAAAACNAO0DaOjLIayNAXgr4fZxUpyNAQfpIQOFxoDNByEUDcQAAACNADztujSCzSQgZICYqLjY6RExV" +
	"XmdweYIAAAAAAAABAQAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAiw=="

// TestLocationEncMatchesAppleShape decodes the reference payload and rebuilds it from the same
// numbers, then asserts the two describe the same location. Compared by value, not bytes: object
// ordering is an encoder detail, and requiring byte equality would pin us to one implementation
// rather than to the format.
func TestLocationEncMatchesAppleShape(t *testing.T) {
	raw, err := base64.StdEncoding.DecodeString(appleLocationEnc)
	if err != nil {
		t.Fatalf("decode reference: %v", err)
	}
	var want map[string]interface{}
	if _, err := plist.Unmarshal(raw, &want); err != nil {
		t.Fatalf("parse reference: %v", err)
	}

	// Sanity: the reference really is the shape this file claims.
	wantKeys := []string{"lat", "lon", "alt", "course", "speed", "horzAcc", "vertAcc", "timestamp"}
	for _, k := range wantKeys {
		if _, ok := want[k]; !ok {
			t.Fatalf("reference payload is missing %q; the documented shape is wrong", k)
		}
	}
	if _, ok := want["timestamp"].(time.Time); !ok {
		t.Fatalf("reference timestamp is %T, not time.Time", want["timestamp"])
	}

	lat := want["lat"].(float64)
	lon := want["lon"].(float64)
	alt := want["alt"].(float64)
	course := want["course"].(float64)
	horz := want["horzAcc"].(float64)
	ts := want["timestamp"].(time.Time).UnixMilli()

	enc := BuildLocationEnc(lat, lon, alt, course, 0, horz, 0, ts)
	if enc == "" {
		t.Fatal("BuildLocationEnc refused a valid location")
	}

	got, err := base64.StdEncoding.DecodeString(enc)
	if err != nil {
		t.Fatalf("decode ours: %v", err)
	}
	var have map[string]interface{}
	if _, err := plist.Unmarshal(got, &have); err != nil {
		t.Fatalf("parse ours: %v", err)
	}

	if len(have) != len(wantKeys) {
		t.Errorf("key count = %d, want %d: %v", len(have), len(wantKeys), have)
	}
	for _, k := range wantKeys {
		if _, ok := have[k]; !ok {
			t.Errorf("missing key %q", k)
		}
	}

	for _, k := range []string{"lat", "lon", "alt", "course", "horzAcc", "speed", "vertAcc"} {
		g, ok := have[k].(float64)
		if !ok {
			t.Errorf("%q is %T, want float64", k, have[k])
			continue
		}
		w, _ := want[k].(float64)
		if g != w {
			t.Errorf("%q = %v, want %v", k, g, w)
		}
	}

	if gts, ok := have["timestamp"].(time.Time); !ok {
		t.Errorf("timestamp is %T, want time.Time", have["timestamp"])
	} else if !gts.Equal(time.UnixMilli(ts).UTC()) {
		t.Errorf("timestamp = %v, want %v", gts, time.UnixMilli(ts).UTC())
	}
}

// TestLocationEncRejectsOrigin guards the redaction case: a permission-less read yields 0,0, which
// is also a real coordinate. Posting it would put the pin in the Gulf of Guinea (ISS-041).
func TestLocationEncRejectsOrigin(t *testing.T) {
	if got := BuildLocationEnc(0, 0, 0, 0, 0, 0, 0, 1750000000000); got != "" {
		t.Fatalf("expected refusal for 0,0, got %d chars", len(got))
	}
}

func TestLocationEncAcceptsSingleAxisZero(t *testing.T) {
	// A point on the equator or the prime meridian is legitimate and must not be dropped.
	if got := BuildLocationEnc(0, 120.1715, 0, 0, 0, 0, 0, 1750000000000); got == "" {
		t.Fatal("a point on the equator was refused")
	}
	if got := BuildLocationEnc(35.9553, 0, 0, 0, 0, 0, 0, 1750000000000); got == "" {
		t.Fatal("a point on the prime meridian was refused")
	}
}

func TestLocationEncDefaultsTimestamp(t *testing.T) {
	// A capture with no usable date still needs a valid <date>: CoreLocation treats a missing
	// timestamp as an unusable fix.
	enc := BuildLocationEnc(35.9553, 120.1715, 10, 0, 0, 0, 0, 0)
	if enc == "" {
		t.Fatal("expected a payload even without a timestamp")
	}
	raw, _ := base64.StdEncoding.DecodeString(enc)
	var have map[string]interface{}
	if _, err := plist.Unmarshal(raw, &have); err != nil {
		t.Fatalf("parse: %v", err)
	}
	if _, ok := have["timestamp"].(time.Time); !ok {
		t.Fatalf("timestamp is %T, want time.Time", have["timestamp"])
	}
}
