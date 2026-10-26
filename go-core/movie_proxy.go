package icloudcore

import (
	"encoding/base64"
	"encoding/binary"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"howett.net/plist"
)

// `mediaMetaDataEnc` for **videos** is a completely different animal from the one for photos
// (ISS-040 follow-up).
//
// Measured on a native Apple record (`IMG_3051.mp4`): `mediaMetaDataType` is `AVAssetProxy` and
// the plist holds exactly three keys:
//
//	{ "moop": <data: the `ftyp` + `moov` boxes, concatenated>,
//	  "mpvr": 1,
//	  "name": "<CPLAsset recordName>.MP4" }
//
// `moop` is the movie header slice — the client reads track layout and **duration** out of it.
// Video uploaded without it plays, but shows `0:00` and no capture metadata in iCloud web,
// macOS Photos and iOS, which is exactly the symptom we were seeing.
//
// Note the key is `moop`, not `moov`. `mpvr` is a format version.

// maxMovieProxyBytes caps the header we are willing to embed.
//
// A `moov` box for a long recording is tens of KB; the one Apple shipped for a 64 s clip was
// 1857 bytes. The cap exists so a pathological or fragmented file cannot blow up the record size
// (CloudKit rejects oversized records, and the whole payload travels over the JNI boundary as a
// JSON string).
const maxMovieProxyBytes = 256 * 1024

// buildVideoMetaDataEnc produces the `AVAssetProxy` payload for a movie file.
//
// `qt` carries whatever QuickTime metadata the caller could source (make/model/date/location).
// It is injected into the `moov` we ship, because Apple renders a video's camera block from those
// atoms rather than from EXIF. An empty `qt` simply yields a proxy without them.
//
// Returns ok=false when the container cannot be parsed or has no `moov`; callers then omit the
// field rather than shipping a proxy the client cannot read.
//
// `codec` is returned alongside the payload even though it is not part of it. It comes from the
// same `moov` buffer, and the alternative - a second call to walk the container - would re-read
// a file that can be hundreds of megabytes. Empty when the codec could not be determined.
func buildVideoMetaDataEnc(
	f *os.File,
	fileSize int64,
	assetRecordName string,
	filename string,
	qt QuickTimeMeta,
) (enc string, codec string, ok bool) {
	proxy, err := extractMovieProxy(f, fileSize)
	if err != nil || len(proxy) == 0 {
		return "", "", false
	}

	// Read the codec before the proxy is mutated, so the result describes the file as it actually
	// is rather than as we rewrote it.
	codec = detectVideoCodec(proxy)

	// Prefer the container's own location atom when the caller had none. It is the same source
	// Apple's clients read, and it survives when MediaStore's columns are simply unset. It does
	// NOT bypass ACCESS_MEDIA_LOCATION - with the permission denied the atom is stripped from the
	// stream altogether (measured), so this is a convenience, not an escape hatch.
	if qt.LocationISO6709 == "" {
		qt.LocationISO6709 = extractUdtaLocation(proxy)
	}

	// Inject the QuickTime `moov/meta` block. Done before marshalling so the encoded proxy is the
	// single source of truth.
	proxy = injectQuickTimeMetadata(proxy, qt)

	ext := strings.ToUpper(filepath.Ext(filename))
	if ext == "" {
		ext = ".MP4"
	}

	props := map[string]interface{}{
		"moop": proxy,
		"mpvr": int64(1),
		"name": assetRecordName + ext,
	}

	b, err := plist.Marshal(props, plist.BinaryFormat)
	if err != nil {
		return "", codec, false
	}

	// Same guard as the photo path: never ship bytes a real parser cannot read.
	var verify map[string]interface{}
	if _, err := plist.Unmarshal(b, &verify); err != nil || len(verify) == 0 {
		return "", codec, false
	}
	if _, ok := verify["moop"].([]byte); !ok {
		return "", codec, false
	}

	return base64.StdEncoding.EncodeToString(b), codec, true
}

// extractMovieProxy returns the concatenated `ftyp` and `moov` boxes.
//
// Scans ISO-BMFF top-level boxes by seeking over their bodies, so a 900 MB video costs a handful
// of `ReadAt` calls rather than a full read.
func extractMovieProxy(f *os.File, fileSize int64) ([]byte, error) {
	if fileSize < 8 {
		return nil, fmt.Errorf("file too small to be a movie (%d bytes)", fileSize)
	}

	var ftyp []byte
	var moov []byte
	// A short trail of what was actually seen. When this function fails in the field there is no
	// debugger and no file to inspect, so the error message has to carry the evidence.
	var trail []string

	offset := int64(0)
	for offset+8 <= fileSize {
		var hdr [16]byte
		n, err := f.ReadAt(hdr[:8], offset)
		if err != nil && err != io.EOF {
			return nil, fmt.Errorf("read box header at %d: %w (seen %v)", offset, err, trail)
		}
		if n < 8 {
			return nil, fmt.Errorf("short box header at %d: got %d bytes (seen %v)", offset, n, trail)
		}

		size := int64(binary.BigEndian.Uint32(hdr[0:4]))
		boxType := string(hdr[4:8])
		headerLen := int64(8)

		switch size {
		case 0:
			// Box extends to end of file.
			size = fileSize - offset
		case 1:
			// 64-bit `largesize` follows the type.
			if _, err := f.ReadAt(hdr[8:16], offset+8); err != nil {
				return nil, fmt.Errorf("read largesize at %d: %w (seen %v)", offset, err, trail)
			}
			size = int64(binary.BigEndian.Uint64(hdr[8:16]))
			headerLen = 16
		}

		trail = append(trail, fmt.Sprintf("%s@%d+%d", boxType, offset, size))
		if len(trail) > 8 {
			trail = trail[len(trail)-8:]
		}

		if size < headerLen || offset+size > fileSize {
			// Truncated or non-conforming box; stop rather than guess.
			return nil, fmt.Errorf("bad box size %d at %d (fileSize %d, seen %v)", size, offset, fileSize, trail)
		}

		switch boxType {
		case "ftyp", "moov":
			if size > maxMovieProxyBytes {
				return nil, fmt.Errorf("%s box is %d bytes, over the %d cap", boxType, size, maxMovieProxyBytes)
			}
			body := make([]byte, size)
			if got, err := f.ReadAt(body, offset); err != nil && err != io.EOF {
				return nil, fmt.Errorf("read %s at %d: %w", boxType, offset, err)
			} else if int64(got) != size {
				return nil, fmt.Errorf("short read of %s at %d: got %d of %d bytes", boxType, offset, got, size)
			}
			if boxType == "ftyp" {
				ftyp = body
			} else {
				moov = body
			}
		}

		offset += size

		// Both boxes found: everything the client needs.
		if len(ftyp) > 0 && len(moov) > 0 {
			break
		}
	}

	if len(moov) == 0 {
		return nil, fmt.Errorf("no moov box found (fileSize %d, seen %v)", fileSize, trail)
	}

	out := make([]byte, 0, len(ftyp)+len(moov))
	out = append(out, ftyp...)
	out = append(out, moov...)
	return out, nil
}
