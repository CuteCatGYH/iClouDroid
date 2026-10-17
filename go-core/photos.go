package icloudcore

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// Desired keys to fetch from CloudKit records
var defaultDesiredKeys = []string{
	"recordName",
	"recordType",
	"recordChangeTag",
	"masterRef",
	"assetDate",
	"addedDate",
	"isFavorite",
	"isHidden",
	"duration",
	"orientation",
	"customRenderedValue",
	"itemType",
	"filenameEnc",
	"originalOrientation",
	"resOriginalRes",
	"resOriginalFileType",
	"resOriginalWidth",
	"resOriginalHeight",
	"resJPEGThumbRes",
	"resJPEGThumbWidth",
	"resJPEGThumbHeight",
	"resJPEGMedRes",
	"resJPEGMedWidth",
	"resJPEGMedHeight",
	"resOriginalVidComplRes",
}

type CKZoneID struct {
	ZoneName string `json:"zoneName"`
}

type CKZone struct {
	ZoneID    CKZoneID `json:"zoneID"`
	SyncToken string   `json:"syncToken,omitempty"`
}

type CKZonesListResponse struct {
	Zones []CKZone `json:"zones"`
}

type ZoneCheckResult struct {
	Success      bool   `json:"success"`
	Ready        bool   `json:"ready"`
	ZoneName     string `json:"zoneName,omitempty"`
	SyncToken    string `json:"syncToken,omitempty"`
	ErrorMessage string `json:"errorMessage,omitempty"`
}

type CKField struct {
	Type  string          `json:"type,omitempty"`
	Value json.RawMessage `json:"value,omitempty"`
}

type CKRecord struct {
	RecordName      string             `json:"recordName"`
	RecordType      string             `json:"recordType"`
	RecordChangeTag string             `json:"recordChangeTag,omitempty"`
	Deleted         bool               `json:"deleted,omitempty"`
	Fields          map[string]CKField `json:"fields,omitempty"`
}

type CKQueryResponse struct {
	Records            []CKRecord `json:"records"`
	ContinuationMarker string     `json:"continuationMarker,omitempty"`
	SyncToken          string     `json:"syncToken,omitempty"`
	MoreComing         bool       `json:"moreComing,omitempty"`
}

type PhotoItem struct {
	ID              string `json:"id"`
	MasterID        string `json:"masterId"`
	Filename        string `json:"filename"`
	AssetDate       int64  `json:"assetDate"`
	AddedDate       int64  `json:"addedDate"`
	IsFavorite      bool   `json:"isFavorite"`
	IsHidden        bool   `json:"isHidden"`
	Duration        int64  `json:"duration"`
	MediaType       string `json:"mediaType"` // "IMAGE", "VIDEO", "LIVE_PHOTO"
	ItemType        string `json:"itemType"`
	Width           int    `json:"width"`
	Height          int    `json:"height"`
	Size            int64  `json:"size"`
	ThumbURL        string `json:"thumbUrl,omitempty"`
	MedURL          string `json:"medUrl,omitempty"`
	OriginalURL     string `json:"originalUrl,omitempty"`
	VideoComplURL   string `json:"videoComplUrl,omitempty"`
	RecordChangeTag string `json:"recordChangeTag,omitempty"`
}

type PhotoPageResult struct {
	Success            bool        `json:"success"`
	Photos             []PhotoItem `json:"photos"`
	ContinuationMarker string      `json:"continuationMarker,omitempty"`
	HasMore            bool        `json:"hasMore"`
	SyncToken          string      `json:"syncToken,omitempty"`
	ErrorMessage       string      `json:"errorMessage,omitempty"`
}

type PhotoChangesResult struct {
	Success      bool        `json:"success"`
	Photos       []PhotoItem `json:"photos"`
	DeletedIDs   []string    `json:"deletedIds"`
	SyncToken    string      `json:"syncToken,omitempty"`
	MoreComing   bool        `json:"moreComing"`
	ErrorMessage string      `json:"errorMessage,omitempty"`
}

type RefreshedURLs struct {
	ThumbURL      string `json:"thumbUrl,omitempty"`
	MedURL        string `json:"medUrl,omitempty"`
	OriginalURL   string `json:"originalUrl,omitempty"`
	VideoComplURL string `json:"videoComplUrl,omitempty"`
}

type RefreshUrlsResult struct {
	Success      bool                     `json:"success"`
	URLs         map[string]RefreshedURLs `json:"urls"`
	ErrorMessage string                   `json:"errorMessage,omitempty"`
}

// Helper methods to unpack CKField values safely

func (f CKField) AsString() string {
	if len(f.Value) == 0 {
		return ""
	}
	var s string
	if err := json.Unmarshal(f.Value, &s); err == nil {
		return s
	}
	return strings.Trim(string(f.Value), `"`)
}

func (f CKField) AsInt64() int64 {
	if len(f.Value) == 0 {
		return 0
	}
	var i int64
	if err := json.Unmarshal(f.Value, &i); err == nil {
		return i
	}
	var fl float64
	if err := json.Unmarshal(f.Value, &fl); err == nil {
		return int64(fl)
	}
	return 0
}

func (f CKField) AsBool() bool {
	if len(f.Value) == 0 {
		return false
	}
	var b bool
	if err := json.Unmarshal(f.Value, &b); err == nil {
		return b
	}
	return f.AsInt64() != 0
}

type CKResource struct {
	DownloadURL string `json:"downloadURL"`
	Size        int64  `json:"size"`
}

func (f CKField) AsResource() CKResource {
	if len(f.Value) == 0 {
		return CKResource{}
	}
	var res CKResource
	_ = json.Unmarshal(f.Value, &res)
	return res
}

func (f CKField) AsMasterRef() string {
	if len(f.Value) == 0 {
		return ""
	}
	var ref struct {
		RecordName string `json:"recordName"`
	}
	if err := json.Unmarshal(f.Value, &ref); err == nil && ref.RecordName != "" {
		return ref.RecordName
	}
	return f.AsString()
}

// decodeFilename decodes base64 filenameEnc or returns fallback
func decodeFilename(enc string, fallback string) string {
	if enc == "" {
		return fallback
	}
	data, err := base64.StdEncoding.DecodeString(enc)
	if err != nil {
		// might be raw string already
		return enc
	}
	return string(data)
}

// photosBaseURL constructs the CloudKit photos endpoint URL
func (s *Session) photosBaseURL(path string) string {
	s.mu.RLock()
	ckURL := s.CKDatabaseURL
	dsid := s.DSID
	s.mu.RUnlock()

	clientID := generateClientID()
	return fmt.Sprintf(
		"%s/database/1/com.apple.photos.cloud/production/private/%s?clientBuildNumber=2422Hotfix23&clientMasteringNumber=2422Hotfix23&clientId=%s&dsid=%s&remapEnums=true&getCurrentSyncToken=true",
		ckURL,
		path,
		clientID,
		dsid,
	)
}

// CheckPrimaryZone verifies that the PrimarySync zone exists in com.apple.photos.cloud
func CheckPrimaryZone() string {
	s := GetSession()
	if s == nil {
		res, _ := json.Marshal(ZoneCheckResult{Success: false, ErrorMessage: "No active session"})
		return string(res)
	}

	s.mu.RLock()
	ckURL := s.CKDatabaseURL
	s.mu.RUnlock()
	if ckURL == "" {
		res, _ := json.Marshal(ZoneCheckResult{Success: false, ErrorMessage: "CloudKit database URL is not set"})
		return string(res)
	}

	reqURL := s.photosBaseURL("zones/list")
	req, err := s.NewRequest(http.MethodPost, reqURL, "{}")
	if err != nil {
		res, _ := json.Marshal(ZoneCheckResult{Success: false, ErrorMessage: err.Error()})
		return string(res)
	}

	resp, err := s.Client.Do(req)
	if err != nil {
		res, _ := json.Marshal(ZoneCheckResult{Success: false, ErrorMessage: err.Error()})
		return string(res)
	}
	defer resp.Body.Close()
	s.ExtractAndSaveCookies(resp)

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		res, _ := json.Marshal(ZoneCheckResult{Success: false, ErrorMessage: err.Error()})
		return string(res)
	}

	if resp.StatusCode != http.StatusOK {
		res, _ := json.Marshal(ZoneCheckResult{
			Success:      false,
			ErrorMessage: fmt.Sprintf("zones/list failed with status %d: %s", resp.StatusCode, string(bodyBytes)),
		})
		return string(res)
	}

	var zonesResp CKZonesListResponse
	if err := json.Unmarshal(bodyBytes, &zonesResp); err != nil {
		res, _ := json.Marshal(ZoneCheckResult{Success: false, ErrorMessage: "Failed to parse zones JSON: " + err.Error()})
		return string(res)
	}

	for _, z := range zonesResp.Zones {
		if z.ZoneID.ZoneName == "PrimarySync" {
			res, _ := json.Marshal(ZoneCheckResult{
				Success:   true,
				Ready:     true,
				ZoneName:  "PrimarySync",
				SyncToken: z.SyncToken,
			})
			return string(res)
		}
	}

	res, _ := json.Marshal(ZoneCheckResult{
		Success:      true,
		Ready:        false,
		ErrorMessage: "PrimarySync zone not found in zones list",
	})
	return string(res)
}

// parseRecords merges CPLAsset and CPLMaster records into unified PhotoItem list
func parseRecords(rawRecords []CKRecord) ([]PhotoItem, []string) {
	type masterData struct {
		recordName    string
		itemType      string
		filename      string
		thumbURL      string
		medURL        string
		originalURL   string
		videoComplURL string
		width         int
		height        int
		size          int64
	}

	masters := make(map[string]masterData)
	var assets []CKRecord
	var deletedIDs []string

	for _, rec := range rawRecords {
		if rec.Deleted || (rec.Fields != nil && rec.Fields["isDeleted"].AsInt64() == 1) {
			deletedIDs = append(deletedIDs, rec.RecordName)
			continue
		}

		if rec.RecordType == "CPLMaster" {
			itemType := rec.Fields["itemType"].AsString()
			fnEnc := rec.Fields["filenameEnc"].AsString()
			filename := decodeFilename(fnEnc, rec.RecordName)

			thumb := rec.Fields["resJPEGThumbRes"].AsResource()
			med := rec.Fields["resJPEGMedRes"].AsResource()
			orig := rec.Fields["resOriginalRes"].AsResource()
			vidCompl := rec.Fields["resOriginalVidComplRes"].AsResource()

			width := int(rec.Fields["resOriginalWidth"].AsInt64())
			height := int(rec.Fields["resOriginalHeight"].AsInt64())
			if width == 0 {
				width = int(rec.Fields["resJPEGMedWidth"].AsInt64())
				height = int(rec.Fields["resJPEGMedHeight"].AsInt64())
			}

			size := orig.Size
			if size == 0 {
				size = med.Size
			}

			masters[rec.RecordName] = masterData{
				recordName:    rec.RecordName,
				itemType:      itemType,
				filename:      filename,
				thumbURL:      thumb.DownloadURL,
				medURL:        med.DownloadURL,
				originalURL:   orig.DownloadURL,
				videoComplURL: vidCompl.DownloadURL,
				width:         width,
				height:        height,
				size:          size,
			}
		} else if rec.RecordType == "CPLAsset" {
			assets = append(assets, rec)
		}
	}

	var photos []PhotoItem
	matchedMasters := make(map[string]bool)

	for _, asset := range assets {
		masterRef := asset.Fields["masterRef"].AsMasterRef()
		assetDate := asset.Fields["assetDate"].AsInt64()
		addedDate := asset.Fields["addedDate"].AsInt64()
		if assetDate == 0 {
			assetDate = addedDate
		}
		if assetDate == 0 {
			assetDate = time.Now().UnixMilli()
		}

		isFav := asset.Fields["isFavorite"].AsBool()
		isHidden := asset.Fields["isHidden"].AsBool()
		duration := asset.Fields["duration"].AsInt64()

		item := PhotoItem{
			ID:              asset.RecordName,
			MasterID:        masterRef,
			AssetDate:       assetDate,
			AddedDate:       addedDate,
			IsFavorite:      isFav,
			IsHidden:        isHidden,
			Duration:        duration,
			MediaType:       "IMAGE",
			RecordChangeTag: asset.RecordChangeTag,
		}

		if m, ok := masters[masterRef]; ok {
			matchedMasters[masterRef] = true
			item.Filename = m.filename
			item.ItemType = m.itemType
			item.Width = m.width
			item.Height = m.height
			item.Size = m.size
			item.ThumbURL = m.thumbURL
			item.MedURL = m.medURL
			item.OriginalURL = m.originalURL
			item.VideoComplURL = m.videoComplURL

			if m.videoComplURL != "" {
				item.MediaType = "LIVE_PHOTO"
			} else if strings.Contains(strings.ToLower(m.itemType), "movie") ||
				strings.Contains(strings.ToLower(m.itemType), "video") ||
				strings.Contains(strings.ToLower(m.itemType), "mp4") ||
				duration > 0 {
				item.MediaType = "VIDEO"
			}
		} else {
			item.Filename = asset.RecordName
		}

		photos = append(photos, item)
	}

	// For any standalone masters that didn't have an asset in this batch,
	// create items so Android can cache or link them.
	for mID, m := range masters {
		if !matchedMasters[mID] {
			mediaType := "IMAGE"
			if m.videoComplURL != "" {
				mediaType = "LIVE_PHOTO"
			} else if strings.Contains(strings.ToLower(m.itemType), "movie") ||
				strings.Contains(strings.ToLower(m.itemType), "video") {
				mediaType = "VIDEO"
			}

			photos = append(photos, PhotoItem{
				ID:            mID,
				MasterID:      mID,
				Filename:      m.filename,
				AssetDate:     time.Now().UnixMilli(),
				AddedDate:     time.Now().UnixMilli(),
				MediaType:     mediaType,
				ItemType:      m.itemType,
				Width:         m.width,
				Height:        m.height,
				Size:          m.size,
				ThumbURL:      m.thumbURL,
				MedURL:        m.medURL,
				OriginalURL:   m.originalURL,
				VideoComplURL: m.videoComplURL,
			})
		}
	}

	return photos, deletedIDs
}

// FetchPhotoPage fetches a single page of photos from com.apple.photos.cloud
func FetchPhotoPage(continuationMarker string, limit int) string {
	s := GetSession()
	if s == nil {
		res, _ := json.Marshal(PhotoPageResult{Success: false, ErrorMessage: "No active session"})
		return string(res)
	}

	if limit <= 0 || limit > 200 {
		limit = 100
	}

	queryPayload := map[string]interface{}{
		"query": map[string]interface{}{
			"recordType": "CPLAssetAndMasterByAssetDateWithoutHiddenOrDeleted",
		},
		"zoneID": map[string]interface{}{
			"zoneName": "PrimarySync",
		},
		"resultsLimit": limit,
		"desiredKeys":  defaultDesiredKeys,
	}

	if continuationMarker != "" {
		queryPayload["continuationMarker"] = continuationMarker
	}

	reqBody, _ := json.Marshal(queryPayload)
	reqURL := s.photosBaseURL("records/query")

	req, err := s.NewRequest(http.MethodPost, reqURL, string(reqBody))
	if err != nil {
		res, _ := json.Marshal(PhotoPageResult{Success: false, ErrorMessage: err.Error()})
		return string(res)
	}

	resp, err := s.Client.Do(req)
	if err != nil {
		res, _ := json.Marshal(PhotoPageResult{Success: false, ErrorMessage: err.Error()})
		return string(res)
	}
	defer resp.Body.Close()
	s.ExtractAndSaveCookies(resp)

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		res, _ := json.Marshal(PhotoPageResult{Success: false, ErrorMessage: err.Error()})
		return string(res)
	}

	if resp.StatusCode != http.StatusOK {
		res, _ := json.Marshal(PhotoPageResult{
			Success:      false,
			ErrorMessage: fmt.Sprintf("records/query HTTP %d: %s", resp.StatusCode, string(bodyBytes)),
		})
		return string(res)
	}

	var qResp CKQueryResponse
	if err := json.Unmarshal(bodyBytes, &qResp); err != nil {
		res, _ := json.Marshal(PhotoPageResult{
			Success:      false,
			ErrorMessage: "Failed to parse records/query JSON: " + err.Error(),
		})
		return string(res)
	}

	photos, _ := parseRecords(qResp.Records)
	hasMore := qResp.ContinuationMarker != ""

	res, _ := json.Marshal(PhotoPageResult{
		Success:            true,
		Photos:             photos,
		ContinuationMarker: qResp.ContinuationMarker,
		HasMore:            hasMore,
		SyncToken:          qResp.SyncToken,
	})
	return string(res)
}

// FetchChanges fetches incremental changes using CloudKit /records/changes and syncToken
func FetchChanges(syncToken string, limit int) string {
	s := GetSession()
	if s == nil {
		res, _ := json.Marshal(PhotoChangesResult{Success: false, ErrorMessage: "No active session"})
		return string(res)
	}

	if limit <= 0 || limit > 200 {
		limit = 100
	}

	changesPayload := map[string]interface{}{
		"zoneID": map[string]interface{}{
			"zoneName": "PrimarySync",
		},
		"resultsLimit": limit,
		"desiredKeys":  defaultDesiredKeys,
	}
	if syncToken != "" {
		changesPayload["syncToken"] = syncToken
	}

	reqBody, _ := json.Marshal(changesPayload)
	reqURL := s.photosBaseURL("records/changes")

	req, err := s.NewRequest(http.MethodPost, reqURL, string(reqBody))
	if err != nil {
		res, _ := json.Marshal(PhotoChangesResult{Success: false, ErrorMessage: err.Error()})
		return string(res)
	}

	resp, err := s.Client.Do(req)
	if err != nil {
		res, _ := json.Marshal(PhotoChangesResult{Success: false, ErrorMessage: err.Error()})
		return string(res)
	}
	defer resp.Body.Close()
	s.ExtractAndSaveCookies(resp)

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		res, _ := json.Marshal(PhotoChangesResult{Success: false, ErrorMessage: err.Error()})
		return string(res)
	}

	if resp.StatusCode != http.StatusOK {
		res, _ := json.Marshal(PhotoChangesResult{
			Success:      false,
			ErrorMessage: fmt.Sprintf("records/changes HTTP %d: %s", resp.StatusCode, string(bodyBytes)),
		})
		return string(res)
	}

	var cResp CKQueryResponse
	if err := json.Unmarshal(bodyBytes, &cResp); err != nil {
		res, _ := json.Marshal(PhotoChangesResult{
			Success:      false,
			ErrorMessage: "Failed to parse records/changes JSON: " + err.Error(),
		})
		return string(res)
	}

	photos, deletedIDs := parseRecords(cResp.Records)

	res, _ := json.Marshal(PhotoChangesResult{
		Success:    true,
		Photos:     photos,
		DeletedIDs: deletedIDs,
		SyncToken:  cResp.SyncToken,
		MoreComing: cResp.MoreComing,
	})
	return string(res)
}

// RefreshAssetUrls refreshes download URLs for a list of master record names via /records/lookup
func RefreshAssetUrls(masterRecordNamesJSON string) string {
	s := GetSession()
	if s == nil {
		res, _ := json.Marshal(RefreshUrlsResult{Success: false, ErrorMessage: "No active session"})
		return string(res)
	}

	var masterNames []string
	if err := json.Unmarshal([]byte(masterRecordNamesJSON), &masterNames); err != nil {
		res, _ := json.Marshal(RefreshUrlsResult{Success: false, ErrorMessage: "Invalid master record names JSON: " + err.Error()})
		return string(res)
	}

	if len(masterNames) == 0 {
		res, _ := json.Marshal(RefreshUrlsResult{Success: true, URLs: make(map[string]RefreshedURLs)})
		return string(res)
	}

	type recordQuery struct {
		RecordName string `json:"recordName"`
	}
	var recQueries []recordQuery
	for _, name := range masterNames {
		recQueries = append(recQueries, recordQuery{RecordName: name})
	}

	lookupPayload := map[string]interface{}{
		"records": recQueries,
		"zoneID": map[string]interface{}{
			"zoneName": "PrimarySync",
		},
		"desiredKeys": []string{
			"resJPEGThumbRes",
			"resJPEGMedRes",
			"resOriginalRes",
			"resOriginalVidComplRes",
		},
	}

	reqBody, _ := json.Marshal(lookupPayload)
	reqURL := s.photosBaseURL("records/lookup")

	req, err := s.NewRequest(http.MethodPost, reqURL, string(reqBody))
	if err != nil {
		res, _ := json.Marshal(RefreshUrlsResult{Success: false, ErrorMessage: err.Error()})
		return string(res)
	}

	resp, err := s.Client.Do(req)
	if err != nil {
		res, _ := json.Marshal(RefreshUrlsResult{Success: false, ErrorMessage: err.Error()})
		return string(res)
	}
	defer resp.Body.Close()
	s.ExtractAndSaveCookies(resp)

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		res, _ := json.Marshal(RefreshUrlsResult{Success: false, ErrorMessage: err.Error()})
		return string(res)
	}

	if resp.StatusCode != http.StatusOK {
		res, _ := json.Marshal(RefreshUrlsResult{
			Success:      false,
			ErrorMessage: fmt.Sprintf("records/lookup HTTP %d: %s", resp.StatusCode, string(bodyBytes)),
		})
		return string(res)
	}

	var lResp struct {
		Records []CKRecord `json:"records"`
	}
	if err := json.Unmarshal(bodyBytes, &lResp); err != nil {
		res, _ := json.Marshal(RefreshUrlsResult{
			Success:      false,
			ErrorMessage: "Failed to parse records/lookup JSON: " + err.Error(),
		})
		return string(res)
	}

	urlMap := make(map[string]RefreshedURLs)
	for _, rec := range lResp.Records {
		thumb := rec.Fields["resJPEGThumbRes"].AsResource()
		med := rec.Fields["resJPEGMedRes"].AsResource()
		orig := rec.Fields["resOriginalRes"].AsResource()
		vidCompl := rec.Fields["resOriginalVidComplRes"].AsResource()

		urlMap[rec.RecordName] = RefreshedURLs{
			ThumbURL:      thumb.DownloadURL,
			MedURL:        med.DownloadURL,
			OriginalURL:   orig.DownloadURL,
			VideoComplURL: vidCompl.DownloadURL,
		}
	}

	res, _ := json.Marshal(RefreshUrlsResult{
		Success: true,
		URLs:    urlMap,
	})
	return string(res)
}
