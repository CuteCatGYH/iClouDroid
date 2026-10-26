package icloudcore

import (
	"os"
	"testing"
)

// extractUdtaLocation is the permission-free fallback for video GPS: `moov/udta/©xyz` is written
// by the camera app straight into the container, so it survives even when MediaStore redacts
// LATITUDE/LONGITUDE for lack of ACCESS_MEDIA_LOCATION.

// buildUdtaMovie assembles ftyp + moov{mvhd, udta{©xyz}}.
func buildUdtaMovie(t *testing.T, xyzBody []byte) []byte {
	t.Helper()

	mvhd := box("mvhd", make([]byte, 100))

	var udtaBody []byte
	if xyzBody != nil {
		// "©xyz" is 0xA9 0x78 0x79 0x7A in Latin-1.
		udtaBody = append(udtaBody, box("\xa9xyz", xyzBody)...)
	}
	udta := box("udta", udtaBody)

	moovBody := append(append([]byte{}, mvhd...), udta...)
	moov := box("moov", moovBody)

	ftyp := box("ftyp", []byte("mp42\x00\x00\x00\x00mp42isom"))
	out := append(append([]byte{}, ftyp...), moov...)
	return out
}

func TestExtractUdtaLocation(t *testing.T) {
	// Real layout: 2-byte string length, 2-byte language code, then the ISO-6709 text.
	text := "+35.9553+120.1715/"
	body := []byte{0x00, byte(len(text)), 0x15, 0xc7}
	body = append(body, []byte(text)...)

	proxy := buildUdtaMovie(t, body)
	if got := extractUdtaLocation(proxy); got != text {
		t.Fatalf("extractUdtaLocation = %q, want %q", got, text)
	}
}

func TestExtractUdtaLocationMissing(t *testing.T) {
	if got := extractUdtaLocation(buildUdtaMovie(t, nil)); got != "" {
		t.Fatalf("expected no location when the atom is absent, got %q", got)
	}
}

func TestExtractUdtaLocationRejectsGarbage(t *testing.T) {
	// A truncated or non-conforming atom must not become a fake coordinate.
	cases := map[string][]byte{
		"too short":  {0x00, 0x03, 0x15, 0xc7, '+', '1', '2'},
		"no sign":    append([]byte{0x00, 0x0a, 0x15, 0xc7}, []byte("35.9553120")...),
		"no decimal": append([]byte{0x00, 0x06, 0x15, 0xc7}, []byte("+359551")...),
	}
	for name, body := range cases {
		if got := extractUdtaLocation(buildUdtaMovie(t, body)); got != "" {
			t.Errorf("%s: expected rejection, got %q", name, got)
		}
	}
}

// TestExtractUdtaLocationRealSamsung runs against a genuine device capture when available:
//
//	REAL_MP4=/tmp/probe/real.mp4 go test ./... -run TestExtractUdtaLocationRealSamsung -v
func TestExtractUdtaLocationRealSamsung(t *testing.T) {
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

	proxy, err := extractMovieProxy(f, info.Size())
	if err != nil {
		t.Fatalf("extractMovieProxy: %v", err)
	}
	got := extractUdtaLocation(proxy)
	if got == "" {
		t.Fatal("expected a location atom from the Samsung capture")
	}
	t.Logf("udta/©xyz = %q", got)
}
