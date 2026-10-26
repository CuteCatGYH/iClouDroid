package icloudcore

import (
	"encoding/base64"
	"encoding/binary"
	"os"
	"path/filepath"
	"testing"

	"howett.net/plist"
)

// buildTestMovie assembles a minimal ISO-BMFF file: ftyp, then an optional mdat-sized filler
// placed BEFORE moov (the common layout), then moov. Used to prove the scanner walks boxes
// correctly rather than assuming moov is the first one.
func buildTestMovie(t *testing.T, ftypPayload, moovPayload []byte, fillerSize int) []byte {
	t.Helper()

	box := func(typ string, payload []byte) []byte {
		out := make([]byte, 8+len(payload))
		binary.BigEndian.PutUint32(out[0:4], uint32(8+len(payload)))
		copy(out[4:8], typ)
		copy(out[8:], payload)
		return out
	}

	var out []byte
	out = append(out, box("ftyp", ftypPayload)...)
	if fillerSize > 0 {
		out = append(out, box("mdat", make([]byte, fillerSize))...)
	}
	out = append(out, box("moov", moovPayload)...)
	return out
}

func writeTempMovie(t *testing.T, data []byte) (*os.File, int64) {
	t.Helper()
	path := filepath.Join(t.TempDir(), "clip.mp4")
	if err := os.WriteFile(path, data, 0o600); err != nil {
		t.Fatalf("write temp movie: %v", err)
	}
	f, err := os.Open(path)
	if err != nil {
		t.Fatalf("open temp movie: %v", err)
	}
	t.Cleanup(func() { f.Close() })
	return f, int64(len(data))
}

// TestExtractMovieProxySkipsMdat is the core case: `mdat` sits between `ftyp` and `moov`, and is
// large. The proxy must contain only ftyp + moov, and must not require reading the mdat body.
func TestExtractMovieProxySkipsMdat(t *testing.T) {
	ftyp := []byte("mp42\x00\x00\x00\x00mp42isom")
	moov := []byte("\x00\x00\x00\x08mvhd" + "payload")
	data := buildTestMovie(t, ftyp, moov, 64*1024)
	f, size := writeTempMovie(t, data)

	proxy, err := extractMovieProxy(f, size)
	if err != nil {
		t.Fatalf("extractMovieProxy: %v", err)
	}

	wantLen := 8 + len(ftyp) + 8 + len(moov)
	if len(proxy) != wantLen {
		t.Fatalf("proxy is %d bytes, want %d (ftyp+moov only)", len(proxy), wantLen)
	}
	if string(proxy[4:8]) != "ftyp" {
		t.Errorf("proxy must start with the ftyp box, got %q", proxy[4:8])
	}
	// moov box sits at the end.
	if string(proxy[len(proxy)-8-len(moov):][4:8]) != "moov" {
		t.Errorf("proxy must end with the moov box")
	}
	if int64(len(proxy)) > size {
		t.Errorf("proxy (%d) must be smaller than the file (%d)", len(proxy), size)
	}
}

// TestBuildVideoMetaDataEnc pins the exact AVAssetProxy shape measured on a native Apple record:
// keys `moop` / `mpvr` / `name`, with `name` derived from the CPLAsset record name.
func TestBuildVideoMetaDataEnc(t *testing.T) {
	ftyp := []byte("mp42\x00\x00\x00\x00mp42isom")
	moov := []byte("\x00\x00\x00\x08mvhd")
	f, size := writeTempMovie(t, buildTestMovie(t, ftyp, moov, 1024))

	enc, _, ok := buildVideoMetaDataEnc(f, size, "7e4382f5-11d4-41af-b0ec-bf316db08968", "clip.mp4", QuickTimeMeta{})
	if !ok {
		t.Fatal("buildVideoMetaDataEnc returned ok=false")
	}

	raw, err := base64.StdEncoding.DecodeString(enc)
	if err != nil {
		t.Fatalf("not valid base64: %v", err)
	}

	var decoded map[string]interface{}
	if _, err := plist.Unmarshal(raw, &decoded); err != nil {
		t.Fatalf("parser rejected the video plist: %v", err)
	}

	moop, ok := decoded["moop"].([]byte)
	if !ok {
		t.Fatalf("`moop` must be a data blob, got %T", decoded["moop"])
	}
	if len(moop) != 8+len(ftyp)+8+len(moov) {
		t.Errorf("moop is %d bytes, want ftyp+moov", len(moop))
	}
	// `mpvr` is a format version, and it is keyed exactly like this in Apple's records.
	if v := toInt(decoded["mpvr"]); v != 1 {
		t.Errorf("mpvr = %v, want 1", decoded["mpvr"])
	}
	// The name is the CPLAsset record name plus an upper-cased extension.
	if got := decoded["name"]; got != "7e4382f5-11d4-41af-b0ec-bf316db08968.MP4" {
		t.Errorf("name = %v, want <assetId>.MP4", got)
	}
}

// TestBuildVideoMetaDataEncWithoutMoov makes sure an unparseable container is skipped rather
// than shipping a proxy the client cannot read.
func TestBuildVideoMetaDataEncWithoutMoov(t *testing.T) {
	// ftyp only. Built by hand because buildTestMovie always emits a (possibly empty) moov, and
	// an empty moov still counts as "found".
	ftyp := make([]byte, 8+4)
	binary.BigEndian.PutUint32(ftyp[0:4], uint32(len(ftyp)))
	copy(ftyp[4:8], "ftyp")
	copy(ftyp[8:], "mp42")

	f, size := writeTempMovie(t, ftyp)

	if _, _, ok := buildVideoMetaDataEnc(f, size, "abc", "clip.mp4", QuickTimeMeta{}); ok {
		t.Fatal("must report ok=false when the container has no moov box")
	}
}

// TestFileClosedAfterStreamingUpload is the regression guard for the bug that made every
// uploaded video ship without `mediaMetaDataEnc`.
//
// `uploadFileToUrl` puts the `*os.File` into `http.NewRequest` as the body. An `*os.File`
// satisfies `io.ReadCloser`, so net/http takes ownership and closes it once the POST is done.
// The record assembly used to read the moov box *after* that upload, and every `ReadAt` then
// failed with "file already closed" - silently, because the failure only produced an omitted
// field. It passed in unit tests precisely because no upload happens before the read there.
//
// This pins the underlying behaviour, so the ordering requirement in
// `uploadPhotoAssetFromFile` (extract the movie header BEFORE streaming) cannot be undone by
// accident.
func TestFileClosedAfterStreamingUpload(t *testing.T) {
	ftyp := []byte("mp42\x00\x00\x00\x00mp42isom")
	moov := []byte("\x00\x00\x00\x08mvhd")
	f, size := writeTempMovie(t, buildTestMovie(t, ftyp, moov, 4096))

	// Works while the file is open.
	if _, err := extractMovieProxy(f, size); err != nil {
		t.Fatalf("extract before close: %v", err)
	}

	// Simulate what net/http does to a request body once the streaming POST completes.
	if err := f.Close(); err != nil {
		t.Fatalf("close: %v", err)
	}

	// Guard on the observable consequence: a closed file must NOT look like a valid proxy.
	if _, err := extractMovieProxy(f, size); err == nil {
		t.Fatal("extractMovieProxy on a closed file returned success; the closed-file failure would be silent again")
	}
	if _, _, ok := buildVideoMetaDataEnc(f, size, "abc", "clip.mp4", QuickTimeMeta{}); ok {
		t.Fatal("buildVideoMetaDataEnc on a closed file must report ok=false")
	}
}
