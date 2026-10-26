package icloudcore

import (
	"encoding/json"
	"testing"
)

func TestUploadMetadataLivePhotoDeserialization(t *testing.T) {
	jsonInput := `{
		"assetDate": 1700000000000,
		"itemType": "public.heic",
		"isLivePhoto": true,
		"livePhotoVideoPath": "/tmp/test_video.mov",
		"mediaGroupId": "6DEC9759-0A3B-4CCA-AB85-ACA084FD4391",
		"vidComplDurValue": 2631666667,
		"vidComplDurScale": 1000000000,
		"vidComplDispValue": 1349973637,
		"vidComplDispScale": 1000000000,
		"vidComplWidth": 1744,
		"vidComplHeight": 1308
	}`

	var meta UploadMetadata
	err := json.Unmarshal([]byte(jsonInput), &meta)
	if err != nil {
		t.Fatalf("Failed to unmarshal UploadMetadata: %v", err)
	}

	if !meta.IsLivePhoto {
		t.Errorf("Expected IsLivePhoto to be true")
	}
	if meta.LivePhotoVideoPath != "/tmp/test_video.mov" {
		t.Errorf("Expected LivePhotoVideoPath to be /tmp/test_video.mov, got %s", meta.LivePhotoVideoPath)
	}
	if meta.MediaGroupId != "6DEC9759-0A3B-4CCA-AB85-ACA084FD4391" {
		t.Errorf("Expected MediaGroupId, got %s", meta.MediaGroupId)
	}
	if meta.VidComplDurValue != 2631666667 {
		t.Errorf("Expected VidComplDurValue 2631666667, got %d", meta.VidComplDurValue)
	}
	if meta.VidComplDurScale != 1000000000 {
		t.Errorf("Expected VidComplDurScale 1000000000, got %d", meta.VidComplDurScale)
	}
	if meta.VidComplDispValue != 1349973637 {
		t.Errorf("Expected VidComplDispValue 1349973637, got %d", meta.VidComplDispValue)
	}
	if meta.VidComplWidth != 1744 || meta.VidComplHeight != 1308 {
		t.Errorf("Expected 1744x1308, got %dx%d", meta.VidComplWidth, meta.VidComplHeight)
	}
}
