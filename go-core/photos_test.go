package icloudcore

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestCheckPrimaryZone(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.Contains(r.URL.Path, "zones/list") {
			w.WriteHeader(http.StatusOK)
			w.Write([]byte(`{
				"zones": [
					{
						"zoneID": {"zoneName": "PrimarySync"},
						"syncToken": "token-xyz"
					}
				]
			}`))
			return
		}
		w.WriteHeader(http.StatusNotFound)
	}))
	defer ts.Close()

	session, err := NewSession("test=cookie", true)
	if err != nil {
		t.Fatalf("NewSession failed: %v", err)
	}
	session.CKDatabaseURL = ts.URL
	session.DSID = "12345678"

	resJSON := CheckPrimaryZone()
	var res ZoneCheckResult
	if err := json.Unmarshal([]byte(resJSON), &res); err != nil {
		t.Fatalf("failed to unmarshal result: %v", err)
	}

	if !res.Success || !res.Ready {
		t.Errorf("expected PrimarySync zone ready, got %+v", res)
	}
	if res.SyncToken != "token-xyz" {
		t.Errorf("expected syncToken token-xyz, got %s", res.SyncToken)
	}
}

func TestFetchPhotoPageAndParse(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.Contains(r.URL.Path, "records/query") {
			w.WriteHeader(http.StatusOK)
			// Return one CPLAsset and one matching CPLMaster
			w.Write([]byte(`{
				"records": [
					{
						"recordName": "asset-uuid-1",
						"recordType": "CPLAsset",
						"fields": {
							"masterRef": {
								"value": {"recordName": "master-uuid-1"},
								"type": "REFERENCE"
							},
							"assetDate": {
								"value": 1690000000000,
								"type": "TIMESTAMP"
							},
							"isFavorite": {
								"value": 1,
								"type": "INT64"
							},
							"duration": {
								"value": 0,
								"type": "INT64"
							}
						}
					},
					{
						"recordName": "master-uuid-1",
						"recordType": "CPLMaster",
						"fields": {
							"itemType": {
								"value": "public.heic",
								"type": "STRING"
							},
							"filenameEnc": {
								"value": "SU1HXzAwMDEuSEVJQw==",
								"type": "ENCRYPTED_BYTES"
							},
							"resJPEGThumbRes": {
								"value": {
									"downloadURL": "https://cvws.example.com/thumb.jpg",
									"size": 1024
								},
								"type": "ASSETID"
							},
							"resJPEGMedRes": {
								"value": {
									"downloadURL": "https://cvws.example.com/med.jpg",
									"size": 5120
								},
								"type": "ASSETID"
							},
							"resOriginalRes": {
								"value": {
									"downloadURL": "https://cvws.example.com/original.heic",
									"size": 20480
								},
								"type": "ASSETID"
							},
							"resOriginalWidth": {
								"value": 4032,
								"type": "INT64"
							},
							"resOriginalHeight": {
								"value": 3024,
								"type": "INT64"
							}
						}
					}
				],
				"continuationMarker": "next-page-marker-123",
				"syncToken": "sync-token-abc"
			}`))
			return
		}
		w.WriteHeader(http.StatusNotFound)
	}))
	defer ts.Close()

	session, err := NewSession("test=cookie", true)
	if err != nil {
		t.Fatalf("NewSession failed: %v", err)
	}
	session.CKDatabaseURL = ts.URL
	session.DSID = "12345678"

	pageJSON := FetchPhotoPage("", 50)
	var page PhotoPageResult
	if err := json.Unmarshal([]byte(pageJSON), &page); err != nil {
		t.Fatalf("failed to unmarshal page: %v", err)
	}

	if !page.Success {
		t.Fatalf("expected success, got error: %s", page.ErrorMessage)
	}
	if !page.HasMore || page.ContinuationMarker != "next-page-marker-123" {
		t.Errorf("unexpected continuation marker: %s", page.ContinuationMarker)
	}
	if len(page.Photos) != 1 {
		t.Fatalf("expected 1 photo, got %d", len(page.Photos))
	}

	p := page.Photos[0]
	if p.ID != "asset-uuid-1" || p.MasterID != "master-uuid-1" {
		t.Errorf("unexpected IDs: %+v", p)
	}
	if p.Filename != "IMG_0001.HEIC" {
		t.Errorf("expected decoded filename IMG_0001.HEIC, got %s", p.Filename)
	}
	if !p.IsFavorite {
		t.Errorf("expected isFavorite=true")
	}
	if p.ThumbURL != "https://cvws.example.com/thumb.jpg" {
		t.Errorf("unexpected ThumbURL: %s", p.ThumbURL)
	}
	if p.Width != 4032 || p.Height != 3024 {
		t.Errorf("unexpected dimensions: %dx%d", p.Width, p.Height)
	}
	if p.MediaType != "IMAGE" {
		t.Errorf("expected IMAGE, got %s", p.MediaType)
	}
}

func TestFetchChanges(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.Contains(r.URL.Path, "records/changes") {
			w.WriteHeader(http.StatusOK)
			w.Write([]byte(`{
				"records": [
					{
						"recordName": "deleted-asset-99",
						"deleted": true
					}
				],
				"syncToken": "updated-sync-token",
				"moreComing": false
			}`))
			return
		}
		w.WriteHeader(http.StatusNotFound)
	}))
	defer ts.Close()

	session, err := NewSession("test=cookie", true)
	if err != nil {
		t.Fatalf("NewSession failed: %v", err)
	}
	session.CKDatabaseURL = ts.URL
	session.DSID = "12345678"

	changesJSON := FetchChanges("old-token", 50)
	var changes PhotoChangesResult
	if err := json.Unmarshal([]byte(changesJSON), &changes); err != nil {
		t.Fatalf("failed to unmarshal changes: %v", err)
	}

	if !changes.Success {
		t.Fatalf("expected success, got %s", changes.ErrorMessage)
	}
	if changes.SyncToken != "updated-sync-token" {
		t.Errorf("unexpected syncToken: %s", changes.SyncToken)
	}
	if len(changes.DeletedIDs) != 1 || changes.DeletedIDs[0] != "deleted-asset-99" {
		t.Errorf("unexpected deleted IDs: %+v", changes.DeletedIDs)
	}
}

func TestRefreshAssetUrls(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.Contains(r.URL.Path, "records/lookup") {
			w.WriteHeader(http.StatusOK)
			w.Write([]byte(`{
				"records": [
					{
						"recordName": "master-uuid-1",
						"fields": {
							"resJPEGThumbRes": {
								"value": {"downloadURL": "https://cvws.example.com/fresh-thumb.jpg"}
							},
							"resOriginalRes": {
								"value": {"downloadURL": "https://cvws.example.com/fresh-orig.heic"}
							}
						}
					}
				]
			}`))
			return
		}
		w.WriteHeader(http.StatusNotFound)
	}))
	defer ts.Close()

	session, err := NewSession("test=cookie", true)
	if err != nil {
		t.Fatalf("NewSession failed: %v", err)
	}
	session.CKDatabaseURL = ts.URL
	session.DSID = "12345678"

	urlsJSON := RefreshAssetUrls(`["master-uuid-1"]`)
	var refreshRes RefreshUrlsResult
	if err := json.Unmarshal([]byte(urlsJSON), &refreshRes); err != nil {
		t.Fatalf("failed to unmarshal: %v", err)
	}

	if !refreshRes.Success {
		t.Fatalf("expected success, got %s", refreshRes.ErrorMessage)
	}
	masterUrls, ok := refreshRes.URLs["master-uuid-1"]
	if !ok {
		t.Fatalf("master-uuid-1 not found in urls")
	}
	if masterUrls.ThumbURL != "https://cvws.example.com/fresh-thumb.jpg" {
		t.Errorf("unexpected thumbUrl: %s", masterUrls.ThumbURL)
	}
}
