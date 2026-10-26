package icloudcore

import (
	"encoding/binary"
	"encoding/json"
	"fmt"
	"os"
	"strconv"
	"strings"
)

// Translating Apple QuickTime metadata into Android-readable `udta` atoms (ISS-042).
//
// ## The problem
//
// A video downloaded from iCloud is byte-identical to what was uploaded (verified: the record's
// `resOriginalFileSize` matches the local file exactly, and a round-tripped iPhone `.MOV` keeps
// its 1660-byte `moov/meta`). Nothing is lost - it is simply in a form Android does not read.
//
// Apple writes capture info as QuickTime `mdta`: a `meta` box holding `hdlr` + `keys` + `ilst`.
// Samsung Gallery (and Android's MediaStore indexer) look at `udta` atoms instead, which is why
// a perfectly intact iPhone clip shows no camera, no lens and no location.
//
// ## What we can reproduce exactly
//
// Two atoms were reverse-engineered from a real Samsung capture and can be rebuilt byte for
// byte; both are implemented here:
//
//	udta/©xyz  -> u16 len, u16 lang, ISO-6709 text          "+35.9553+120.1715/"
//	udta/auth  -> u32 0, u16 lang, UTF-8 text, NUL          "Galaxy Z Fold8 Ultra\0"
//
// `udta/smta` (device code `SM-F9760` + lens focal length) is deliberately NOT written: only one
// sample exists, its TLV framing is not yet pinned down, and a guessed layout would show garbage
// rather than a missing field. It is a separate, later increment.
//
// `udta/cami` is also skipped: its payload (`"3, 2, 2192, 534, 1.0"`) has unknown semantics.
//
// ## The one invariant that matters
//
// `moov` may sit **before** `mdat`, and `stbl/stco` holds absolute file offsets into it. Growing
// `moov` in that layout shifts every media sample and the file stops playing. So:
//
//   - if a `free` box inside `moov` is large enough, its bytes are borrowed and `moov` keeps its
//     exact size - always safe, whatever the layout;
//   - otherwise, `moov` is grown only when it is the LAST top-level box, so nothing after it
//     moves;
//   - otherwise the file is left untouched and the reason is reported.

// samsungLangCode is the 16-bit language tag Samsung writes into `udta` atoms.
//
// Measured value 0x15C7 on the reference capture. It is QuickTime's packed ISO-639-2/T ("und",
// undetermined) with the Macintosh language flag set, which is what both Samsung and Apple emit
// for these atoms.
const samsungLangCode = 0x15C7

// appleCaptureMeta is the subset of Apple's `moov/meta` we translate.
type appleCaptureMeta struct {
	Make            string
	Model           string
	Software        string
	CreationDate    string
	LocationISO6709 string
	FocalLength35mm string
	LensModel       string
}

func (a appleCaptureMeta) empty() bool {
	return a.Model == "" && a.Make == "" && a.LocationISO6709 == ""
}

// readAppleCaptureMeta extracts the `com.apple.quicktime.*` values from a `moov` box.
//
// Two levels matter, and they carry different things (measured on a real iPhone capture):
//
//	moov/meta          make, model, software, creationdate, location.ISO6709
//	moov/trak[*]/meta  camera.lens_model, camera.focal_length.35mm_equivalent
//
// Reading only the `moov`-level box - which an earlier version did - silently loses the focal
// length, and with it the only value Samsung's lens field can be built from.
//
// Returns a zero value when nothing is present; callers then add nothing rather than writing
// placeholder text.
func readAppleCaptureMeta(moov []byte) appleCaptureMeta {
	out := readCaptureMetaAt(moov, findBox(moov, 8, len(moov), "meta"))

	// Track-level metadata holds the lens. The video track is normally first, but every track is
	// checked so a container with an audio track ahead of it still resolves.
	for _, trak := range findBoxes(moov, 8, len(moov), "trak") {
		if meta := findBox(moov, trak.bodyStart(), trak.end(), "meta"); meta != nil {
			lens := readCaptureMetaAt(moov, meta)
			if out.FocalLength35mm == "" {
				out.FocalLength35mm = lens.FocalLength35mm
			}
			if out.LensModel == "" {
				out.LensModel = lens.LensModel
			}
		}
	}
	return out
}

// readCaptureMetaAt parses one `meta` box (hdlr + keys + ilst) into the fields we translate.
func readCaptureMetaAt(moov []byte, meta *boxSpanQT) appleCaptureMeta {
	var out appleCaptureMeta
	if meta == nil {
		return out
	}

	// QuickTime `meta` has no version/flags word; ISO `meta` does. Try the direct layout first
	// and fall back to the 4-byte skip, so both flavours parse.
	start := meta.bodyStart()
	if findBox(moov, start, meta.end(), "hdlr") == nil {
		start += 4
	}

	keys := readMetaKeys(moov, start, meta.end())
	if len(keys) == 0 {
		return out
	}

	ilst := findBox(moov, start, meta.end(), "ilst")
	if ilst == nil {
		return out
	}

	// ilst children cannot be matched by type: their "type" field is a 1-based index into
	// `keys`, so they have to be walked positionally.
	p := ilst.bodyStart()
	for p+8 <= ilst.end() {
		size := int(binary.BigEndian.Uint32(moov[p : p+4]))
		if size < 8 || p+size > ilst.end() {
			break
		}
		idx := int(binary.BigEndian.Uint32(moov[p+4 : p+8]))
		if idx >= 1 && idx <= len(keys) {
			if v, ok := readIlstData(moov, p+8, p+size); ok {
				switch keys[idx-1] {
				case "com.apple.quicktime.make":
					out.Make = v
				case "com.apple.quicktime.model":
					out.Model = v
				case "com.apple.quicktime.software":
					out.Software = v
				case "com.apple.quicktime.creationdate":
					out.CreationDate = v
				case "com.apple.quicktime.location.ISO6709":
					out.LocationISO6709 = v
				case "com.apple.quicktime.camera.focal_length.35mm_equivalent":
					out.FocalLength35mm = v
				case "com.apple.quicktime.camera.lens_model":
					out.LensModel = v
				}
			}
		}
		p += size
	}
	return out
}

// readMetaKeys returns the key names declared by a `keys` box, in order.
func readMetaKeys(data []byte, start, end int) []string {
	keys := findBox(data, start, end, "keys")
	if keys == nil {
		return nil
	}
	p := keys.bodyStart()
	if p+8 > keys.end() {
		return nil
	}
	count := int(binary.BigEndian.Uint32(data[p+4 : p+8]))
	p += 8

	out := make([]string, 0, count)
	for i := 0; i < count && p+8 <= keys.end(); i++ {
		size := int(binary.BigEndian.Uint32(data[p : p+4]))
		if size < 8 || p+size > keys.end() {
			break
		}
		out = append(out, string(data[p+8:p+size]))
		p += size
	}
	return out
}

// readIlstData unwraps an `ilst` entry's `data` box and returns its UTF-8 payload.
func readIlstData(data []byte, start, end int) (string, bool) {
	d := findBox(data, start, end, "data")
	if d == nil {
		return "", false
	}
	// data payload: type(4) + locale(4) + bytes
	p := d.bodyStart()
	if p+8 > d.end() {
		return "", false
	}
	return string(data[p+8 : d.end()]), true
}

// smtaTemplatePrefix is bytes 0..45 of a real Samsung `smta` box body.
//
// Measured identical across four captures from the same device (three focal lengths plus an
// earlier clip), which is what makes patching only the variable bytes defensible: this is not a
// guessed layout, it is Samsung's own output reproduced verbatim.
//
// Structure, by byte offset (verified against four captures, identical in all of them):
//
//	0..3    u32 0
//	4..7    u32 14          entry length for "saut"
//	8..11   "saut"
//	12..19  8 zero bytes
//	20..21  u16 16          entry length for "mdln"
//	22..25  "mdln"
//	26..33  device code, NUL padded to 8
//	34..35  00 00           padding to a 2-byte boundary
//	36..37  u16 17          entry length for "lens"
//	38..41  "lens"
//	42..45  u32 0
//
// Followed by the variable tail: lens id (u16 LE), focal (u16 LE), and the low byte of a second
// focal. Samsung's own box is 51 bytes even though that last field implies 52 - the trailing byte
// is simply absent. That off-by-one is reproduced rather than "fixed": a reader tuned to
// Samsung's own output may depend on the shape it was written against.
var smtaTemplatePrefix = []byte{
	0x00, 0x00, 0x00, 0x00, // 0..3   flags
	0x00, 0x00, 0x00, 0x0e, // 4..7   entry length 14
	's', 'a', 'u', 't', // 8..11
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 12..19
	0x00, 0x10, // 20..21 entry length 16
	'm', 'd', 'l', 'n', // 22..25
	0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 26..33 device code, filled in below
	0x00, 0x00, // 34..35 padding
	0x00, 0x11, // 36..37 entry length 17
	'l', 'e', 'n', 's', // 38..41
	0x00, 0x00, 0x00, 0x00, // 42..45
}

// samsungLensID maps a 35mm-equivalent focal length onto the lens id Samsung records.
//
// Calibrated from three captures on one device, where `cami`'s second field and `smta`'s lens id
// agreed in all three:
//
//	ultra-wide 0.6x -> id 3, 13 mm
//	main       1.0x -> id 2, 23 mm
//	tele       3.0x -> id 4, 69 mm
//
// The id selects the *label* the info panel shows; the millimetres come from the focal value. So a
// wrong id would mislabel the lens, while the number would still be right.
func samsungLensID(focal35 int) byte {
	switch {
	case focal35 <= 0:
		return 0
	case focal35 < 20:
		return 3 // ultra-wide
	case focal35 <= 45:
		return 2 // wide / main - covers a typical phone main camera, iPhone SE included
	default:
		return 4 // tele
	}
}

// buildSmtaBox renders Samsung's `smta` box for a capture.
//
// `deviceCode` fills the 8-byte `mdln` field. Returns nil when the focal length is unusable: the
// field stores a single byte, so anything above 255 mm cannot be represented without truncating
// into a wrong number, and a wrong number is worse than a missing line.
func buildSmtaBox(deviceCode string, focal35 int) []byte {
	if focal35 <= 0 || focal35 > 255 {
		return nil
	}

	body := make([]byte, len(smtaTemplatePrefix), len(smtaTemplatePrefix)+5)
	copy(body, smtaTemplatePrefix)

	// mdln: fixed 8-byte field, NUL padded, truncated if longer.
	code := []byte(deviceCode)
	if len(code) > 8 {
		code = code[:8]
	}
	copy(body[26:34], code)

	lensID := samsungLensID(focal35)
	if lensID == 0 {
		return nil
	}

	// Tail: lens id (u16 LE), focal (u16 LE), then the low byte of a second focal.
	body = append(body,
		lensID, 0x00,
		byte(focal35), 0x00,
		byte(focal35),
	)
	return box("smta", body)
}

// buildUdtaAtoms renders the `©xyz`, `auth` and `smta` atoms for a capture.
//
// Each is a complete box, ready to be appended inside a `udta` container. Order matches the
// Samsung reference capture closely enough for readers that walk children by type.
func buildUdtaAtoms(apple appleCaptureMeta) []byte {
	var out []byte

	// ©xyz - location. Body: u16 text length, u16 language, ISO-6709 string.
	//
	// Apple appends altitude as a third signed component (`+35.9552+120.1714+027.366/`), while
	// Samsung's own atom carries latitude and longitude only. Normalise to the two-component form
	// so the atom is byte-shaped like one Samsung wrote itself.
	if loc := normalizeISO6709(apple.LocationISO6709); loc != "" {
		body := make([]byte, 4, 4+len(loc))
		binary.BigEndian.PutUint16(body[0:2], uint16(len(loc)))
		binary.BigEndian.PutUint16(body[2:4], samsungLangCode)
		body = append(body, loc...)
		out = append(out, box("\xa9xyz", body)...)
	}

	// auth - the marketing device name shown in the info panel. Body: u32 0, u16 language,
	// NUL-terminated UTF-8.
	if name := strings.TrimSpace(apple.Model); name != "" {
		body := make([]byte, 6, 6+len(name)+1)
		binary.BigEndian.PutUint16(body[4:6], samsungLangCode)
		body = append(body, name...)
		body = append(body, 0)
		out = append(out, box("auth", body)...)
	}

	// smta - device code plus the lens, which is what puts the focal length in the info panel.
	//
	// `mdln` gets the Apple make rather than a Samsung code. We are describing an Apple capture,
	// and claiming `SM-F9760` would be a lie; the panel's model line comes from `auth` anyway
	// (confirmed: an iPhone clip with no `smta` at all already shows its model).
	if focal, err := strconv.Atoi(strings.TrimSpace(apple.FocalLength35mm)); err == nil {
		if smta := buildSmtaBox(strings.TrimSpace(apple.Make), focal); smta != nil {
			out = append(out, smta...)
		}
	}

	return out
}

// normalizeISO6709 reduces an ISO-6709 point to the two-component form Samsung writes.
//
// Apple records latitude, longitude AND altitude (`+35.9552+120.1714+027.366/`); Samsung's own
// `©xyz` carries only the first two (`+35.9553+120.1715/`). Keeping the altitude would make our
// atom a shape no Samsung device produces, so it is dropped - the coordinates themselves are
// preserved character for character rather than reformatted, to avoid changing precision.
//
// Returns "" when the input is not a usable point.
func normalizeISO6709(raw string) string {
	s := strings.TrimSpace(raw)
	if s == "" {
		return ""
	}

	// A signed number: optional sign, digits, optional fraction.
	var parts []string
	i := 0
	for i < len(s) {
		c := s[i]
		if c != '+' && c != '-' {
			i++
			continue
		}
		start := i
		i++
		for i < len(s) && (s[i] >= '0' && s[i] <= '9' || s[i] == '.') {
			i++
		}
		if i > start+1 {
			parts = append(parts, s[start:i])
		}
	}

	if len(parts) < 2 {
		return ""
	}
	out := parts[0] + parts[1] + "/"
	if !looksLikeISO6709(out) {
		return ""
	}
	return out
}

// androidMetaReport is what the injection returns to the caller, for logging.
type androidMetaReport struct {
	OK       bool   `json:"ok"`
	Reason   string `json:"reason,omitempty"`
	Model    string `json:"model,omitempty"`
	Location string `json:"location,omitempty"`
	// Added lists the atom names that were written.
	Added []string `json:"added,omitempty"`
	// Bytes is how much the moov grew; 0 when the space was borrowed from a `free` box.
	Bytes int `json:"bytes"`
}

// InjectAndroidVideoMetadata rewrites the video at `path` in place so Android gallery apps can
// show capture information that Apple keeps in QuickTime `mdta` metadata.
//
// Returns a JSON report. The function is safe to call on any video: when there is nothing to
// translate, or the layout cannot be changed without breaking playback, the file is left
// byte-identical and the reason is reported.
func InjectAndroidVideoMetadata(path string) string {
	f, err := os.OpenFile(path, os.O_RDWR, 0)
	if err != nil {
		report := androidMetaReport{OK: false, Reason: "open: " + err.Error()}
		b, _ := json.Marshal(report)
		return string(b)
	}
	defer f.Close()
	return injectAndroidVideoMetadata(f)
}

// InjectAndroidVideoMetadataFD is the same operation over an already-open descriptor.
//
// This is the shape the Android side uses: it downloads straight into MediaStore and patches the
// pending file through `openFileDescriptor(uri, "rw")`, so the bytes never take a second trip
// through a temp file. Takes ownership of `fd` and closes it.
func InjectAndroidVideoMetadataFD(fd int64) string {
	f := os.NewFile(uintptr(fd), "media")
	if f == nil {
		report := androidMetaReport{OK: false, Reason: "os.NewFile returned nil"}
		b, _ := json.Marshal(report)
		return string(b)
	}
	defer f.Close()
	return injectAndroidVideoMetadata(f)
}

func injectAndroidVideoMetadata(f *os.File) string {
	report := androidMetaReport{}
	fail := func(reason string) string {
		report.OK = false
		report.Reason = reason
		b, _ := json.Marshal(report)
		return string(b)
	}

	info, err := f.Stat()
	if err != nil {
		return fail("stat: " + err.Error())
	}
	fileSize := info.Size()

	moovOffset, moovSize, moovIsLast, err := locateMoov(f, fileSize)
	if err != nil {
		return fail(err.Error())
	}
	if moovSize > maxMovieProxyBytes {
		return fail(fmt.Sprintf("moov is %d bytes, over the %d cap", moovSize, maxMovieProxyBytes))
	}

	moov := make([]byte, moovSize)
	if _, err := f.ReadAt(moov, moovOffset); err != nil {
		return fail("read moov: " + err.Error())
	}

	apple := readAppleCaptureMeta(moov)
	if apple.empty() {
		return fail("no com.apple.quicktime metadata to translate")
	}
	report.Model = apple.Model
	report.Location = apple.LocationISO6709

	atoms := buildUdtaAtoms(apple)
	if len(atoms) == 0 {
		return fail("nothing translatable (no model and no location)")
	}

	// Prefer keeping moov's size unchanged by borrowing `free` space. This is layout-agnostic:
	// nothing outside moov can move, so no chunk offset needs fixing.
	newMoov, borrowed, ok := rebuildMoovBorrowingFree(moov, atoms)
	if !ok {
		if !moovIsLast {
			return fail("no free box to borrow and moov is not the last box; refusing to shift mdat")
		}
		newMoov = growMoov(moov, atoms)
	}

	if borrowed {
		report.Bytes = 0
	} else {
		report.Bytes = len(newMoov) - len(moov)
		if !moovIsLast {
			return fail("internal: grew moov but it is not last; refusing")
		}
	}

	if _, err := f.WriteAt(newMoov, moovOffset); err != nil {
		return fail("write moov: " + err.Error())
	}
	if !borrowed {
		// Extending only ever happens when moov is last, so truncating to the new length is the
		// only tail change needed.
		if err := f.Truncate(moovOffset + int64(len(newMoov))); err != nil {
			return fail("truncate: " + err.Error())
		}
	}

	report.OK = true
	if strings.TrimSpace(apple.LocationISO6709) != "" {
		report.Added = append(report.Added, "\u00a9xyz")
	}
	if strings.TrimSpace(apple.Model) != "" {
		report.Added = append(report.Added, "auth")
	}
	if findBox(atoms, 0, len(atoms), "smta") != nil {
		report.Added = append(report.Added, "smta")
	}
	b, _ := json.Marshal(report)
	return string(b)
}

// locateMoov finds the `moov` box's offset and size, and whether it is the file's last box.
func locateMoov(f *os.File, fileSize int64) (offset, size int64, isLast bool, err error) {
	var hdr [16]byte
	off := int64(0)
	for off+8 <= fileSize {
		if _, rerr := f.ReadAt(hdr[:8], off); rerr != nil {
			return 0, 0, false, fmt.Errorf("read box header at %d: %w", off, rerr)
		}
		boxSize := int64(binary.BigEndian.Uint32(hdr[0:4]))
		boxType := string(hdr[4:8])
		headerLen := int64(8)
		switch boxSize {
		case 0:
			boxSize = fileSize - off
		case 1:
			if _, rerr := f.ReadAt(hdr[8:16], off+8); rerr != nil {
				return 0, 0, false, fmt.Errorf("read largesize at %d: %w", off, rerr)
			}
			boxSize = int64(binary.BigEndian.Uint64(hdr[8:16]))
			headerLen = 16
		}
		if boxSize < headerLen || off+boxSize > fileSize {
			return 0, 0, false, fmt.Errorf("bad box size %d at %d", boxSize, off)
		}
		if boxType == "moov" {
			return off, boxSize, off+boxSize == fileSize, nil
		}
		off += boxSize
	}
	return 0, 0, false, fmt.Errorf("no moov box found")
}

// rebuildMoovBorrowingFree appends `atoms` to `moov/udta` while keeping moov's total size
// identical, by shrinking a `free` box by the same number of bytes.
//
// Returns ok=false when there is no `free` box with enough spare space, or no `udta` to extend.
func rebuildMoovBorrowingFree(moov, atoms []byte) ([]byte, bool, bool) {
	need := len(atoms)

	// Find a `free` box with at least `need` spare bytes.
	var freeStart, freeSize int
	p := 8
	for p+8 <= len(moov) {
		size := int(binary.BigEndian.Uint32(moov[p : p+4]))
		if size < 8 || p+size > len(moov) {
			return moov, false, false
		}
		if string(moov[p+4:p+8]) == "free" && size-8 >= need {
			freeStart, freeSize = p, size
			break
		}
		p += size
	}
	if freeSize == 0 {
		return moov, false, false
	}

	// Rebuild the child list: udta grows by `need`, the free box shrinks by `need`.
	out := make([]byte, 0, len(moov))
	out = append(out, moov[0:8]...) // moov header, size unchanged

	wroteUdta := false
	p = 8
	for p+8 <= len(moov) {
		size := int(binary.BigEndian.Uint32(moov[p : p+4]))
		typ := string(moov[p+4 : p+8])

		switch {
		case typ == "udta" && !wroteUdta:
			body := make([]byte, 0, size-8+need)
			body = append(body, moov[p+8:p+size]...)
			body = append(body, atoms...)
			out = append(out, box("udta", body)...)
			wroteUdta = true

		case p == freeStart:
			// Shrink the free box; keep its header, drop `need` bytes of padding.
			newSize := freeSize - need
			hdr := make([]byte, 8)
			binary.BigEndian.PutUint32(hdr[0:4], uint32(newSize))
			copy(hdr[4:8], "free")
			out = append(out, hdr...)
			out = append(out, make([]byte, newSize-8)...)

		default:
			out = append(out, moov[p:p+size]...)
		}
		p += size
	}

	if !wroteUdta {
		return moov, false, false
	}
	if len(out) != len(moov) {
		// The invariant this whole approach rests on. Bail rather than ship a mangled file.
		return moov, false, false
	}
	return out, true, true
}

// growMoov appends `atoms` to `moov/udta`, adding a `udta` box when the movie has none, and
// fixes the `moov` size header. Only safe when moov is the file's last box.
func growMoov(moov, atoms []byte) []byte {
	udta := findBox(moov, 8, len(moov), "udta")

	var body []byte
	if udta != nil {
		body = make([]byte, 0, len(moov)-8+len(atoms))
		body = append(body, moov[8:udta.start]...)
		newUdtaBody := make([]byte, 0, udta.size-8+len(atoms))
		newUdtaBody = append(newUdtaBody, moov[udta.bodyStart():udta.end()]...)
		newUdtaBody = append(newUdtaBody, atoms...)
		body = append(body, box("udta", newUdtaBody)...)
		body = append(body, moov[udta.end():]...)
	} else {
		body = make([]byte, 0, len(moov)-8+len(atoms)+8)
		body = append(body, moov[8:]...)
		body = append(body, box("udta", atoms)...)
	}

	hdr := make([]byte, 8)
	binary.BigEndian.PutUint32(hdr[0:4], uint32(8+len(body)))
	copy(hdr[4:8], "moov")
	out := append(hdr, body...)
	return out
}
