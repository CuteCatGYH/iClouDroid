package icloudcore

import (
	"encoding/binary"
	"fmt"
	"strings"
	"time"
)

// Injecting QuickTime metadata into the movie header we ship as `moop` (ISS-040 follow-up).
//
// ## Why this is needed
//
// A video's "camera info" block in iCloud web / macOS Photos / iOS is **not** EXIF. It is read
// from QuickTime `mdta` metadata: a `meta` box containing `hdlr` + `keys` + `ilst`, hung off
// `moov` (device-level) and `moov/trak` (lens-level). Measured on a genuine iPhone SE capture:
//
//	moov/meta         com.apple.quicktime.make / .model / .software / .creationdate
//	                  com.apple.quicktime.location.ISO6709 / location.accuracy.horizontal
//	moov/trak[0]/meta com.apple.quicktime.camera.lens_model
//	                  com.apple.quicktime.camera.focal_length.35mm_equivalent
//	                  com.apple.quicktime.camera.lens_irisfnumber
//
// An Android-recorded MP4 carries none of those; it has Samsung-private `udta` atoms (`smta`,
// `auth`, `cami`) and Android's own `com.android.*` keys, none of which Apple reads. So the asset
// plays fine and shows a duration, but the info panel reports "no camera information".
//
// ## Approach
//
// Append one `meta` box as the last child of `moov` and fix the `moov` size header. Nothing
// existing is rewritten or reordered, so a mistake here degrades to "Apple ignores the metadata"
// rather than "the movie no longer parses". The result is re-parsed before it is allowed into a
// record.

// QuickTimeMeta carries the values we can honestly source on Android.
//
// Deliberately absent: lens focal length / f-number / 35mm equivalent. `MediaMetadataRetriever`
// exposes no lens information for an arbitrary file, so inventing it would put wrong numbers in
// front of the user. The make/model/date/location below all come from either `Build` (the device
// performing the backup, i.e. the device that shot it) or the file's own atoms.
type QuickTimeMeta struct {
	Make             string
	Model            string
	Software         string
	CreationDate     string // ISO-8601 basic-with-offset: "2026-10-26T20:07:17+0800"
	LocationISO6709  string // "+35.9553+120.1715/"
	LocationAccuracy string // horizontal accuracy, metres
}

func (q QuickTimeMeta) empty() bool {
	return q.Make == "" && q.Model == "" && q.Software == "" &&
		q.CreationDate == "" && q.LocationISO6709 == ""
}

// quickTimeMetaKey is one `keys` entry.
type quickTimeMetaKey struct {
	name  string
	value string
}

// buildMetaBox renders a complete `meta` box: hdlr + keys + ilst.
//
// Layout mirrors Apple's byte for byte (verified against a real record):
//
//	meta
//	  hdlr  33 bytes, handler_type "mdta", single NUL name
//	  keys  version/flags 0, entry_count, then { size, "mdta", key }
//	  ilst  one child per key, its type being the 1-based key index
//	          data: size, "data", 0x00000001 (UTF-8), 0 (locale), payload
func buildMetaBox(entries []quickTimeMetaKey) []byte {
	if len(entries) == 0 {
		return nil
	}

	// hdlr
	hdlr := make([]byte, 0, 33)
	hdlr = append(hdlr, make([]byte, 8)...) // size + type, filled in below
	binary.BigEndian.PutUint32(hdlr[0:4], 33)
	copy(hdlr[4:8], "hdlr")
	hdlr = append(hdlr, 0, 0, 0, 0)          // version + flags
	hdlr = append(hdlr, 0, 0, 0, 0)          // pre_defined
	hdlr = append(hdlr, 'm', 'd', 't', 'a')  // handler_type
	hdlr = append(hdlr, make([]byte, 12)...) // reserved
	hdlr = append(hdlr, 0)                   // empty name

	// keys
	keysBody := make([]byte, 0, 64)
	keysBody = append(keysBody, 0, 0, 0, 0) // version + flags
	cnt := make([]byte, 4)
	binary.BigEndian.PutUint32(cnt, uint32(len(entries)))
	keysBody = append(keysBody, cnt...)
	for _, e := range entries {
		entry := make([]byte, 0, 8+len(e.name))
		sz := make([]byte, 4)
		binary.BigEndian.PutUint32(sz, uint32(8+len(e.name)))
		entry = append(entry, sz...)
		entry = append(entry, 'm', 'd', 't', 'a')
		entry = append(entry, e.name...)
		keysBody = append(keysBody, entry...)
	}
	keysBox := box("keys", keysBody)

	// ilst
	ilstBody := make([]byte, 0, 128)
	for i, e := range entries {
		payload := make([]byte, 0, 16+len(e.value))
		payload = append(payload, 0, 0, 0, 1) // data type: UTF-8
		payload = append(payload, 0, 0, 0, 0) // locale (unused)
		payload = append(payload, e.value...)
		dataBox := box("data", payload)

		child := make([]byte, 0, 8+len(dataBox))
		sz := make([]byte, 4)
		binary.BigEndian.PutUint32(sz, uint32(8+len(dataBox)))
		child = append(child, sz...)
		// The child's "type" is the 1-based index into `keys`, not a FourCC.
		idx := make([]byte, 4)
		binary.BigEndian.PutUint32(idx, uint32(i+1))
		child = append(child, idx...)
		child = append(child, dataBox...)
		ilstBody = append(ilstBody, child...)
	}
	ilstBox := box("ilst", ilstBody)

	body := make([]byte, 0, len(hdlr)+len(keysBox)+len(ilstBox))
	body = append(body, hdlr...)
	body = append(body, keysBox...)
	body = append(body, ilstBox...)
	return box("meta", body)
}

func box(boxType string, body []byte) []byte {
	out := make([]byte, 8+len(body))
	binary.BigEndian.PutUint32(out[0:4], uint32(8+len(body)))
	copy(out[4:8], boxType)
	copy(out[8:], body)
	return out
}

// injectQuickTimeMetadata appends a `meta` box to the `moov` inside `proxy`.
//
// `proxy` is the `ftyp`||`moov` buffer produced by extractMovieProxy. Returns the input
// unchanged when it cannot be parsed or when there is nothing to add - never a half-built box.
func injectQuickTimeMetadata(proxy []byte, qt QuickTimeMeta) []byte {
	if qt.empty() || len(proxy) == 0 {
		return proxy
	}

	metaBox := buildMetaBox(qt.entries())
	if len(metaBox) == 0 {
		return proxy
	}

	// Locate `moov` among the top-level boxes of the proxy.
	off := 0
	for off+8 <= len(proxy) {
		size := int(binary.BigEndian.Uint32(proxy[off : off+4]))
		boxType := string(proxy[off+4 : off+8])
		if size < 8 || off+size > len(proxy) {
			return proxy
		}
		if boxType == "moov" {
			// Rebuild: existing moov children, minus any moov-level `meta`, plus ours.
			//
			// Existing must be dropped rather than kept alongside ours. An Android capture often
			// already carries a `moov/meta` (Samsung writes one holding `com.android.*` keys), and
			// two siblings of the same type make "which one does the reader use" ambiguous. Apple's
			// own records have exactly one, so we match that shape. Nothing is lost: the dropped
			// box only held Android-private keys that Apple never reads.
			inner := proxy[off+8 : off+size]
			newBody := make([]byte, 0, len(inner)+len(metaBox))

			p := 0
			for p+8 <= len(inner) {
				childSize := int(binary.BigEndian.Uint32(inner[p : p+4]))
				childType := string(inner[p+4 : p+8])
				if childSize < 8 || p+childSize > len(inner) {
					// Malformed sibling: keep the buffer intact rather than desyncing the walk.
					newBody = append(newBody, inner...)
					break
				}
				if childType != "meta" {
					newBody = append(newBody, inner[p:p+childSize]...)
				}
				p += childSize
			}
			newBody = append(newBody, metaBox...)

			newMoov := make([]byte, 0, 8+len(newBody))
			hdr := make([]byte, 8)
			binary.BigEndian.PutUint32(hdr[0:4], uint32(8+len(newBody)))
			copy(hdr[4:8], "moov")
			newMoov = append(newMoov, hdr...)
			newMoov = append(newMoov, newBody...)

			out := make([]byte, 0, off+len(newMoov))
			out = append(out, proxy[:off]...)
			out = append(out, newMoov...)
			return out
		}
		off += size
	}

	return proxy
}

// entries renders the non-empty values, in a stable order.
//
// Order matters only for the index references inside `ilst`, which buildMetaBox derives from
// position, so any order is valid; this one is alphabetical for reproducibility.
func (q QuickTimeMeta) entries() []quickTimeMetaKey {
	var out []quickTimeMetaKey
	add := func(name, value string) {
		if strings.TrimSpace(value) != "" {
			out = append(out, quickTimeMetaKey{name: name, value: value})
		}
	}
	// Sorted by key name, matching the alphabetical listing above.
	add("com.apple.quicktime.creationdate", q.CreationDate)
	add("com.apple.quicktime.location.ISO6709", q.LocationISO6709)
	add("com.apple.quicktime.location.accuracy.horizontal", q.LocationAccuracy)
	add("com.apple.quicktime.make", q.Make)
	add("com.apple.quicktime.model", q.Model)
	add("com.apple.quicktime.software", q.Software)
	return out
}

// formatQuickTimeDate renders Apple's `creationdate` form: "2026-10-26T20:07:17+0800".
//
// Local time with a numeric offset and **no colon in the offset**, which is not what
// time.RFC3339 produces.
func formatQuickTimeDate(epochMillis int64) string {
	if epochMillis <= 0 {
		return ""
	}
	return time.UnixMilli(epochMillis).Format("2006-01-02T15:04:05-0700")
}

// formatISO6709 renders coordinates the way Apple does: "+35.9553+120.1715/".
//
// Fixed at 4 decimals because that is what the reference record carries; the trailing slash is
// required and its absence makes the field unusable.
func formatISO6709(lat, lon float64) string {
	if lat == 0 && lon == 0 {
		return ""
	}
	return fmt.Sprintf("%+.4f%+.4f/", lat, lon)
}

// optionalFloat dereferences a nullable coordinate, treating nil as absent.
func optionalFloat(v *float64) float64 {
	if v == nil {
		return 0
	}
	return *v
}

// extractUdtaLocation pulls the ISO-6709 point out of `moov/udta/©xyz`.
//
// **This still requires `ACCESS_MEDIA_LOCATION`.** Measured on a real Samsung capture through the
// content-URI FD:
//
//	granted    -> udta/©xyz = "+35.9553+120.1715/"   (bytes 001215c72b33352e...)
//	denied     -> the atom is GONE from the stream  (findBox returns nil)
//
// So MediaProvider strips it rather than zeroing it, and there is no permission-free path to
// video GPS. An earlier version of this comment claimed otherwise; that was wrong.
//
// Keeping the extraction is still worthwhile: it is the same source Apple's clients read, it
// costs one buffer walk (the moov is already in memory), and it works for containers where the
// caller supplied no location (e.g. a file imported from elsewhere).
//
// Layout of the atom body: 2-byte string length, 2-byte language code, then the text.
// Returns "" when the box or the coordinates are absent.
func extractUdtaLocation(proxy []byte) string {
	moov := findBox(proxy, 0, len(proxy), "moov")
	if moov == nil {
		return ""
	}
	udta := findBox(proxy, moov.bodyStart(), moov.end(), "udta")
	if udta == nil {
		return ""
	}

	// "©xyz" is 0xA9 0x78 0x79 0x7A in Latin-1.
	xyz := findBox(proxy, udta.bodyStart(), udta.end(), "\xa9xyz")
	if xyz == nil {
		return ""
	}

	bodyStart := xyz.bodyStart()
	bodyEnd := xyz.end()
	if bodyEnd-bodyStart < 4 {
		return ""
	}
	text := strings.TrimSpace(string(proxy[bodyStart+4 : bodyEnd]))
	if !looksLikeISO6709(text) {
		return ""
	}
	return text
}

// looksLikeISO6709 accepts only the shape we are willing to write: a leading signed latitude.
//
// Guards against a truncated or non-conforming atom producing garbage that would then be fed to
// Apple as a real location.
func looksLikeISO6709(s string) bool {
	if len(s) < 8 {
		return false
	}
	if s[0] != '+' && s[0] != '-' {
		return false
	}
	return strings.Count(s, ".") >= 2
}

// boxSpanQT is a located ISO-BMFF box inside a buffer.
type boxSpanQT struct {
	start     int // box start (size field)
	headerLen int
	size      int
}

func (b boxSpanQT) bodyStart() int { return b.start + b.headerLen }
func (b boxSpanQT) end() int       { return b.start + b.size }

// findBox walks the direct children of [start, end) and returns the first box of [boxType].
func findBox(data []byte, start, end int, boxType string) *boxSpanQT {
	all := findBoxes(data, start, end, boxType)
	if len(all) == 0 {
		return nil
	}
	return all[0]
}

// findBoxes returns every direct child of [start, end) whose type is [boxType].
//
// Needed wherever "the first one found" is not good enough - a `moov` holds several `trak` boxes
// and only one of them is the video track.
func findBoxes(data []byte, start, end int, boxType string) []*boxSpanQT {
	var out []*boxSpanQT
	p := start
	for p+8 <= end {
		size := int(binary.BigEndian.Uint32(data[p : p+4]))
		typ := string(data[p+4 : p+8])
		headerLen := 8
		switch size {
		case 0:
			size = end - p
		case 1:
			if p+16 > end {
				return out
			}
			size = int(binary.BigEndian.Uint64(data[p+8 : p+16]))
			headerLen = 16
		}
		if size < headerLen || p+size > end {
			return out
		}
		if typ == boxType {
			out = append(out, &boxSpanQT{start: p, headerLen: headerLen, size: size})
		}
		p += size
	}
	return out
}
