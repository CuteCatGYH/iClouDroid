package icloudcore

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
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
	"isDeleted",
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
	ResultBase
	Ready     bool   `json:"ready"`
	ZoneName  string `json:"zoneName,omitempty"`
	SyncToken string `json:"syncToken,omitempty"`
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
	ID         string `json:"id"`
	MasterID   string `json:"masterId"`
	Filename   string `json:"filename"`
	AssetDate  int64  `json:"assetDate"`
	AddedDate  int64  `json:"addedDate"`
	IsFavorite bool   `json:"isFavorite"`
	IsHidden   bool   `json:"isHidden"`
	// IsDeleted marks an asset that has been moved to "Recently Deleted". The record still
	// exists on the server for ~30 days and is therefore returned by every query; the Android
	// layer must never persist such a row. Without this key in `defaultDesiredKeys` the field is
	// simply absent from the JSON and the client cannot tell a trashed asset from a live one.
	IsDeleted       bool   `json:"isDeleted,omitempty"`
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
	ResultBase
	Photos             []PhotoItem `json:"photos"`
	ContinuationMarker string      `json:"continuationMarker,omitempty"`
	HasMore            bool        `json:"hasMore"`
	SyncToken          string      `json:"syncToken,omitempty"`
	// DeletedIDs carries the ids of records returned in *this* page that are flagged deleted.
	// They never appear in Photos, but the caller must know about them: a page that silently
	// drops a deleted asset looks identical to a page that never contained it, and the full
	// reconciliation walk relies on that difference to purge locally-cached copies.
	DeletedIDs []string `json:"deletedIds"`
}

type PhotoChangesResult struct {
	ResultBase
	Photos     []PhotoItem `json:"photos"`
	DeletedIDs []string    `json:"deletedIds"`
	SyncToken  string      `json:"syncToken,omitempty"`
	MoreComing bool        `json:"moreComing"`
}

type RefreshedURLs struct {
	ThumbURL      string `json:"thumbUrl,omitempty"`
	MedURL        string `json:"medUrl,omitempty"`
	OriginalURL   string `json:"originalUrl,omitempty"`
	VideoComplURL string `json:"videoComplUrl,omitempty"`
}

type RefreshUrlsResult struct {
	ResultBase
	URLs map[string]RefreshedURLs `json:"urls"`
}

type RecentlyDeletedResult struct {
	ResultBase
	DeletedIDs []string `json:"deletedIds"`
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
	// Reuse the session's ClientID instead of minting a fresh one per call (ISS-014).
	// CloudKit binds the continuationMarker cursor to the client that issued it; rotating the
	// clientId between pages made the server treat each page as a different client.
	clientID := s.ClientID
	if clientID == "" {
		clientID = generateClientID()
	}
	s.mu.RUnlock()

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
		return marshalZoneResult(failBase(ErrCodeAuthExpired, 0, "No active session"), false, "", "")
	}

	s.mu.RLock()
	ckURL := s.CKDatabaseURL
	s.mu.RUnlock()
	if ckURL == "" {
		return marshalZoneResult(failBase(ErrCodeAuthExpired, 0, "CloudKit database URL is not set"), false, "", "")
	}

	reqURL := s.photosBaseURL("zones/list")
	req, err := s.NewRequest(http.MethodPost, reqURL, "{}")
	if err != nil {
		return marshalZoneResult(failBase(ClassifyNetErr(err), 0, err.Error()), false, "", "")
	}

	resp, err := s.Client.Do(req)
	if err != nil {
		return marshalZoneResult(failBase(ClassifyNetErr(err), 0, err.Error()), false, "", "")
	}
	defer resp.Body.Close()
	s.ExtractAndSaveCookies(resp)

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return marshalZoneResult(failBase(ErrCodeNetwork, 0, err.Error()), false, "", "")
	}

	if resp.StatusCode != http.StatusOK {
		return marshalZoneResult(
			failBase(
				ClassifyHTTPStatus(resp.StatusCode),
				resp.StatusCode,
				fmt.Sprintf("zones/list failed with status %d: %s", resp.StatusCode, string(bodyBytes)),
			),
			false, "", "",
		)
	}

	var zonesResp CKZonesListResponse
	if err := json.Unmarshal(bodyBytes, &zonesResp); err != nil {
		return marshalZoneResult(failBase(ErrCodeParse, resp.StatusCode, "Failed to parse zones JSON: "+err.Error()), false, "", "")
	}

	for _, z := range zonesResp.Zones {
		if z.ZoneID.ZoneName == "PrimarySync" {
			return marshalZoneResult(okBase(), true, "PrimarySync", z.SyncToken)
		}
	}

	// The request itself succeeded, but there is no photos zone to work with. Reported as a
	// dedicated code so Kotlin can tell "zone absent" apart from "request failed".
	res, _ := json.Marshal(ZoneCheckResult{
		ResultBase: failBase(ErrCodeZoneMissing, http.StatusOK, "PrimarySync zone not found in zones list"),
		Ready:      false,
	})
	return string(res)
}

// marshalZoneResult marshals a ZoneCheckResult from a base plus its zone fields.
func marshalZoneResult(base ResultBase, ready bool, zoneName, syncToken string) string {
	res, _ := json.Marshal(ZoneCheckResult{
		ResultBase: base,
		Ready:      ready,
		ZoneName:   zoneName,
		SyncToken:  syncToken,
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
			if rec.Fields != nil {
				if masterRef := rec.Fields["masterRef"].AsMasterRef(); masterRef != "" {
					deletedIDs = append(deletedIDs, masterRef)
				}
			}
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
		// P2-016: never fall back to time.Now(). An asset with no date would otherwise get a
		// BRAND NEW sort key on every page fetch, so the same row keeps migrating through the
		// `ORDER BY assetDate DESC` list. Room then re-invalidates the PagingSource on every
		// APPEND and the grid's itemCount collapses (observed: 305 -> 60), which is what the
		// user perceives as "jumped back to the top". A stable 0 sorts last and never moves.

		isFav := asset.Fields["isFavorite"].AsBool()
		isHidden := asset.Fields["isHidden"].AsBool()
		isDeleted := asset.Fields["isDeleted"].AsInt64() == 1
		duration := asset.Fields["duration"].AsInt64()

		item := PhotoItem{
			ID:              asset.RecordName,
			MasterID:        masterRef,
			AssetDate:       assetDate,
			AddedDate:       addedDate,
			IsFavorite:      isFav,
			IsHidden:        isHidden,
			IsDeleted:       isDeleted,
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
			if duration > 0 {
				item.MediaType = "VIDEO"
			} else {
				item.MediaType = ""
			}
		}

		photos = append(photos, item)
	}

	// Standalone masters that have no matching CPLAsset in this batch are NOT added as photos.
	// In iCloud Photos, an active photo is always represented by a CPLAsset. Masters without a CPLAsset
	// in WithoutHiddenOrDeleted queries belong to deleted (Recently Deleted) or hidden assets whose
	// CPLMaster payload lingers in CloudKit storage during the 30-day retention window.

	return photos, deletedIDs
}

// FetchPhotoPage fetches a single page of photos from com.apple.photos.cloud
func FetchPhotoPage(continuationMarker string, limit int) string {
	s := GetSession()
	if s == nil {
		return marshalPageResult(failBase(ErrCodeAuthExpired, 0, "No active session"))
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
		return marshalPageResult(failBase(ClassifyNetErr(err), 0, err.Error()))
	}

	resp, err := s.Client.Do(req)
	if err != nil {
		return marshalPageResult(failBase(ClassifyNetErr(err), 0, err.Error()))
	}
	defer resp.Body.Close()
	s.ExtractAndSaveCookies(resp)

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return marshalPageResult(failBase(ErrCodeNetwork, 0, err.Error()))
	}

	if resp.StatusCode != http.StatusOK {
		return marshalPageResult(failBase(
			ClassifyHTTPStatus(resp.StatusCode),
			resp.StatusCode,
			fmt.Sprintf("records/query HTTP %d: %s", resp.StatusCode, string(bodyBytes)),
		))
	}

	var qResp CKQueryResponse
	if err := json.Unmarshal(bodyBytes, &qResp); err != nil {
		return marshalPageResult(failBase(ErrCodeParse, resp.StatusCode, "Failed to parse records/query JSON: "+err.Error()))
	}

	photos, deletedIDs := parseRecords(qResp.Records)
	hasMore := qResp.ContinuationMarker != ""

	if deletedIDs == nil {
		deletedIDs = []string{}
	}

	res, _ := json.Marshal(PhotoPageResult{
		ResultBase:         okBase(),
		Photos:             photos,
		DeletedIDs:         deletedIDs,
		ContinuationMarker: qResp.ContinuationMarker,
		HasMore:            hasMore,
		SyncToken:          qResp.SyncToken,
	})
	return string(res)
}

// marshalPageResult marshals a failure PhotoPageResult with empty photo/deleted lists.
func marshalPageResult(base ResultBase) string {
	res, _ := json.Marshal(PhotoPageResult{
		ResultBase: base,
		Photos:     []PhotoItem{},
		DeletedIDs: []string{},
	})
	return string(res)
}

// FetchChanges fetches incremental changes using CloudKit /records/changes and syncToken
func FetchChanges(syncToken string, limit int) string {
	s := GetSession()
	if s == nil {
		return marshalChangesResult(failBase(ErrCodeAuthExpired, 0, "No active session"))
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
		return marshalChangesResult(failBase(ClassifyNetErr(err), 0, err.Error()))
	}

	resp, err := s.Client.Do(req)
	if err != nil {
		return marshalChangesResult(failBase(ClassifyNetErr(err), 0, err.Error()))
	}
	defer resp.Body.Close()
	s.ExtractAndSaveCookies(resp)

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return marshalChangesResult(failBase(ErrCodeNetwork, 0, err.Error()))
	}

	if resp.StatusCode != http.StatusOK {
		return marshalChangesResult(failBase(
			ClassifyHTTPStatus(resp.StatusCode),
			resp.StatusCode,
			fmt.Sprintf("records/changes HTTP %d: %s", resp.StatusCode, string(bodyBytes)),
		))
	}

	var cResp CKQueryResponse
	if err := json.Unmarshal(bodyBytes, &cResp); err != nil {
		return marshalChangesResult(failBase(ErrCodeParse, resp.StatusCode, "Failed to parse records/changes JSON: "+err.Error()))
	}

	photos, deletedIDs := parseRecords(cResp.Records)

	res, _ := json.Marshal(PhotoChangesResult{
		ResultBase: okBase(),
		Photos:     photos,
		DeletedIDs: deletedIDs,
		SyncToken:  cResp.SyncToken,
		MoreComing: cResp.MoreComing,
	})
	return string(res)
}

// marshalChangesResult marshals a failure PhotoChangesResult with empty slices.
func marshalChangesResult(base ResultBase) string {
	res, _ := json.Marshal(PhotoChangesResult{
		ResultBase: base,
		Photos:     []PhotoItem{},
		DeletedIDs: []string{},
	})
	return string(res)
}

// RefreshAssetUrls refreshes download URLs for a list of master record names via /records/lookup
func RefreshAssetUrls(masterRecordNamesJSON string) string {
	s := GetSession()
	if s == nil {
		return marshalRefreshResult(failBase(ErrCodeAuthExpired, 0, "No active session"))
	}

	var masterNames []string
	if err := json.Unmarshal([]byte(masterRecordNamesJSON), &masterNames); err != nil {
		return marshalRefreshResult(failBase(ErrCodeParse, 0, "Invalid master record names JSON: "+err.Error()))
	}

	if len(masterNames) == 0 {
		res, _ := json.Marshal(RefreshUrlsResult{
			ResultBase: okBase(),
			URLs:       make(map[string]RefreshedURLs),
		})
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
		return marshalRefreshResult(failBase(ClassifyNetErr(err), 0, err.Error()))
	}

	resp, err := s.Client.Do(req)
	if err != nil {
		return marshalRefreshResult(failBase(ClassifyNetErr(err), 0, err.Error()))
	}
	defer resp.Body.Close()
	s.ExtractAndSaveCookies(resp)

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return marshalRefreshResult(failBase(ErrCodeNetwork, 0, err.Error()))
	}

	if resp.StatusCode != http.StatusOK {
		return marshalRefreshResult(failBase(
			ClassifyHTTPStatus(resp.StatusCode),
			resp.StatusCode,
			fmt.Sprintf("records/lookup HTTP %d: %s", resp.StatusCode, string(bodyBytes)),
		))
	}

	var lResp struct {
		Records []CKRecord `json:"records"`
	}
	if err := json.Unmarshal(bodyBytes, &lResp); err != nil {
		return marshalRefreshResult(failBase(ErrCodeParse, resp.StatusCode, "Failed to parse records/lookup JSON: "+err.Error()))
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
		ResultBase: okBase(),
		URLs:       urlMap,
	})
	return string(res)
}

// marshalRefreshResult marshals a failure RefreshUrlsResult with an empty URL map.
func marshalRefreshResult(base ResultBase) string {
	res, _ := json.Marshal(RefreshUrlsResult{
		ResultBase: base,
		URLs:       make(map[string]RefreshedURLs),
	})
	return string(res)
}

// FetchRecentlyDeleted queries com.apple.photos.cloud for records currently in the "Recently Deleted" album
func FetchRecentlyDeleted() string {
	s := GetSession()
	if s == nil {
		return marshalRecentlyDeletedResult(failBase(ErrCodeAuthExpired, 0, "No active session"))
	}

	// Measured trail (each attempt rejected by CloudKit, and the error text is what pinned it):
	//   1. `recordType: CPLAssetDeletedByExpungedDate`   -> 404 "Did not find record type"
	//      That identifier is the `indexCountID` used by `HyperionIndexCountLookup` for counts.
	//   2. `recordType: ...InSmartAlbumByAssetDate`      -> 400 "Expected a parameter with name: smartAlbum"
	//   3. same + `smartAlbum` (inside `query` and top-level) -> same 400
	// The list itself is a sibling record type: the count ID and the list ID differ. This mirrors
	// pyicloud's SMART_FOLDERS entry, where `obj_type` counts and `list_type` lists:
	//     "Recently Deleted": {"obj_type": "CPLAssetDeletedByExpungedDate",
	//                         "list_type": "CPLAssetAndMasterDeletedByExpungedDate"}
	// No `smartAlbum` filter is needed — the record type already scopes the album.
	queryPayload := map[string]interface{}{
		"query": map[string]interface{}{
			"recordType": "CPLAssetAndMasterDeletedByExpungedDate",
		},
		"zoneID": map[string]interface{}{
			"zoneName": "PrimarySync",
		},
		"desiredKeys": []string{
			"recordName",
			"recordType",
			"masterRef",
			"isDeleted",
		},
		"resultsLimit": 200,
	}

	var allDeletedIDs []string
	continuationMarker := ""

	for {
		if continuationMarker != "" {
			queryPayload["continuationMarker"] = continuationMarker
		} else {
			delete(queryPayload, "continuationMarker")
		}

		reqBody, _ := json.Marshal(queryPayload)
		reqURL := s.photosBaseURL("records/query")

		req, err := s.NewRequest(http.MethodPost, reqURL, string(reqBody))
		if err != nil {
			return marshalRecentlyDeletedResult(failBase(ClassifyNetErr(err), 0, err.Error()))
		}

		resp, err := s.Client.Do(req)
		if err != nil {
			return marshalRecentlyDeletedResult(failBase(ClassifyNetErr(err), 0, err.Error()))
		}
		s.ExtractAndSaveCookies(resp)

		bodyBytes, err := io.ReadAll(resp.Body)
		resp.Body.Close()
		if err != nil {
			return marshalRecentlyDeletedResult(failBase(ErrCodeNetwork, 0, err.Error()))
		}

		if resp.StatusCode != http.StatusOK {
			return marshalRecentlyDeletedResult(failBase(
				ClassifyHTTPStatus(resp.StatusCode),
				resp.StatusCode,
				fmt.Sprintf("records/query HTTP %d: %s", resp.StatusCode, string(bodyBytes)),
			))
		}

		var qResp CKQueryResponse
		if err := json.Unmarshal(bodyBytes, &qResp); err != nil {
			return marshalRecentlyDeletedResult(failBase(ErrCodeParse, resp.StatusCode, "Failed to parse records/query JSON: "+err.Error()))
		}

		for _, rec := range qResp.Records {
			allDeletedIDs = append(allDeletedIDs, rec.RecordName)
			if rec.Fields != nil {
				if masterRef := rec.Fields["masterRef"].AsMasterRef(); masterRef != "" {
					allDeletedIDs = append(allDeletedIDs, masterRef)
				}
			}
		}

		if qResp.ContinuationMarker == "" {
			break
		}
		continuationMarker = qResp.ContinuationMarker
	}

	res, _ := json.Marshal(RecentlyDeletedResult{
		ResultBase: okBase(),
		DeletedIDs: allDeletedIDs,
	})
	return string(res)
}

// marshalRecentlyDeletedResult marshals a failure RecentlyDeletedResult with an empty slice.
func marshalRecentlyDeletedResult(base ResultBase) string {
	res, _ := json.Marshal(RecentlyDeletedResult{
		ResultBase: base,
		DeletedIDs: []string{},
	})
	return string(res)
}
