package icloudcore

import (
	"encoding/binary"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

// buildAppleMetaMoov assembles a `moov` carrying an Apple `mdta` meta block, plus a `udta` and a
// `free` box, so the borrow-from-free path can be exercised.
func buildAppleMetaMoov(t *testing.T, freeBytes int) []byte {
	t.Helper()

	entries := []quickTimeMetaKey{
		{name: "com.apple.quicktime.model", value: "iPhone SE (3rd generation)"},
		{name: "com.apple.quicktime.location.ISO6709", value: "+35.9552+120.1714/"},
		{name: "com.apple.quicktime.make", value: "Apple"},
	}
	metaBox := buildMetaBox(entries)

	mvhd := box("mvhd", make([]byte, 100))
	udta := box("udta", nil)
	free := box("free", make([]byte, freeBytes))

	body := append(append(append(append([]byte{}, mvhd...), udta...), metaBox...), free...)
	return box("moov", body)
}

func TestReadAppleCaptureMeta(t *testing.T) {
	moov := buildAppleMetaMoov(t, 0)
	got := readAppleCaptureMeta(moov)

	if got.Model != "iPhone SE (3rd generation)" {
		t.Errorf("Model = %q", got.Model)
	}
	if got.Make != "Apple" {
		t.Errorf("Make = %q", got.Make)
	}
	if got.LocationISO6709 != "+35.9552+120.1714/" {
		t.Errorf("Location = %q", got.LocationISO6709)
	}
}

func TestBuildUdtaAtoms(t *testing.T) {
	atoms := buildUdtaAtoms(appleCaptureMeta{
		Model:           "Galaxy Z Fold8 Ultra",
		LocationISO6709: "+35.9553+120.1715/",
	})

	xyz := findBox(atoms, 0, len(atoms), "\xa9xyz")
	if xyz == nil {
		t.Fatal("©xyz atom missing")
	}
	body := atoms[xyz.bodyStart():xyz.end()]
	// u16 length, u16 language, then the text.
	if n := binary.BigEndian.Uint16(body[0:2]); int(n) != len("+35.9553+120.1715/") {
		t.Errorf("©xyz length prefix = %d", n)
	}
	if lang := binary.BigEndian.Uint16(body[2:4]); lang != samsungLangCode {
		t.Errorf("©xyz language = %#x, want %#x", lang, samsungLangCode)
	}
	if got := string(body[4:]); got != "+35.9553+120.1715/" {
		t.Errorf("©xyz text = %q", got)
	}

	auth := findBox(atoms, 0, len(atoms), "auth")
	if auth == nil {
		t.Fatal("auth atom missing")
	}
	abody := atoms[auth.bodyStart():auth.end()]
	// u32 zero, u16 language, NUL-terminated UTF-8.
	for i := 0; i < 4; i++ {
		if abody[i] != 0 {
			t.Errorf("auth byte %d = %#x, want 0", i, abody[i])
		}
	}
	if lang := binary.BigEndian.Uint16(abody[4:6]); lang != samsungLangCode {
		t.Errorf("auth language = %#x, want %#x", lang, samsungLangCode)
	}
	if got := string(abody[6:]); got != "Galaxy Z Fold8 Ultra\x00" {
		t.Errorf("auth text = %q", got)
	}
}

func TestBuildUdtaAtomsSkipsInvalidLocation(t *testing.T) {
	// A redacted or malformed coordinate must not be written as a real place.
	atoms := buildUdtaAtoms(appleCaptureMeta{LocationISO6709: "0"})
	if findBox(atoms, 0, len(atoms), "\xa9xyz") != nil {
		t.Fatal("invalid location must be skipped")
	}
}

// TestNormalizeISO6709 pins the shape Samsung writes: latitude and longitude only, altitude
// dropped, coordinates preserved character for character.
func TestNormalizeISO6709(t *testing.T) {
	cases := []struct{ in, want string }{
		// Apple's own form, with altitude.
		{"+35.9552+120.1714+027.366/", "+35.9552+120.1714/"},
		// Samsung's own form, already two-component.
		{"+35.9553+120.1715/", "+35.9553+120.1715/"},
		// Southern/western hemispheres keep their signs.
		{"-33.8688+151.2093/", "-33.8688+151.2093/"},
		{"+35.9553-120.1715/", "+35.9553-120.1715/"},
		// Unusable input.
		{"", ""},
		{"0", ""},
	}
	for _, c := range cases {
		if got := normalizeISO6709(c.in); got != c.want {
			t.Errorf("normalizeISO6709(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

// TestRebuildMoovBorrowingFreeKeepsSize is the invariant the whole approach depends on: moov's
// total size must not change, because `stco`/`co64` hold absolute offsets into `mdat` and a
// resize would shift every media sample when moov precedes mdat.
func TestRebuildMoovBorrowingFreeKeepsSize(t *testing.T) {
	moov := buildAppleMetaMoov(t, 512)
	atoms := buildUdtaAtoms(appleCaptureMeta{
		Model:           "iPhone SE (3rd generation)",
		LocationISO6709: "+35.9552+120.1714/",
	})

	out, borrowed, ok := rebuildMoovBorrowingFree(moov, atoms)
	if !ok || !borrowed {
		t.Fatalf("expected a successful borrow, ok=%v borrowed=%v", ok, borrowed)
	}
	if len(out) != len(moov) {
		t.Fatalf("moov size changed: %d -> %d", len(moov), len(out))
	}

	// The atoms must be inside udta now.
	udta := findBox(out, 8, len(out), "udta")
	if udta == nil {
		t.Fatal("udta disappeared")
	}
	inner := out[udta.bodyStart():udta.end()]
	if findBox(inner, 0, len(inner), "\xa9xyz") == nil {
		t.Fatal("©xyz not folded into udta")
	}
	if findBox(inner, 0, len(inner), "auth") == nil {
		t.Fatal("auth not folded into udta")
	}

	// The free box must have shrunk but still be structurally valid.
	free := findBox(out, 8, len(out), "free")
	if free == nil {
		t.Fatal("free box disappeared")
	}
	if free.size != 512+8-len(atoms) {
		t.Fatalf("free size = %d, want %d", free.size, 512+8-len(atoms))
	}
}

func TestRebuildMoovBorrowingFreeNoSpace(t *testing.T) {
	moov := buildAppleMetaMoov(t, 8) // far too small
	out, borrowed, ok := rebuildMoovBorrowingFree(moov, buildUdtaAtoms(appleCaptureMeta{
		Model: "X", LocationISO6709: "+1.0000+1.0000/",
	}))
	if ok || borrowed {
		t.Fatal("must refuse when the free box is too small")
	}
	if len(out) != len(moov) {
		t.Fatal("must return the input untouched")
	}
}

func TestGrowMoovAddsUdtaWhenAbsent(t *testing.T) {
	moov := box("moov", box("mvhd", make([]byte, 100)))
	atoms := buildUdtaAtoms(appleCaptureMeta{Model: "iPhone SE (3rd generation)"})

	out := growMoov(moov, atoms)
	if len(out) <= len(moov) {
		t.Fatal("moov did not grow")
	}
	// Header size must match the payload.
	if int(binary.BigEndian.Uint32(out[0:4])) != len(out) {
		t.Fatalf("moov size header = %d, want %d", binary.BigEndian.Uint32(out[0:4]), len(out))
	}
	udta := findBox(out, 8, len(out), "udta")
	if udta == nil {
		t.Fatal("udta was not created")
	}
	if findBox(out[udta.bodyStart():udta.end()], 0, udta.size-8, "auth") == nil {
		t.Fatal("auth not written")
	}
}

// TestInjectAndroidVideoMetadataRealIPhone runs the whole path on a genuine iPhone clip and
// verifies the result still parses as a valid movie with moov at the same size.
//
//	IPHONE_MOV=/tmp/probe/dl_iphone.mov go test ./... -run TestInjectAndroidVideoMetadataRealIPhone -v
func TestInjectAndroidVideoMetadataRealIPhone(t *testing.T) {
	src := os.Getenv("IPHONE_MOV")
	if src == "" {
		t.Skip("IPHONE_MOV not set")
	}

	// Work on a copy: the function edits in place.
	dst := filepath.Join(t.TempDir(), "patched.mov")
	raw, err := os.ReadFile(src)
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	if err := os.WriteFile(dst, raw, 0o600); err != nil {
		t.Fatalf("write copy: %v", err)
	}

	out := InjectAndroidVideoMetadata(dst)

	var rep androidMetaReport
	if err := json.Unmarshal([]byte(out), &rep); err != nil {
		t.Fatalf("bad report %q: %v", out, err)
	}
	t.Logf("report: %s", out)

	if !rep.OK {
		t.Fatalf("injection refused: %s", rep.Reason)
	}
	if rep.Model == "" {
		t.Fatal("no model was translated")
	}

	patched, err := os.ReadFile(dst)
	if err != nil {
		t.Fatalf("re-read: %v", err)
	}

	// Size must be unchanged: the borrow-from-free path should have handled this file.
	if len(patched) != len(raw) {
		t.Fatalf("file size changed: %d -> %d (would break stco offsets)", len(raw), len(patched))
	}

	// The movie must still walk cleanly from the first byte to the last.
	boxes := topLevelBoxes(t, patched)
	moovBox, ok := boxes["moov"]
	if !ok {
		t.Fatal("moov lost")
	}
	if moovBox.off+moovBox.size != len(patched) {
		t.Fatalf("moov ends at %d, file is %d", moovBox.off+moovBox.size, len(patched))
	}

	// And the Samsung atoms must be readable back out of moov/udta.
	moov := patched[moovBox.off : moovBox.off+moovBox.size]
	udta := findBox(moov, 8, len(moov), "udta")
	if udta == nil {
		t.Fatal("udta missing after injection")
	}
	inner := moov[udta.bodyStart():udta.end()]
	if findBox(inner, 0, len(inner), "auth") == nil {
		t.Fatal("auth not present in moov/udta")
	}
	if findBox(inner, 0, len(inner), "\xa9xyz") == nil {
		t.Fatal("©xyz not present in moov/udta")
	}
	// Apple's own metadata must survive - we must not clobber what we read from.
	if readAppleCaptureMeta(moov).Model == "" {
		t.Fatal("Apple moov/meta was damaged")
	}
}
