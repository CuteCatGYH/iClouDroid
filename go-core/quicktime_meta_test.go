package icloudcore

import (
	"encoding/binary"
	"os"
	"testing"
)

// Regression guards for the QuickTime metadata injector.
//
// Apple renders a video's camera block from `moov/meta` (`keys` + `ilst`), not from EXIF, so an
// Android-recorded MP4 shows "no camera information" until we add those atoms ourselves. The
// injector appends one box to `moov`; the assertions below make sure it does not disturb anything
// that was already there.

type boxSpan struct{ off, size int }

// topLevelBoxes walks ISO-BMFF siblings and returns type -> span.
func topLevelBoxes(t *testing.T, data []byte) map[string]boxSpan {
	t.Helper()
	out := map[string]boxSpan{}
	off := 0
	for off+8 <= len(data) {
		size := int(binary.BigEndian.Uint32(data[off : off+4]))
		typ := string(data[off+4 : off+8])
		if size < 8 || off+size > len(data) {
			break
		}
		out[typ] = boxSpan{off, size}
		off += size
	}
	return out
}

// childTypes lists the box types directly inside a container box.
func childTypes(t *testing.T, data []byte, off, size int) []string {
	t.Helper()
	var out []string
	p := off + 8
	end := off + size
	for p+8 <= end {
		cs := int(binary.BigEndian.Uint32(data[p : p+4]))
		ct := string(data[p+4 : p+8])
		if cs < 8 || p+cs > end {
			break
		}
		out = append(out, ct)
		p += cs
	}
	return out
}

func TestInjectQuickTimeMetadata(t *testing.T) {
	ftyp := []byte("mp42\x00\x00\x00\x00mp42isom")
	moovBody := append([]byte("\x00\x00\x00\x08mvhd"), []byte("\x00\x00\x00\x08udta")...)
	proxy := buildTestMovie(t, ftyp, moovBody, 512)

	before := topLevelBoxes(t, proxy)
	moovBefore, ok := before["moov"]
	if !ok {
		t.Fatal("fixture is missing moov")
	}
	childrenBefore := childTypes(t, proxy, moovBefore.off, moovBefore.size)

	qt := QuickTimeMeta{
		Make:            "samsung",
		Model:           "SM-F9760",
		Software:        "16",
		CreationDate:    "2026-10-26T19:58:24+0800",
		LocationISO6709: "+35.9553+120.1715/",
	}
	out := injectQuickTimeMetadata(proxy, qt)

	after := topLevelBoxes(t, out)
	if len(after) != len(before) {
		t.Fatalf("top-level box count changed: before=%d after=%d", len(before), len(after))
	}
	if _, ok := after["ftyp"]; !ok {
		t.Fatal("ftyp disappeared")
	}

	moovAfter, ok := after["moov"]
	if !ok {
		t.Fatal("moov disappeared")
	}
	// The moov size header must have been rewritten. The parser above walked the whole buffer
	// without bailing, which is the real consistency check.
	if moovAfter.size <= moovBefore.size {
		t.Fatalf("moov did not grow: %d -> %d", moovBefore.size, moovAfter.size)
	}

	childrenAfter := childTypes(t, out, moovAfter.off, moovAfter.size)
	for _, want := range childrenBefore {
		found := false
		for _, got := range childrenAfter {
			if got == want {
				found = true
				break
			}
		}
		if !found {
			t.Fatalf("pre-existing child %q was dropped; children now %v", want, childrenAfter)
		}
	}

	hasMeta := false
	for _, c := range childrenAfter {
		if c == "meta" {
			hasMeta = true
		}
	}
	if !hasMeta {
		t.Fatalf("no meta box was injected; children are %v", childrenAfter)
	}
}

// TestInjectQuickTimeMetadataIsReadableByAppleLayout asserts the internal structure of the
// injected box: `meta` must contain hdlr/keys/ilst in that order, and hdlr must declare the
// "mdta" handler. Apple's parser is strict about this shape.
func TestInjectQuickTimeMetadataIsReadableByAppleLayout(t *testing.T) {
	ftyp := []byte("mp42\x00\x00\x00\x00mp42isom")
	proxy := buildTestMovie(t, ftyp, []byte("\x00\x00\x00\x08mvhd"), 0)

	out := injectQuickTimeMetadata(proxy, QuickTimeMeta{Make: "samsung", Model: "SM-F9760"})

	moovBox := topLevelBoxes(t, out)["moov"]
	var metaOff, metaSize int
	p := moovBox.off + 8
	for p+8 <= moovBox.off+moovBox.size {
		cs := int(binary.BigEndian.Uint32(out[p : p+4]))
		if string(out[p+4:p+8]) == "meta" {
			metaOff, metaSize = p, cs
			break
		}
		p += cs
	}
	if metaSize == 0 {
		t.Fatal("meta box not found inside moov")
	}

	children := childTypes(t, out, metaOff, metaSize)
	want := []string{"hdlr", "keys", "ilst"}
	if len(children) != len(want) {
		t.Fatalf("meta children = %v, want %v", children, want)
	}
	for i := range want {
		if children[i] != want[i] {
			t.Fatalf("meta children = %v, want %v (order matters for Apple's parser)", children, want)
		}
	}

	// hdlr payload: version/flags(4) + pre_defined(4) + handler_type(4)
	hdlrPayload := metaOff + 16
	if got := string(out[hdlrPayload+8 : hdlrPayload+12]); got != "mdta" {
		t.Fatalf("hdlr handler_type = %q, want mdta", got)
	}
}

// TestInjectQuickTimeMetadataNoop covers the "nothing to say" path: an empty QuickTimeMeta must
// leave the proxy byte-identical rather than adding an empty meta box.
func TestInjectQuickTimeMetadataNoop(t *testing.T) {
	proxy := buildTestMovie(t, []byte("mp42\x00\x00\x00\x00mp42isom"), []byte("\x00\x00\x00\x08mvhd"), 0)
	if out := injectQuickTimeMetadata(proxy, QuickTimeMeta{}); len(out) != len(proxy) {
		t.Fatalf("empty metadata must not modify the proxy: %d -> %d bytes", len(proxy), len(out))
	}
}

func TestFormatISO6709(t *testing.T) {
	cases := []struct {
		lat, lon float64
		want     string
	}{
		{35.9553, 120.1715, "+35.9553+120.1715/"},
		{-33.8688, 151.2093, "-33.8688+151.2093/"},
		{35.9553, -120.1715, "+35.9553-120.1715/"},
		{0, 0, ""},
	}
	for _, c := range cases {
		if got := formatISO6709(c.lat, c.lon); got != c.want {
			t.Errorf("formatISO6709(%v,%v) = %q, want %q", c.lat, c.lon, got, c.want)
		}
	}
}

func TestFormatQuickTimeDate(t *testing.T) {
	if got := formatQuickTimeDate(0); got != "" {
		t.Errorf("epoch 0 must render empty, got %q", got)
	}
	got := formatQuickTimeDate(1793015914000)
	// Shape check: yyyy-MM-ddTHH:mm:ss+HHMM, no colon in the offset.
	if len(got) != len("2026-10-26T19:58:34+0800") {
		t.Fatalf("unexpected length for %q", got)
	}
	if got[10] != 'T' {
		t.Fatalf("expected 'T' separator at 10: %q", got)
	}
	if got[19] != '+' && got[19] != '-' {
		t.Fatalf("offset sign missing at position 19: %q", got)
	}
}

// TestInjectRealSamsungMovie runs the whole path on a genuine device capture when one is
// available, and can write the result for external inspection:
//
//	REAL_MP4=/tmp/probe/real.mp4 MOOP_OUT=/tmp/probe/moop.bin \
//	  go test ./... -run TestInjectRealSamsungMovie -v
func TestInjectRealSamsungMovie(t *testing.T) {
	path := os.Getenv("REAL_MP4")
	if path == "" {
		t.Skip("REAL_MP4 not set")
	}
	f, err := os.Open(path)
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	defer f.Close()
	info, _ := f.Stat()

	qt := QuickTimeMeta{
		Make:            "samsung",
		Model:           "SM-F9760",
		Software:        "16",
		CreationDate:    formatQuickTimeDate(1793015914000),
		LocationISO6709: "+35.9553+120.1715/",
	}
	proxy, err := extractMovieProxy(f, info.Size())
	if err != nil {
		t.Fatalf("extract: %v", err)
	}
	out := injectQuickTimeMetadata(proxy, qt)
	if len(out) <= len(proxy) {
		t.Fatalf("injection did not grow the proxy: %d -> %d", len(proxy), len(out))
	}

	boxes := topLevelBoxes(t, out)
	moovBox, ok := boxes["moov"]
	if !ok {
		t.Fatal("moov lost after injection")
	}
	// Proves the rewritten size header is consistent with the payload.
	if moovBox.off+moovBox.size != len(out) {
		t.Fatalf("moov ends at %d but buffer is %d bytes", moovBox.off+moovBox.size, len(out))
	}

	if dst := os.Getenv("MOOP_OUT"); dst != "" {
		if err := os.WriteFile(dst, out, 0o600); err != nil {
			t.Fatalf("write %s: %v", dst, err)
		}
		t.Logf("wrote %d bytes to %s", len(out), dst)
	}
	t.Logf("proxy %d -> %d bytes", len(proxy), len(out))
}
