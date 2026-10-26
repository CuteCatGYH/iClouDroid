package icloudcore

import (
	"bytes"
	"encoding/binary"
	"os"
	"testing"
)

// The `smta` box is what puts the focal length into Samsung's info panel ("wide lens - 23 mm").
// Its layout was reverse-engineered from three captures at three different focal lengths, which is
// the only reason it can be written at all: one sample would not distinguish a constant from a
// variable.
//
// The measurements that matter (all from one device, same session):
//
//	raw name            cami (ASCII)               smta tail
//	20261026_215300     3, 3, 1954, 534, 0.6       3, 13, 13   ultra-wide 0.6x
//	20261026_215309     3, 2, 2328, 206, 1.0       2, 23, 23   main 1.0x
//	20261026_215317     3, 4, 2452, -198, 3.0      4, 69, 69   tele 3.0x
//
// `cami`'s second field equals the lens id, and the focal values satisfy 23x0.6~=13 and 23x3=69,
// so the numbers are 35 mm-equivalent millimetres rather than anything else.

// smtaReference is a real box body captured from the device, used as the ground truth the writer
// must reproduce.
type smtaReference struct {
	name     string
	deviceID string
	focal    int
	body     []byte
}

var smtaReferences = []smtaReference{
	{
		name:     "ultra-wide 0.6x",
		deviceID: "SM-F9760",
		focal:    13,
		body: []byte{
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0e,
			's', 'a', 'u', 't', 0, 0, 0, 0, 0, 0, 0, 0,
			0x00, 0x10,
			'm', 'd', 'l', 'n', 'S', 'M', '-', 'F', '9', '7', '6', '0',
			0x00, 0x00, 0x00, 0x11,
			'l', 'e', 'n', 's', 0, 0, 0, 0,
			0x03, 0x00, 0x0d, 0x00, 0x0d,
		},
	},
	{
		name:     "main 1.0x",
		deviceID: "SM-F9760",
		focal:    23,
		body: []byte{
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0e,
			's', 'a', 'u', 't', 0, 0, 0, 0, 0, 0, 0, 0,
			0x00, 0x10,
			'm', 'd', 'l', 'n', 'S', 'M', '-', 'F', '9', '7', '6', '0',
			0x00, 0x00, 0x00, 0x11,
			'l', 'e', 'n', 's', 0, 0, 0, 0,
			0x02, 0x00, 0x17, 0x00, 0x17,
		},
	},
	{
		name:     "tele 3.0x",
		deviceID: "SM-F9760",
		focal:    69,
		body: []byte{
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0e,
			's', 'a', 'u', 't', 0, 0, 0, 0, 0, 0, 0, 0,
			0x00, 0x10,
			'm', 'd', 'l', 'n', 'S', 'M', '-', 'F', '9', '7', '6', '0',
			0x00, 0x00, 0x00, 0x11,
			'l', 'e', 'n', 's', 0, 0, 0, 0,
			0x04, 0x00, 0x45, 0x00, 0x45,
		},
	},
}

// TestBuildSmtaBoxReproducesRealSamples is the whole justification for this writer: given the
// three measured focal lengths, it must emit bytes identical to what the device itself wrote.
func TestBuildSmtaBoxReproducesRealSamples(t *testing.T) {
	for _, ref := range smtaReferences {
		t.Run(ref.name, func(t *testing.T) {
			got := buildSmtaBox(ref.deviceID, ref.focal)
			if got == nil {
				t.Fatal("buildSmtaBox refused a valid focal length")
			}

			if string(got[4:8]) != "smta" {
				t.Fatalf("box type = %q, want smta", got[4:8])
			}
			body := got[8:]
			if !bytes.Equal(body, ref.body) {
				t.Fatalf("body mismatch\n got (%d): %x\nwant (%d): %x",
					len(body), body, len(ref.body), ref.body)
			}
			if int(binary.BigEndian.Uint32(got[0:4])) != len(got) {
				t.Fatalf("size header %d, actual %d", binary.BigEndian.Uint32(got[0:4]), len(got))
			}
		})
	}
}

// TestSamsungLensID pins the band mapping. The id chooses the label, so a wrong band would
// mislabel a lens while still showing the right millimetres.
func TestSamsungLensID(t *testing.T) {
	cases := []struct {
		focal int
		want  byte
	}{
		{13, 3}, // measured ultra-wide
		{23, 2}, // measured main
		{29, 2}, // iPhone SE main camera
		{69, 4}, // measured tele
		{0, 0},  // unknown
		{-5, 0}, // nonsensical
	}
	for _, c := range cases {
		if got := samsungLensID(c.focal); got != c.want {
			t.Errorf("samsungLensID(%d) = %d, want %d", c.focal, got, c.want)
		}
	}
}

// TestBuildSmtaBoxRejectsUnrepresentable ensures we refuse rather than truncate: the focal field
// is a single byte, so 300 mm would be written as 44 mm.
func TestBuildSmtaBoxRejectsUnrepresentable(t *testing.T) {
	if got := buildSmtaBox("Apple", 300); got != nil {
		t.Fatalf("expected refusal for 300 mm, got %d bytes", len(got))
	}
	if got := buildSmtaBox("Apple", 0); got != nil {
		t.Fatal("expected refusal for unknown focal length")
	}
}

func TestBuildSmtaBoxTruncatesDeviceCode(t *testing.T) {
	got := buildSmtaBox("SM-F9760-EXTRA", 23)
	if got == nil {
		t.Fatal("unexpected refusal")
	}
	if string(got[8+26:8+34]) != "SM-F9760" {
		t.Fatalf("device code field = %q", got[8+26:8+34])
	}
}

// TestBuildSmtaBoxMatchesDeviceFiles compares against the actual captures on disk rather than the
// hard-coded fixtures above, so a change to either the writer or the understanding of the format
// cannot both drift together unnoticed.
//
//	SAMSUNG_DIR=/tmp/probe go test ./... -run TestBuildSmtaBoxMatchesDeviceFiles -v
func TestBuildSmtaBoxMatchesDeviceFiles(t *testing.T) {
	dir := os.Getenv("SAMSUNG_DIR")
	if dir == "" {
		t.Skip("SAMSUNG_DIR not set")
	}

	cases := []struct {
		file  string
		focal int
	}{
		{"20261026_215300.mp4", 13}, // ultra-wide 0.6x
		{"20261026_215309.mp4", 23}, // main 1.0x
		{"20261026_215317.mp4", 69}, // tele 3.0x
	}

	for _, c := range cases {
		raw, err := os.ReadFile(dir + "/" + c.file)
		if err != nil {
			t.Logf("skipping %s: %v", c.file, err)
			continue
		}
		moov := findBox(raw, 0, len(raw), "moov")
		if moov == nil {
			t.Fatalf("%s: no moov", c.file)
		}
		udta := findBox(raw, moov.bodyStart(), moov.end(), "udta")
		if udta == nil {
			t.Fatalf("%s: no udta", c.file)
		}
		actual := findBox(raw, udta.bodyStart(), udta.end(), "smta")
		if actual == nil {
			t.Fatalf("%s: no smta", c.file)
		}
		want := raw[actual.start:actual.end()]

		got := buildSmtaBox("SM-F9760", c.focal)
		if got == nil {
			t.Fatalf("%s: writer refused focal %d", c.file, c.focal)
		}
		if !bytes.Equal(got, want) {
			t.Errorf("%s: mismatch\n got: %x\nwant: %x", c.file, got, want)
		}
	}
}

// TestReadAppleCaptureMetaFindsTrackLevelLens guards the mistake an earlier version made: the
// focal length lives in `trak`-level metadata, not in the `moov`-level box. Reading only the
// latter loses the only value `smta` can be built from.
func TestReadAppleCaptureMetaFindsTrackLevelLens(t *testing.T) {
	trackMeta := buildMetaBox([]quickTimeMetaKey{
		{name: "com.apple.quicktime.camera.lens_model", value: "iPhone SE (3rd generation) back camera 3.99mm f/1.8"},
		{name: "com.apple.quicktime.camera.focal_length.35mm_equivalent", value: "29"},
	})
	moovMeta := buildMetaBox([]quickTimeMetaKey{
		{name: "com.apple.quicktime.model", value: "iPhone SE (3rd generation)"},
	})

	trak := box("trak", append(append([]byte{}, box("mvhd", make([]byte, 20))...), trackMeta...))
	moov := box("moov", append(append(append([]byte{}, box("mvhd", make([]byte, 20))...), moovMeta...), trak...))

	got := readAppleCaptureMeta(moov)
	if got.Model != "iPhone SE (3rd generation)" {
		t.Errorf("Model = %q", got.Model)
	}
	if got.FocalLength35mm != "29" {
		t.Fatalf("FocalLength35mm = %q, want 29 (track-level meta was not read)", got.FocalLength35mm)
	}
	if got.LensModel == "" {
		t.Error("LensModel was not read from the track meta")
	}
}

// TestInjectRealIPhoneWritesSmta runs the whole path on a genuine iPhone clip and checks that all
// three atoms land while the file stays the same size.
//
//	IPHONE_MOV=/tmp/probe/dl_iphone.mov go test ./... -run TestInjectRealIPhoneWritesSmta -v
func TestInjectRealIPhoneWritesSmta(t *testing.T) {
	src := os.Getenv("IPHONE_MOV")
	if src == "" {
		t.Skip("IPHONE_MOV not set")
	}
	raw, err := os.ReadFile(src)
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	dst := t.TempDir() + "/patched.mov"
	if err := os.WriteFile(dst, raw, 0o600); err != nil {
		t.Fatalf("write: %v", err)
	}

	report := InjectAndroidVideoMetadata(dst)
	t.Logf("report: %s", report)
	if !bytes.Contains([]byte(report), []byte(`"ok":true`)) {
		t.Fatalf("injection refused: %s", report)
	}
	if !bytes.Contains([]byte(report), []byte("smta")) {
		t.Fatalf("smta was not written: %s", report)
	}

	patched, err := os.ReadFile(dst)
	if err != nil {
		t.Fatalf("re-read: %v", err)
	}
	if len(patched) != len(raw) {
		t.Fatalf("size changed %d -> %d", len(raw), len(patched))
	}

	boxes := topLevelBoxes(t, patched)
	moovBox := boxes["moov"]
	moov := patched[moovBox.off : moovBox.off+moovBox.size]
	udta := findBox(moov, 8, len(moov), "udta")
	if udta == nil {
		t.Fatal("udta missing")
	}
	inner := moov[udta.bodyStart():udta.end()]
	for _, atom := range []string{"auth", "\xa9xyz", "smta"} {
		if findBox(inner, 0, len(inner), atom) == nil {
			t.Errorf("atom %q missing from udta", atom)
		}
	}

	smta := findBox(inner, 0, len(inner), "smta")
	body := inner[smta.bodyStart():smta.end()]
	if body[len(body)-3] != 29 {
		t.Errorf("focal byte = %d, want 29 (iPhone SE 35mm equivalent)", body[len(body)-3])
	}
	t.Logf("smta = %x", body)
}
