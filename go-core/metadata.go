package icloudcore

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"

	"howett.net/plist"
)

// EXIF / GPS extraction from `CPLMaster.mediaMetaDataEnc` (see docs/REFERENCE.md §2A).
//
// Apple stores a *trimmed* CGImageProperties dictionary there, base64-wrapped and encoded as a
// binary plist (`bplist00`). It carries `{TIFF}` / `{Exif}` / `{GPS}` / `{PNG}` sub-dictionaries,
// which is where aperture, shutter, ISO, focal length, lens, capture time and coordinates live.
//
// Two facts drive the whole design of this file:
//
//  1. **Metadata is optional, and its absence is normal.** Measured on a real library: only 8 of
//     25 sampled records carried a `{GPS}` dictionary at all (screenshots and location-disabled
//     captures have none), and imports from third-party apps have no `mediaMetaDataEnc` at all.
//     So every failure path here returns `Success: true` with `HasMetadata: false` — a missing
//     plist is *not* a request failure, and must never be reported as one.
//
//  2. **Metadata is NOT fetched by the pager.** `defaultDesiredKeys` deliberately excludes
//     `mediaMetaDataEnc`: it measures 742–3859 bytes per record, so including it in
//     `/records/query` would add roughly 0.5–1.5 MB per 200-record page — over a hundred
//     megabytes across a full 15k-item scan. It is pulled on demand via `/records/lookup`
//     instead, which costs one round trip when the user actually opens the info panel.

// PhotoGPS is the position block. It is a pointer on [PhotoMetadata] so that "no GPS" is
// representable and disappears from the JSON entirely (`omitempty`), rather than being a struct of
// zeroes that the UI would have to disambiguate from real coordinates.
type PhotoGPS struct {
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
	Altitude  float64 `json:"altitude,omitempty"`
	Speed     float64 `json:"speed,omitempty"`
	// ImgDirection is the compass heading of the camera at capture, in degrees.
	ImgDirection float64 `json:"imgDirection,omitempty"`
	// HPositioningError is the reported horizontal accuracy in metres.
	HPositioningError float64 `json:"hPositioningError,omitempty"`
	// Timestamp is the GPS fix time as Apple stored it (`TimeStamp` + `DateStamp`), kept verbatim.
	Timestamp string `json:"timestamp,omitempty"`
}

// PhotoMetadata is the flat, Kotlin-friendly projection of one record's EXIF.
//
// Every numeric field is a float64 with `omitempty` — note that `omitempty` on a float also drops
// a legitimate 0, which is fine here: no real lens has f/0, and exposure time 0 means "absent".
// The UI is expected to render only the fields it actually receives (the user's requirement:
// "有多少显示多少"), so absence must be expressed by the field not being present at all.
type PhotoMetadata struct {
	ResultBase

	// Camera body / lens.
	CameraMake  string `json:"cameraMake,omitempty"`
	CameraModel string `json:"cameraModel,omitempty"`
	LensModel   string `json:"lensModel,omitempty"`

	// Exposure triangle and optics.
	FNumber       float64 `json:"fNumber,omitempty"`      // aperture, e.g. 1.85
	ExposureTime  float64 `json:"exposureTime,omitempty"` // shutter, in seconds, e.g. 0.00135
	ISO           int64   `json:"iso,omitempty"`
	FocalLength   float64 `json:"focalLength,omitempty"`   // mm
	FocalLength35 float64 `json:"focalLength35,omitempty"` // 35mm-equivalent mm
	ExposureBias  float64 `json:"exposureBias,omitempty"`
	Brightness    float64 `json:"brightness,omitempty"`

	// DateTimeOriginal is Apple's EXIF capture time, kept verbatim ("2026:10:21 11:03:54").
	// Deliberately NOT re-formatted or parsed into an epoch here: the separator style is a display
	// concern and converting it would require inventing a timezone the record does not carry.
	DateTimeOriginal string `json:"dateTimeOriginal,omitempty"`

	// Pixel dimensions and colour profile.
	PixelWidth  int64  `json:"pixelWidth,omitempty"`
	PixelHeight int64  `json:"pixelHeight,omitempty"`
	ColorModel  string `json:"colorModel,omitempty"`
	ProfileName string `json:"profileName,omitempty"`

	// Gps is nil when the record has no `{GPS}` dictionary.
	Gps *PhotoGPS `json:"gps,omitempty"`

	// HasMetadata is true when a plist was found and decoded. The UI uses it to decide whether to
	// show the "photo has no embedded metadata" hint in the file-info section.
	HasMetadata bool `json:"hasMetadata"`

	// OriginalFingerprint is surfaced for Phase 3 de-duplication, which needs no local hashing.
	OriginalFingerprint string `json:"originalFingerprint,omitempty"`
}

// MetadataResult is the batch response: masterRecordName -> metadata.
//
// Keyed by master record name rather than returned as a slice so the Kotlin side can look up by
// the id it already holds, without depending on ordering.
type MetadataResult struct {
	ResultBase
	Items map[string]PhotoMetadata `json:"items"`
}

// maxMetadataBatch caps how many records one lookup may request.
//
// `/records/lookup` has no documented `resultsLimit > 1` restriction (unlike `/records/query`,
// see PITFALLS §14), but an unbounded body would let a caller ask for the whole library in one
// request and land a multi-megabyte response on the JNI boundary. 20 is plenty for the viewer,
// which only ever needs the visible page.
const maxMetadataBatch = 20

// FetchPhotoMetadata returns EXIF/GPS for the given `CPLMaster` record names.
//
// Input is a JSON array of record names; output is [MetadataResult] JSON.
//
// Records that exist but carry no metadata are returned with `HasMetadata: false` rather than
// being omitted, so the caller can cache the negative result and avoid re-asking.
func FetchPhotoMetadata(masterRecordNamesJSON string) string {
	s := GetSession()
	if s == nil {
		return marshalMetadataResult(failBase(ErrCodeAuthExpired, 0, "No active session"))
	}

	var masterNames []string
	if err := json.Unmarshal([]byte(masterRecordNamesJSON), &masterNames); err != nil {
		return marshalMetadataResult(failBase(ErrCodeParse, 0, "Invalid master record names JSON: "+err.Error()))
	}
	if len(masterNames) == 0 {
		res, _ := json.Marshal(MetadataResult{
			ResultBase: okBase(),
			Items:      map[string]PhotoMetadata{},
		})
		return string(res)
	}
	if len(masterNames) > maxMetadataBatch {
		masterNames = masterNames[:maxMetadataBatch]
	}

	recQueries := make([]map[string]string, 0, len(masterNames))
	for _, name := range masterNames {
		recQueries = append(recQueries, map[string]string{"recordName": name})
	}

	lookupPayload := map[string]interface{}{
		"records": recQueries,
		"zoneID":  map[string]interface{}{"zoneName": "PrimarySync"},
		"desiredKeys": []string{
			"mediaMetaDataEnc",
			"mediaMetaDataType",
			"resOriginalFingerprint",
		},
	}

	reqBody, _ := json.Marshal(lookupPayload)
	reqURL := s.photosBaseURL("records/lookup")

	req, err := s.NewRequest(http.MethodPost, reqURL, string(reqBody))
	if err != nil {
		return marshalMetadataResult(failBase(ClassifyNetErr(err), 0, err.Error()))
	}

	resp, err := s.Client.Do(req)
	if err != nil {
		return marshalMetadataResult(failBase(ClassifyNetErr(err), 0, err.Error()))
	}
	defer resp.Body.Close()
	s.ExtractAndSaveCookies(resp)

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return marshalMetadataResult(failBase(ErrCodeNetwork, 0, err.Error()))
	}

	if resp.StatusCode != http.StatusOK {
		return marshalMetadataResult(failBase(
			ClassifyHTTPStatus(resp.StatusCode),
			resp.StatusCode,
			fmt.Sprintf("records/lookup HTTP %d: %s", resp.StatusCode, truncateForLog(string(bodyBytes))),
		))
	}

	var lResp struct {
		Records []CKRecord `json:"records"`
	}
	if err := json.Unmarshal(bodyBytes, &lResp); err != nil {
		return marshalMetadataResult(failBase(
			ErrCodeParse, resp.StatusCode, "Failed to parse records/lookup JSON: "+err.Error(),
		))
	}

	items := make(map[string]PhotoMetadata, len(lResp.Records))
	for _, rec := range lResp.Records {
		md := PhotoMetadata{
			HasMetadata:         false,
			OriginalFingerprint: rec.Fields["resOriginalFingerprint"].AsString(),
		}
		if decoded, ok := decodeMediaMetaData(rec.Fields["mediaMetaDataEnc"].AsString()); ok {
			md = decoded
			md.OriginalFingerprint = rec.Fields["resOriginalFingerprint"].AsString()
		}
		items[rec.RecordName] = md
	}

	res, _ := json.Marshal(MetadataResult{ResultBase: okBase(), Items: items})
	return string(res)
}

// marshalMetadataResult marshals a failure with an empty item map.
func marshalMetadataResult(base ResultBase) string {
	res, _ := json.Marshal(MetadataResult{ResultBase: base, Items: map[string]PhotoMetadata{}})
	return string(res)
}

// truncateForLog bounds an error body before it reaches a log line / errorMessage.
// Apple's error bodies are short, but this also guards against the JNI layer holding a large
// string for something nobody will read.
func truncateForLog(s string) string {
	const max = 400
	if len(s) <= max {
		return s
	}
	return s[:max] + "…"
}

// decodeMediaMetaData turns the base64(binary plist) field into a [PhotoMetadata].
//
// Returns ok=false for every "there is nothing here" case: blank field, bad base64, wrong magic,
// or an unparseable plist. A false return is not an error — see the file header.
func decodeMediaMetaData(enc string) (PhotoMetadata, bool) {
	enc = strings.TrimSpace(enc)
	if enc == "" {
		return PhotoMetadata{}, false
	}

	raw, err := base64.StdEncoding.DecodeString(enc)
	if err != nil {
		// Some records come back as raw binary already (defensive: never observed, but the cost of
		// handling it is one branch versus silently losing every field on such a record).
		raw = []byte(enc)
	}
	if len(raw) < 8 || string(raw[:8]) != "bplist00" {
		return PhotoMetadata{}, false
	}

	var props map[string]interface{}
	if _, err := plist.Unmarshal(raw, &props); err != nil {
		return PhotoMetadata{}, false
	}

	md := PhotoMetadata{HasMetadata: true}

	// Apple's key space, per CGImageProperties. Both spellings of the EXIF key occur in the wild
	// (`Exif` in the documented form, `{Exif}` in Apple's serialised dictionaries), so the lookup
	// helper accepts either.
	tiff := subDict(props, "TIFF")
	exif := subDict(props, "Exif")
	gps := subDict(props, "GPS")
	png := subDict(props, "PNG")

	// Camera body and lens.
	md.CameraMake = firstStringIn([]string{"Make"}, exif, tiff)
	md.CameraModel = firstStringIn([]string{"Model"}, exif, tiff)
	md.LensModel = firstString(exif, "LensModel")

	// Exposure triangle.
	md.FNumber = firstFloat(exif, "FNumber", "ApertureValue")
	md.ExposureTime = firstFloat(exif, "ExposureTime")
	md.ISO = firstInt(exif, "ISOSpeedRatings", "PhotographicSensitivity", "ISOSpeed")
	md.FocalLength = firstFloat(exif, "FocalLength")
	md.FocalLength35 = firstFloat(exif, "FocalLenIn35mmFilm", "FocalLengthIn35mmFilm")
	md.ExposureBias = firstFloat(exif, "ExposureBiasValue")
	md.Brightness = firstFloat(exif, "BrightnessValue")

	// Capture time. `DateTimeOriginal` is the authoritative one; `{TIFF}.DateTime` is the
	// fallback Apple writes more often than the EXIF tag.
	md.DateTimeOriginal = firstStringIn(
		[]string{"DateTimeOriginal", "DateTimeDigitized"}, exif,
	)
	if md.DateTimeOriginal == "" {
		md.DateTimeOriginal = firstString(tiff, "DateTime")
	}

	// Dimensions: EXIF first (it is the pixel grid of the original), TIFF as fallback.
	md.PixelWidth = firstInt(exif, "PixelXDimension")
	md.PixelHeight = firstInt(exif, "PixelYDimension")
	if md.PixelWidth == 0 {
		md.PixelWidth = firstInt(tiff, "PixelXDimension")
		md.PixelHeight = firstInt(tiff, "PixelYDimension")
	}

	md.ColorModel = firstString(png, "ColorModel")
	md.ProfileName = firstString(png, "ProfileName")

	if g := parseGPS(gps); g != nil {
		md.Gps = g
	}

	return md, true
}

// parseGPS builds a [PhotoGPS], or nil when the dictionary has no usable coordinate.
//
// IMPORTANT — the sign handling is not optional. Apple stores the magnitude in `Latitude` /
// `Longitude` and the hemisphere separately in `LatitudeRef` / `LongitudeRef`, as EXIF does.
// Reading the number alone puts every southern-hemisphere and western-hemisphere photo at its
// mirrored location (Sydney at 33.87 N instead of S, the Americas east of Greenwich). The refs are
// the documented CGImageProperties representation; where a writer instead pre-signed the value,
// applying the ref is idempotent for "N"/"E" and correct for the rest, so this stays safe.
func parseGPS(gps map[string]interface{}) *PhotoGPS {
	if gps == nil {
		return nil
	}

	lat, okLat := firstFloatOK(gps, "Latitude")
	lng, okLng := firstFloatOK(gps, "Longitude")

	// Some writers nest the pair as a two-element list; accept that too.
	if !okLat || !okLng {
		if pair, ok := gps["Latitude"].([]interface{}); ok && len(pair) >= 2 {
			lat, okLat = toFloat(pair[0])
			lng, okLng = toFloat(pair[1])
		}
	}
	if !okLat || !okLng {
		return nil
	}

	if ref, ok := toStr(gps["LatitudeRef"]); ok {
		if strings.EqualFold(strings.TrimSpace(ref), "S") && lat > 0 {
			lat = -lat
		}
	}
	if ref, ok := toStr(gps["LongitudeRef"]); ok {
		if strings.EqualFold(strings.TrimSpace(ref), "W") && lng > 0 {
			lng = -lng
		}
	}

	g := &PhotoGPS{Latitude: lat, Longitude: lng}
	g.Altitude = firstFloat(gps, "Altitude")
	g.Speed = firstFloat(gps, "Speed")
	g.ImgDirection = firstFloat(gps, "ImgDirection")
	g.HPositioningError = firstFloat(gps, "HPositioningError")

	// GPS time is split across two keys; join them when both are present.
	dateStamp := firstString(gps, "DateStamp")
	timeStamp := firstString(gps, "TimeStamp")
	if dateStamp != "" || timeStamp != "" {
		g.Timestamp = strings.TrimSpace(dateStamp + " " + timeStamp)
	}

	return g
}

// subDict resolves a nested dictionary by key, tolerating Apple's brace-decorated spelling.
//
// The wire format uses `{Exif}` while the CGImageProperties documentation writes `Exif`. Checking
// both costs nothing and removes a whole class of "works on my sample" bugs.
func subDict(props map[string]interface{}, name string) map[string]interface{} {
	for _, key := range []string{name, "{" + name + "}"} {
		if v, ok := props[key]; ok {
			if m, ok := v.(map[string]interface{}); ok {
				return m
			}
		}
	}
	return nil
}

// firstString returns the first non-empty string for any of keys in a single dictionary.
func firstString(dict map[string]interface{}, keys ...string) string {
	if dict == nil {
		return ""
	}
	for _, k := range keys {
		if s, ok := toStr(dict[k]); ok {
			if trimmed := strings.TrimSpace(s); trimmed != "" {
				return trimmed
			}
		}
	}
	return ""
}

// firstStringIn searches keys across several dictionaries, in priority order.
//
// This is spelled out rather than folded into a variadic trick so the call sites read plainly and
// the priority order is explicit at every use: the earlier dictionary wins.
func firstStringIn(keys []string, dicts ...map[string]interface{}) string {
	for _, d := range dicts {
		if s := firstString(d, keys...); s != "" {
			return s
		}
	}
	return ""
}

// firstFloat returns the first usable float for any of keys in dict, or 0.
func firstFloat(dict map[string]interface{}, keys ...string) float64 {
	v, _ := firstFloatOK(dict, keys...)
	return v
}

// firstFloatOK is firstFloat with an explicit presence flag, so callers can distinguish a genuine
// 0 from "not present" (GPS coordinates sit on the equator / prime meridian legitimately).
func firstFloatOK(dict map[string]interface{}, keys ...string) (float64, bool) {
	if dict == nil {
		return 0, false
	}
	for _, k := range keys {
		if v, ok := dict[k]; ok {
			if f, ok := toFloat(v); ok {
				return f, true
			}
		}
	}
	return 0, false
}

// firstInt returns the first usable integer for any of keys in dict, or 0.
func firstInt(dict map[string]interface{}, keys ...string) int64 {
	if dict == nil {
		return 0
	}
	for _, k := range keys {
		if v, ok := dict[k]; ok {
			// ISOSpeedRatings is famously serialised as a single-element array.
			if list, ok := v.([]interface{}); ok && len(list) > 0 {
				v = list[0]
			}
			if f, ok := toFloat(v); ok {
				return int64(f)
			}
		}
	}
	return 0
}

// toFloat coerces the numeric shapes a plist can produce (uint64/int64/float64/string).
func toFloat(v interface{}) (float64, bool) {
	switch n := v.(type) {
	case float64:
		return n, true
	case float32:
		return float64(n), true
	case int:
		return float64(n), true
	case int64:
		return float64(n), true
	case uint64:
		return float64(n), true
	case json.Number:
		f, err := n.Float64()
		return f, err == nil
	case string:
		var f float64
		if _, err := fmt.Sscanf(strings.TrimSpace(n), "%g", &f); err == nil {
			return f, true
		}
	}
	return 0, false
}

// toStr coerces the string shapes a plist can produce.
func toStr(v interface{}) (string, bool) {
	switch s := v.(type) {
	case string:
		return s, true
	case []byte:
		return string(s), true
	}
	return "", false
}
