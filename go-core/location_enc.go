package icloudcore

import (
	"encoding/base64"
	"time"

	"howett.net/plist"
)

// Building `CPLAsset.locationEnc` for uploads (ISS-043).
//
// ## What it is
//
// A serialized `CLLocation`, base64-wrapped and encoded as a **binary plist**. Measured on a
// genuine Apple upload (`IMG_2123.HEIC`, 187 bytes decoded) it holds exactly eight keys:
//
//	{
//	  course:    15.10289193302892   // CLLocation.course, degrees
//	  speed:     0.0                 // CLLocation.speed, m/s
//	  alt:       27.253329884908833  // CLLocation.altitude, metres
//	  lon:       120.17141666666667
//	  vertAcc:   0.0                 // CLLocation.verticalAccuracy
//	  lat:       35.955330000000004
//	  timestamp: <date> 2026-10-22T08:54:58Z   // CLLocation.timestamp, a plist DATE
//	  horzAcc:   3.9040194535831785  // CLLocation.horizontalAccuracy
//	}
//
// ## Why it matters
//
// `CPLMaster.mediaMetaDataEnc` carries a `{GPS}` dictionary too, and ours was already valid and
// complete enough for our own reader to display the coordinates. The web info panel still showed
// no map. The difference between a native Apple record and ours was this field: Apple writes
// `locationEnc` on **every** `CPLAsset` that has a fix, and we wrote none.
//
// So `{GPS}` is the EXIF view of the position; `locationEnc` is the photo library's own index of
// it, and that index is what the map is built from.
//
// Note the timestamp is a real plist `<date>`, not a string. Writing it as a string decodes as the
// wrong object class.

// BuildLocationEnc renders the `CPLAsset.locationEnc` payload for a capture.
//
// Returns "" when there is no usable coordinate, so callers omit the field rather than posting a
// location at the origin.
//
// `course`, `speed`, `hAcc` and `vAcc` default to 0 when unknown. Apple's own stationary captures
// report 0.0 for speed and vertical accuracy. A wrong accuracy figure only widens the displayed
// uncertainty circle, whereas a wrong coordinate would move the pin.
func BuildLocationEnc(
	lat, lon float64,
	altitude float64,
	course, speed float64,
	hAcc, vAcc float64,
	timestampMillis int64,
) string {
	// A 0,0 pair is what a redacted read looks like, and it is also a real place in the Gulf of
	// Guinea. Treating it as absent has been the rule throughout this codebase (ISS-041).
	if lat == 0 && lon == 0 {
		return ""
	}

	ts := timestampMillis
	if ts <= 0 {
		ts = time.Now().UnixMilli()
	}

	location := map[string]interface{}{
		"lat":       lat,
		"lon":       lon,
		"alt":       altitude,
		"course":    course,
		"speed":     speed,
		"horzAcc":   hAcc,
		"vertAcc":   vAcc,
		"timestamp": time.UnixMilli(ts).UTC(),
	}

	b, err := plist.Marshal(location, plist.BinaryFormat)
	if err != nil {
		return ""
	}

	// Self-check, same policy as mediaMetaDataEnc: never ship bytes a real parser cannot read.
	var verify map[string]interface{}
	if _, err := plist.Unmarshal(b, &verify); err != nil {
		return ""
	}
	if _, ok := verify["lat"]; !ok {
		return ""
	}
	// The date must come back as a date, not a string or an integer. A wrong class here is
	// exactly the kind of defect that only shows up on the receiving client.
	if _, ok := verify["timestamp"].(time.Time); !ok {
		return ""
	}

	return base64.StdEncoding.EncodeToString(b)
}
