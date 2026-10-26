package icloudcore

import (
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"time"

	"howett.net/plist"
)

// Building `CPLMaster.mediaMetaDataEnc` for uploads (ISS-040).
//
// Apple stores a trimmed `CGImageProperties` dictionary here, base64-wrapped and encoded as a
// **binary plist** (`bplist00`). The parse direction already lives in metadata.go; this file is
// the write direction and deliberately reuses the same library, `howett.net/plist`.
//
// ## Why the hand-rolled encoder that used to live in upload.go is gone
//
// It produced structurally invalid bytes. Two independent defects:
//
//  1. Object indices were off by one: sub-objects were numbered from 0 by `addObject`, but the
//     root dictionary was then spliced in at index 0, so every reference pointed at the previous
//     object. A standard parser reads the result as `{"<camera model>": "{TIFF}", ...}`.
//  2. The offset table used a hard-coded 1-byte width (`trailer[6]=1, trailer[7]=1`), so any
//     payload larger than 255 bytes had its offsets silently truncated — which is every payload
//     carrying real EXIF.
//
// Measured with Apple's own `plutil`: `Property List error: Unexpected character b`. Python's
// `plistlib` was worse than useless — it "succeeded" and returned garbage.
//
// The lesson this encodes: **never hand-roll a serialization format when a tested library for it
// is already a dependency.**

// buildMediaMetaDataEnc renders the media metadata plist and returns it base64-encoded.
//
// The second return value is false when there is nothing worth writing, or when the encoder
// failed. Callers must treat false as "omit the field": a missing `mediaMetaDataEnc` degrades
// gracefully (the info panel shows no camera block), whereas a corrupt one can take the whole
// asset down on Apple's side.
func buildMediaMetaDataEnc(meta UploadMetadata) (string, bool) {
	props := buildCGImageProperties(meta)
	if len(props) == 0 {
		return "", false
	}

	b, err := plist.Marshal(props, plist.BinaryFormat)
	if err != nil {
		return "", false
	}

	// Self-check before letting these bytes anywhere near a record. Round-tripping through the
	// same library is cheap (these payloads are ~1.4 KB) and would have caught both original
	// defects immediately: the off-by-one references fail to resolve, and truncated offsets
	// produce a file the decoder rejects.
	var verify map[string]interface{}
	if _, err := plist.Unmarshal(b, &verify); err != nil || len(verify) == 0 {
		return "", false
	}

	return base64.StdEncoding.EncodeToString(b), true
}

// buildCGImageProperties assembles the dictionary Apple's clients expect.
//
// Only fields we actually measured on a real Apple-created record are emitted, and any field
// whose source value is missing is omitted entirely rather than zero-filled — the panel is meant
// to render "however much there is" (see the note on [PhotoMetadata]).
func buildCGImageProperties(meta UploadMetadata) map[string]interface{} {
	root := map[string]interface{}{}

	// {TIFF} — always worth emitting: Make/Model are what the info panel shows first.
	tiff := map[string]interface{}{}
	if meta.Make != "" {
		tiff["Make"] = meta.Make
	}
	if meta.Model != "" {
		tiff["Model"] = meta.Model
	}
	if meta.Software != "" {
		tiff["Software"] = meta.Software
	}
	if meta.DateTimeOriginal != "" {
		tiff["DateTime"] = meta.DateTimeOriginal
	}
	if meta.Orientation > 0 {
		tiff["Orientation"] = int64(meta.Orientation)
	}
	if meta.Width > 0 {
		tiff["PixelXDimension"] = int64(meta.Width)
	}
	if meta.Height > 0 {
		tiff["PixelYDimension"] = int64(meta.Height)
	}
	tiff["XResolution"] = 72.0
	tiff["YResolution"] = 72.0
	tiff["ResolutionUnit"] = int64(2)
	if len(tiff) > 3 {
		root["{TIFF}"] = tiff
	}

	// {Exif} — the exposure triangle.
	exif := map[string]interface{}{}
	if meta.DateTimeOriginal != "" {
		exif["DateTimeOriginal"] = meta.DateTimeOriginal
		exif["DateTimeDigitized"] = meta.DateTimeOriginal
	}
	if meta.FNumber != nil {
		exif["FNumber"] = *meta.FNumber
	}
	if meta.ExposureTime != nil {
		exif["ExposureTime"] = *meta.ExposureTime
	}
	if meta.Iso != nil {
		// Apple uses an array here, not a scalar (measured: `ISOSpeedRatings: [250]`).
		exif["ISOSpeedRatings"] = []interface{}{int64(*meta.Iso)}
	}
	if meta.FocalLength != nil {
		exif["FocalLength"] = *meta.FocalLength
	}
	if meta.FocalLength35 != nil {
		exif["FocalLenIn35mmFilm"] = int64(*meta.FocalLength35)
	}
	if meta.LensModel != "" {
		exif["LensModel"] = meta.LensModel
	}
	if meta.Width > 0 {
		exif["PixelXDimension"] = int64(meta.Width)
	}
	if meta.Height > 0 {
		exif["PixelYDimension"] = int64(meta.Height)
	}
	exif["ColorSpace"] = int64(65535)
	exif["ExifVersion"] = []interface{}{int64(2), int64(2)}
	if len(exif) > 2 {
		root["{Exif}"] = exif
	}

	// {GPS} — refs are mandatory. Apple stores the value plus a hemisphere letter; without them
	// southern/western coordinates land mirrored on the map.
	//
	// A `0,0` pair is treated as absent: the platform redacts location by zeroing the rationals
	// rather than dropping the tags, so a permission-less read looks like a valid position exactly
	// at the origin. A wrong pin is worse than no pin.
	if meta.Lat != nil && meta.Lon != nil && (*meta.Lat != 0 || *meta.Lon != 0) {
		gps := map[string]interface{}{
			"Latitude":     *meta.Lat,
			"Longitude":    *meta.Lon,
			"LatitudeRef":  hemisphereRef(*meta.Lat, "N", "S"),
			"LongitudeRef": hemisphereRef(*meta.Lon, "E", "W"),
		}
		if meta.Altitude != nil {
			// EXIF represents altitude as a **non-negative magnitude plus a separate ref**, and
			// `{GPS}` follows that: `Altitude` is the magnitude, `AltitudeRef` is 0 above sea
			// level and 1 below. Writing the signed value directly and omitting the ref loses the
			// sign for everything south of, or below, sea level - the Dead Sea (-430 m) reads back
			// as +430 m.
			//
			// The caller already applies the sign (Kotlin folds `TAG_GPS_ALTITUDE_REF` into the
			// value it passes), so splitting it back out here is not double work: this is the
			// one place that knows the target format.
			//
			// Apple writes `AltitudeRef` as an integer, not a float - verified against a native
			// record, where it decodes as `int` while `Altitude` decodes as `float`.
			alt := *meta.Altitude
			ref := int64(0)
			if alt < 0 {
				alt = -alt
				ref = 1
			}
			gps["Altitude"] = alt
			gps["AltitudeRef"] = ref
		}
		root["{GPS}"] = gps
	}

	// Top-level dimension mirrors and bit depth, both present on every real record.
	if meta.Width > 0 {
		root["PixelWidth"] = int64(meta.Width)
	}
	if meta.Height > 0 {
		root["PixelHeight"] = int64(meta.Height)
	}
	root["Depth"] = int64(8)

	// A dictionary with only the four boilerplate keys carries no information; treat it as
	// "nothing to say" so we do not ship an empty-looking metadata block.
	hasRealContent := meta.Make != "" || meta.Model != "" || meta.LensModel != "" ||
		meta.FNumber != nil || meta.ExposureTime != nil || meta.Iso != nil ||
		meta.FocalLength != nil || meta.DateTimeOriginal != "" || meta.Lat != nil
	if !hasRealContent {
		return nil
	}

	return root
}

func hemisphereRef(value float64, positive string, negative string) string {
	if value < 0 {
		return negative
	}
	return positive
}

// utiForJPEG is the UTI Apple uses for every server-side JPEG derivative.
//
// Note this is NOT a MIME type. `resJPEGMedFileType` / `resJPEGThumbFileType` must read
// `public.jpeg`; writing `image/jpeg` is one of the defects fixed in ISS-040.
const utiForJPEG = "public.jpeg"

// newUUIDv4 mints a lowercase UUID for record names.
//
// Apple's own records use plain UUIDs. We previously used `ASSET-<unix nanos>`, which is
// visibly non-conforming and leaks into the web URL
// (`/photos/.../l,pz,ASSET-1793008755144003404,15939/`).
//
// Built from crypto/rand with the version/variant bits patched per RFC 4122.
func newUUIDv4() string {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		// crypto/rand does not fail in practice; fall back to a time-seeded value rather
		// than returning an empty record name, which CloudKit would reject outright.
		n := time.Now().UnixNano()
		for i := range b {
			b[i] = byte(n >> (8 * (i % 8)))
		}
	}
	b[6] = (b[6] & 0x0f) | 0x40 // version 4
	b[8] = (b[8] & 0x3f) | 0x80 // RFC 4122 variant
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}

func boolToInt(v bool) int64 {
	if v {
		return 1
	}
	return 0
}

// timeZoneOffsetSeconds returns the UTC offset the asset was captured at.
//
// Apple stores this on `CPLAsset` and the client renders capture times against it; without it
// the timestamp shows up shifted by the device offset.
func timeZoneOffsetSeconds(epochMillis int64) int64 {
	_, offset := time.UnixMilli(epochMillis).Zone()
	return int64(offset)
}

// timeZoneNameEnc renders the base64-wrapped zone label Apple stores in
// `CPLAsset.timeZoneNameEnc`.
//
// Two forms are present in real records, and both are accepted by the clients:
//
//	"Asia/Shanghai"  - on an imported asset (web uploader)
//	"GMT+0800"       - on a genuine iPhone capture
//
// We emit the `GMT` form, whose only variable is the numeric offset.
func timeZoneNameEnc(epochMillis int64) string {
	_, offset := time.UnixMilli(epochMillis).Zone()
	sign := "+"
	if offset < 0 {
		offset = -offset
		sign = "-"
	}
	// No colon: Apple writes "GMT+0800", not "GMT+08:00".
	name := fmt.Sprintf("GMT%s%02d%02d", sign, offset/3600, (offset%3600)/60)
	return base64.StdEncoding.EncodeToString([]byte(name))
}
