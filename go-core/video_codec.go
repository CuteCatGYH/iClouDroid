package icloudcore

import "encoding/binary"

// Video codec detection for `CPLMaster.codec` (ISS-040 follow-up).
//
// ## Why the container is the only reliable source
//
// The value used to be derived from the MIME type reported by `MediaMetadataRetriever`. That
// yields `video/mp4`, which names the **container**, not the codec, so the mapping produced
// nothing and the field was simply omitted. Measured on a real Samsung capture:
//
//	CPLMaster.codec                     -> <MISSING>
//	resOriginalFileType                 -> "public.mpeg-4"   (container)
//	MediaMetadataRetriever MIMETYPE     -> "video/mp4"        (container)
//
// The same probe showed how unreliable the retriever really is: the key it labels MANUFACTURER
// (40) returned `video/hevc` for this file, while MODEL (41) returned null. So the codec is
// read out of `stsd` instead, which is authoritative and already in memory on the upload path.
//
// Apple's own records use plain marketing names: `HEVC`, not `H.265`, and `H.264`, not `AVC`.
// The client renders this string verbatim, so an unrecognised value shows as a wrong codec name
// rather than a missing one.

// codecFromFourCC maps an ISO-BMFF sample-entry FourCC to Apple's `codec` string.
//
// Both the `hvc1` and `hev1` variants are HEVC; they differ only in whether parameter sets are
// in-band, which the client does not care about for display purposes.
func codecFromFourCC(fourcc string) string {
	switch fourcc {
	case "hvc1", "hev1":
		return "HEVC"
	case "avc1", "avc2", "avc3", "avc4":
		return "H.264"
	case "av01":
		return "AV1"
	case "vp08", "vp09":
		return "VP9"
	case "mp4v":
		return "MPEG-4"
	default:
		return ""
	}
}

// detectVideoCodec reads the codec of the first video track in a `ftyp`+`moov` buffer.
//
// Returns "" when the container has no video track or the FourCC is not one we recognise;
// callers omit the field rather than writing a guess.
func detectVideoCodec(proxy []byte) string {
	moov := findBox(proxy, 0, len(proxy), "moov")
	if moov == nil {
		return ""
	}

	for _, trak := range findBoxes(proxy, moov.bodyStart(), moov.end(), "trak") {
		mdia := findBox(proxy, trak.bodyStart(), trak.end(), "mdia")
		if mdia == nil {
			continue
		}

		// Only a `vide` handler track carries a video sample description. Audio, timecode and
		// metadata tracks also have `stsd` boxes, and treating the first track found as the video
		// one was how this class of bug starts.
		hdlr := findBox(proxy, mdia.bodyStart(), mdia.end(), "hdlr")
		if hdlr == nil || hdlr.bodyStart()+12 > hdlr.end() {
			continue
		}
		if string(proxy[hdlr.bodyStart()+8:hdlr.bodyStart()+12]) != "vide" {
			continue
		}

		minf := findBox(proxy, mdia.bodyStart(), mdia.end(), "minf")
		if minf == nil {
			continue
		}
		stbl := findBox(proxy, minf.bodyStart(), minf.end(), "stbl")
		if stbl == nil {
			continue
		}
		stsd := findBox(proxy, stbl.bodyStart(), stbl.end(), "stsd")
		if stsd == nil {
			continue
		}

		// stsd payload: version+flags(4), entry_count(4), then entries of size(4)+fourcc(4).
		p := stsd.bodyStart() + 8
		if p+8 > stsd.end() {
			continue
		}
		entrySize := int(binary.BigEndian.Uint32(proxy[p : p+4]))
		if entrySize < 8 || p+entrySize > stsd.end() {
			continue
		}
		return codecFromFourCC(string(proxy[p+4 : p+8]))
	}
	return ""
}
