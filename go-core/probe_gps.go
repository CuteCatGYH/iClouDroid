package icloudcore

// TEMPORARY DIAGNOSTIC — DELETE BEFORE RELEASE.
//
// Follow-up to probe.go: does CloudKit actually carry GPS coordinates?
//
// The first probe found `{Exif}` and `{TIFF}` dictionaries in `mediaMetaDataEnc` but no `{GPS}`
// on the sample it inspected. That single data point is ambiguous — the photo may simply have had
// location services off. This probe walks a page of assets and reports, per photo, whether a
// `{GPS}` dictionary exists and which coordinate keys it holds.
//
// It also reports which keys CPLAsset carries, in case CloudKit denormalises coordinates onto the
// asset record instead of keeping them inside the master's EXIF blob.

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
)

// ProbeGPS scans a page of assets for geolocation metadata.
func ProbeGPS(pageSize int) string {
	s := GetSession()
	if s == nil {
		return probeFail("No active session")
	}
	// Defensive: another path may have rebuilt the session between InitSession and here, which
	// clears DSID/CKDatabaseURL until the next successful handshake. Rather than fail with a
	// confusing empty-host error, re-run the handshake so the probe is self-contained.
	s.mu.RLock()
	hasRoute := s.CKDatabaseURL != "" && s.DSID != ""
	s.mu.RUnlock()
	if !hasRoute {
		if res, err := s.ValidateSession(); err == nil && res != nil {
			s.mu.Lock()
			if res.DSID != "" {
				s.DSID = res.DSID
			}
			if res.CKDatabaseURL != "" {
				s.CKDatabaseURL = res.CKDatabaseURL
			}
			s.mu.Unlock()
		}
	}
	if pageSize <= 0 || pageSize > 50 {
		pageSize = 50
	}

	queryPayload := map[string]interface{}{
		"query": map[string]interface{}{
			"recordType": "CPLAssetAndMasterByAssetDateWithoutHiddenOrDeleted",
		},
		"zoneID":       map[string]interface{}{"zoneName": "PrimarySync"},
		"resultsLimit": pageSize,
		"desiredKeys": []string{
			"masterRef", "assetDate",
			// CloudKit rejects unknown key names, so only ask for keys Apple documents.
			"locationEnc",
		},
	}
	queryBody, _ := json.Marshal(queryPayload)
	req, err := s.NewRequest(http.MethodPost, s.photosBaseURL("records/query"), string(queryBody))
	if err != nil {
		return probeFail(err.Error())
	}
	resp, err := s.Client.Do(req)
	if err != nil {
		return probeFail(err.Error())
	}
	defer resp.Body.Close()
	s.ExtractAndSaveCookies(resp)
	queryRaw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		return probeFail(fmt.Sprintf("records/query HTTP %d: %s", resp.StatusCode, string(queryRaw)))
	}

	var qResp CKQueryResponse
	if err := json.Unmarshal(queryRaw, &qResp); err != nil {
		return probeFail("parse query: " + err.Error())
	}

	// Which keys do CPLAsset records actually expose?
	assetKeyCounts := map[string]int{}
	var masterNames []string
	seen := map[string]bool{}
	for _, rec := range qResp.Records {
		if rec.RecordType != "CPLAsset" {
			continue
		}
		for k := range rec.Fields {
			assetKeyCounts[k]++
		}
		if name := rec.Fields["masterRef"].AsMasterRef(); name != "" && !seen[name] {
			seen[name] = true
			masterNames = append(masterNames, name)
		}
	}
	if len(masterNames) == 0 {
		return probeFail("no master refs found")
	}

	type recRef struct {
		RecordName string `json:"recordName"`
	}
	type gpsRow struct {
		RecordName string `json:"recordName"`
		HasGPSDict bool   `json:"hasGpsDict"`
		GPSKeys    string `json:"gpsKeys,omitempty"`
		MetaBytes  int    `json:"metaBytes"`
		HasExif    bool   `json:"hasExif"`
		HasTiff    bool   `json:"hasTiff"`
	}

	var rows []gpsRow
	const batch = 20
	for start := 0; start < len(masterNames); start += batch {
		end := start + batch
		if end > len(masterNames) {
			end = len(masterNames)
		}
		var refs []recRef
		for _, n := range masterNames[start:end] {
			refs = append(refs, recRef{RecordName: n})
		}
		lookupPayload := map[string]interface{}{
			"records":     refs,
			"zoneID":      map[string]interface{}{"zoneName": "PrimarySync"},
			"desiredKeys": []string{"mediaMetaDataEnc"},
		}
		lookupBody, _ := json.Marshal(lookupPayload)
		lreq, err := s.NewRequest(http.MethodPost, s.photosBaseURL("records/lookup"), string(lookupBody))
		if err != nil {
			continue
		}
		lresp, err := s.Client.Do(lreq)
		if err != nil {
			continue
		}
		lookupRaw, _ := io.ReadAll(lresp.Body)
		lresp.Body.Close()
		if lresp.StatusCode != http.StatusOK {
			continue
		}

		var lr struct {
			Records []CKRecord `json:"records"`
		}
		if err := json.Unmarshal(lookupRaw, &lr); err != nil {
			continue
		}
		for _, rec := range lr.Records {
			row := gpsRow{RecordName: rec.RecordName}
			enc := rec.Fields["mediaMetaDataEnc"].AsString()
			if enc == "" {
				rows = append(rows, row)
				continue
			}
			decoded, err := base64.StdEncoding.DecodeString(enc)
			if err != nil {
				rows = append(rows, row)
				continue
			}
			row.MetaBytes = len(decoded)
			body := string(decoded)
			row.HasExif = strings.Contains(body, "{Exif}")
			row.HasTiff = strings.Contains(body, "{TIFF}")
			// bplist stores dictionary keys as plain ASCII, so a substring test reliably detects
			// the dictionary's existence.
			if strings.Contains(body, "{GPS}") {
				row.HasGPSDict = true
				row.GPSKeys = gpsKeysIn(body)
			}
			rows = append(rows, row)
		}
	}

	withGPS := 0
	for _, r := range rows {
		if r.HasGPSDict {
			withGPS++
		}
	}

	assetKeys := make([]string, 0, len(assetKeyCounts))
	for k := range assetKeyCounts {
		assetKeys = append(assetKeys, k)
	}

	res, _ := json.Marshal(map[string]interface{}{
		"success":        true,
		"mastersScanned": len(rows),
		"withGpsDict":    withGPS,
		"assetFieldKeys": assetKeys,
		"rows":           rows,
	})
	return string(res)
}

// gpsKeysIn lists which GPS coordinate keys appear in a decoded metadata blob.
func gpsKeysIn(body string) string {
	keys := []string{
		"Latitude", "Longitude", "Altitude", "Speed", "ImgDirection",
		"HPositioningError", "DestLatitude", "DestLongitude", "DateStamp", "TimeStamp",
	}
	var hits []string
	for _, k := range keys {
		if strings.Contains(body, k) {
			hits = append(hits, k)
		}
	}
	if len(hits) == 0 {
		return "dict-present-but-no-known-keys"
	}
	return strings.Join(hits, ",")
}
