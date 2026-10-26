package icloudcore

import (
	"os"
	"testing"
)

// buildStsdTrack assembles a minimal `trak/mdia` with the given handler type and sample-entry
// FourCC, which is all detectVideoCodec needs to make its decision.
func buildStsdTrack(handler string, fourcc string) []byte {
	// stsd payload: version+flags(4) + entry_count(4) + one entry { size(4), fourcc(4), ... }
	entry := make([]byte, 16)
	putU32(entry[0:4], 16)
	copy(entry[4:8], fourcc)

	stsdBody := make([]byte, 8)
	putU32(stsdBody[4:8], 1) // entry_count
	stsdBody = append(stsdBody, entry...)
	stsd := box("stsd", stsdBody)

	stbl := box("stbl", stsd)
	minf := box("minf", stbl)

	// hdlr payload: version+flags(4) + pre_defined(4) + handler_type(4) + reserved(12) + name(1)
	hdlrBody := make([]byte, 4+4+12+1)
	copy(hdlrBody[8:12], handler)
	hdlr := box("hdlr", hdlrBody)

	mdia := box("mdia", append(append([]byte{}, hdlr...), minf...))
	return box("trak", mdia)
}

func putU32(b []byte, v uint32) {
	b[0] = byte(v >> 24)
	b[1] = byte(v >> 16)
	b[2] = byte(v >> 8)
	b[3] = byte(v)
}

func TestDetectVideoCodec(t *testing.T) {
	cases := []struct {
		fourcc string
		want   string
	}{
		{"hvc1", "HEVC"},
		{"hev1", "HEVC"},
		{"avc1", "H.264"},
		{"avc3", "H.264"},
		{"av01", "AV1"},
		{"vp09", "VP9"},
		{"mp4v", "MPEG-4"},
		{"xxxx", ""},
	}
	for _, c := range cases {
		proxy := box("moov", buildStsdTrack("vide", c.fourcc))
		if got := detectVideoCodec(proxy); got != c.want {
			t.Errorf("fourcc %q -> %q, want %q", c.fourcc, got, c.want)
		}
	}
}

// TestDetectVideoCodecSkipsAudioTrack is the trap this function has to avoid: a `moov` holds one
// `trak` per stream, and the audio track usually comes first in Samsung/Android captures. Reading
// the first `stsd` found would report the audio codec (`mp4a`, which maps to nothing) and emit an
// empty field even though the video track is perfectly identifiable.
func TestDetectVideoCodecSkipsAudioTrack(t *testing.T) {
	audio := buildStsdTrack("soun", "mp4a")
	video := buildStsdTrack("vide", "hvc1")
	proxy := box("moov", append(append([]byte{}, audio...), video...))

	if got := detectVideoCodec(proxy); got != "HEVC" {
		t.Fatalf("audio track was not skipped: got %q, want HEVC", got)
	}
}

func TestDetectVideoCodecNoMoov(t *testing.T) {
	if got := detectVideoCodec(box("ftyp", []byte("mp42\x00\x00\x00\x00mp42isom"))); got != "" {
		t.Fatalf("expected empty when there is no moov, got %q", got)
	}
}

func TestDetectVideoCodecAudioOnly(t *testing.T) {
	proxy := box("moov", buildStsdTrack("soun", "mp4a"))
	if got := detectVideoCodec(proxy); got != "" {
		t.Fatalf("audio-only container must yield no codec, got %q", got)
	}
}

// TestDetectVideoCodecRealSamsung runs against a genuine device capture when available:
//
//	REAL_MP4=/tmp/probe/real.mp4 go test ./... -run TestDetectVideoCodecRealSamsung -v
func TestDetectVideoCodecRealSamsung(t *testing.T) {
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
	got := detectVideoCodec(proxy)
	if got != "HEVC" {
		t.Fatalf("codec = %q, want HEVC for this capture", got)
	}
	t.Logf("detected codec = %q", got)
}
