package icloudcore

// TEMPORARY DIAGNOSTIC — DELETE BEFORE RELEASE.
//
// Feasibility probe for the photo-detail panel and Phase 3 upload dedup.
//
// The gallery's detail view wants aperture / shutter / ISO / lens / GPS, and Phase 3 wants a
// content fingerprint for dedup. None of that is in `defaultDesiredKeys` today, so before
// designing either feature we need to know whether CloudKit will hand those fields over and in
// what shape.
//
// This file exists purely to answer that question on a real account. It issues one
// /records/query for a single CPLAssetAndMasterByAssetDate page, then fetches the raw
// CPLMaster records by name with **every** field (no `desiredKeys` filter), and returns them
// verbatim. It is not wired into any UI path and must be removed once the question is answered.
//
// Why no desiredKeys: CloudKit returns only the requested keys. Asking for the full record is the
// only way to discover what the server actually stores.

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
)

// ProbeRecordFieldsResult is the JSON envelope returned by ProbeRecordFields.
type ProbeRecordFieldsResult struct {
	Success      bool   `json:"success"`
	ErrorMessage string `json:"errorMessage,omitempty"`
	// The record names that were looked up, for cross-checking.
	MasterRecordNames []string `json:"masterRecordNames,omitempty"`
	// Verbatim /records/lookup response body, so the caller can inspect every key CloudKit sent.
	RawLookupJSON string `json:"rawLookupJSON,omitempty"`
	// Verbatim /records/query response body for the asset page (useful for CPLAsset fields).
	RawQueryJSON string `json:"rawQueryJSON,omitempty"`
}

// ProbeRecordFields asks CloudKit for the first N assets, then looks up their CPLMaster records
// with the full field set and returns the raw JSON untouched.
//
// limit is the number of master records to fetch; keep it small (1-3) — the response bodies are
// large and are meant to be read by a human.
func ProbeRecordFields(limit int) string {
	s := GetSession()
	if s == nil {
		res, _ := json.Marshal(ProbeRecordFieldsResult{
			Success:      false,
			ErrorMessage: "No active session",
		})
		return string(res)
	}

	if limit <= 0 {
		limit = 1
	}
	// CloudKit rejects resultsLimit <= 1 with "Result size limit must be greater than 1",
	// so the page query always asks for at least 2 even when one master is enough.
	pageLimit := limit
	if pageLimit < 2 {
		pageLimit = 2
	}

	// Step 1: one page of assets, to learn real CPLMaster record names.
	queryPayload := map[string]interface{}{
		"query": map[string]interface{}{
			"recordType": "CPLAssetAndMasterByAssetDateWithoutHiddenOrDeleted",
		},
		"zoneID":       map[string]interface{}{"zoneName": "PrimarySync"},
		"resultsLimit": pageLimit,
		"desiredKeys":  []string{"masterRef", "assetDate"},
	}
	queryBody, _ := json.Marshal(queryPayload)

	queryURL := s.photosBaseURL("records/query")
	req, err := s.NewRequest(http.MethodPost, queryURL, string(queryBody))
	if err != nil {
		return probeFail(err.Error())
	}
	resp, err := s.Client.Do(req)
	if err != nil {
		return probeFail(err.Error())
	}
	defer resp.Body.Close()
	s.ExtractAndSaveCookies(resp)

	queryRaw, err := io.ReadAll(resp.Body)
	if err != nil {
		return probeFail(err.Error())
	}
	if resp.StatusCode != http.StatusOK {
		return probeFail(fmt.Sprintf("records/query HTTP %d: %s", resp.StatusCode, string(queryRaw)))
	}

	// Pull the masterRef values out of the asset page.
	var qResp CKQueryResponse
	if err := json.Unmarshal(queryRaw, &qResp); err != nil {
		return probeFail("parse query: " + err.Error())
	}
	var masterNames []string
	seen := map[string]bool{}
	for _, rec := range qResp.Records {
		if rec.RecordType != "CPLAsset" {
			continue
		}
		name := rec.Fields["masterRef"].AsMasterRef()
		if name != "" && !seen[name] {
			seen[name] = true
			masterNames = append(masterNames, name)
		}
	}
	if len(masterNames) == 0 {
		res, _ := json.Marshal(ProbeRecordFieldsResult{
			Success:      false,
			ErrorMessage: "no CPLMaster names found in the first page",
			RawQueryJSON: string(queryRaw),
		})
		return string(res)
	}
	// Cap what we look up even if the page returned more.
	if len(masterNames) > limit {
		masterNames = masterNames[:limit]
	}

	// Step 2: look those masters up WITHOUT desiredKeys, so every stored field comes back.
	type recRef struct {
		RecordName string `json:"recordName"`
	}
	var refs []recRef
	for _, n := range masterNames {
		refs = append(refs, recRef{RecordName: n})
	}
	lookupPayload := map[string]interface{}{
		"records": refs,
		"zoneID":  map[string]interface{}{"zoneName": "PrimarySync"},
		// Deliberately omit desiredKeys.
	}
	lookupBody, _ := json.Marshal(lookupPayload)

	lookupURL := s.photosBaseURL("records/lookup")
	lreq, err := s.NewRequest(http.MethodPost, lookupURL, string(lookupBody))
	if err != nil {
		return probeFail(err.Error())
	}
	lresp, err := s.Client.Do(lreq)
	if err != nil {
		return probeFail(err.Error())
	}
	defer lresp.Body.Close()
	s.ExtractAndSaveCookies(lresp)

	lookupRaw, err := io.ReadAll(lresp.Body)
	if err != nil {
		return probeFail(err.Error())
	}
	if lresp.StatusCode != http.StatusOK {
		return probeFail(fmt.Sprintf("records/lookup HTTP %d: %s", lresp.StatusCode, string(lookupRaw)))
	}

	res, _ := json.Marshal(ProbeRecordFieldsResult{
		Success:           true,
		MasterRecordNames: masterNames,
		RawQueryJSON:      string(queryRaw),
		RawLookupJSON:     string(lookupRaw),
	})
	return string(res)
}

// ProbeFieldKeys is the compact form of ProbeRecordFields: it returns only the *key names* present
// on each CPLMaster record, not the values. Much easier to read in logcat, and safe to paste.
func ProbeFieldKeys(limit int) string {
	raw := ProbeRecordFields(limit)

	var outer ProbeRecordFieldsResult
	if err := json.Unmarshal([]byte(raw), &outer); err != nil || !outer.Success {
		return raw
	}

	var lr struct {
		Records []CKRecord `json:"records"`
	}
	if err := json.Unmarshal([]byte(outer.RawLookupJSON), &lr); err != nil {
		return probeFail("parse lookup: " + err.Error())
	}

	type masterKeys struct {
		RecordName string   `json:"recordName"`
		Keys       []string `json:"keys"`
		// For resource fields, expose the shape of the value so we can see whether CloudKit
		// inlines metadata or only a downloadURL.
		ResourceShapes map[string]string `json:"resourceShapes,omitempty"`
	}
	var out []masterKeys
	for _, rec := range lr.Records {
		mk := masterKeys{RecordName: rec.RecordName, ResourceShapes: map[string]string{}}
		for k, f := range rec.Fields {
			mk.Keys = append(mk.Keys, k)
			if f.Type == "ASSETID" || k == "resOriginalRes" || k == "resJPEGMedRes" {
				// Show which sub-keys the resource object carries (downloadURL/size/fileChecksum/...)
				var probe map[string]json.RawMessage
				if json.Unmarshal(f.Value, &probe) == nil {
					var sub []string
					for sk := range probe {
						sub = append(sub, sk)
					}
					b, _ := json.Marshal(sub)
					mk.ResourceShapes[k] = string(b)
				}
			}
		}
		out = append(out, mk)
	}

	res, _ := json.Marshal(map[string]interface{}{
		"success": true,
		"masters": out,
	})
	return string(res)
}

func probeFail(msg string) string {
	res, _ := json.Marshal(ProbeRecordFieldsResult{Success: false, ErrorMessage: msg})
	return string(res)
}

// ProbeMediaMetadata decodes `mediaMetaDataEnc` on a CPLMaster and reports what format it is in.
//
// This is the field the photo-detail panel depends on: it holds the EXIF that the iCloud web UI
// renders (aperture, shutter, ISO, lens, GPS). The value arrives base64-encoded and the inner
// format is undocumented, so this probe answers three questions at once:
//
//  1. How big is the raw payload (i.e. is it the whole EXIF block or a compact subset)?
//  2. What do the first bytes look like (plist magic? TIFF/Exif header? JSON? binary plist?)
//  3. What does a decoded text rendering look like, so we can recognise a plist dictionary.
//
// It deliberately does not attempt full EXIF parsing — that decision depends on the answer.
func ProbeMediaMetadata() string {
	s := GetSession()
	if s == nil {
		return probeFail("No active session")
	}

	// Reuse the paged query to find a master that actually carries mediaMetaDataEnc.
	queryPayload := map[string]interface{}{
		"query": map[string]interface{}{
			"recordType": "CPLAssetAndMasterByAssetDateWithoutHiddenOrDeleted",
		},
		"zoneID":       map[string]interface{}{"zoneName": "PrimarySync"},
		"resultsLimit": 20,
		"desiredKeys":  []string{"masterRef"},
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

	var masterNames []string
	seen := map[string]bool{}
	for _, rec := range qResp.Records {
		if rec.RecordType != "CPLAsset" {
			continue
		}
		if name := rec.Fields["masterRef"].AsMasterRef(); name != "" && !seen[name] {
			seen[name] = true
			masterNames = append(masterNames, name)
		}
	}
	if len(masterNames) == 0 {
		return probeFail("no master refs on the first page")
	}

	// Look the masters up with just the metadata fields, to keep the response small.
	type recRef struct {
		RecordName string `json:"recordName"`
	}
	var refs []recRef
	for _, n := range masterNames {
		refs = append(refs, recRef{RecordName: n})
	}
	lookupPayload := map[string]interface{}{
		"records":     refs,
		"zoneID":      map[string]interface{}{"zoneName": "PrimarySync"},
		"desiredKeys": []string{"mediaMetaDataType", "mediaMetaDataEnc", "filenameEnc", "itemType"},
	}
	lookupBody, _ := json.Marshal(lookupPayload)
	lreq, err := s.NewRequest(http.MethodPost, s.photosBaseURL("records/lookup"), string(lookupBody))
	if err != nil {
		return probeFail(err.Error())
	}
	lresp, err := s.Client.Do(lreq)
	if err != nil {
		return probeFail(err.Error())
	}
	defer lresp.Body.Close()
	s.ExtractAndSaveCookies(lresp)
	lookupRaw, _ := io.ReadAll(lresp.Body)
	if lresp.StatusCode != http.StatusOK {
		return probeFail(fmt.Sprintf("records/lookup HTTP %d: %s", lresp.StatusCode, string(lookupRaw)))
	}

	var lr struct {
		Records []CKRecord `json:"records"`
	}
	if err := json.Unmarshal(lookupRaw, &lr); err != nil {
		return probeFail("parse lookup: " + err.Error())
	}

	type metaInfo struct {
		RecordName    string `json:"recordName"`
		HasMeta       bool   `json:"hasMeta"`
		MetaType      string `json:"metaType,omitempty"`
		EncLength     int    `json:"encLength"`
		DecodedBytes  int    `json:"decodedBytes"`
		FirstBytesHex string `json:"firstBytesHex,omitempty"`
		FormatGuess   string `json:"formatGuess"`
		TextPreview   string `json:"textPreview,omitempty"`
		ExifHits      string `json:"exifHits,omitempty"`
	}
	var out []metaInfo

	for _, rec := range lr.Records {
		mi := metaInfo{RecordName: rec.RecordName}
		enc := rec.Fields["mediaMetaDataEnc"].AsString()
		mi.MetaType = rec.Fields["mediaMetaDataType"].AsString()
		if enc == "" {
			mi.HasMeta = false
			mi.FormatGuess = "empty"
			out = append(out, mi)
			continue
		}
		mi.HasMeta = true
		mi.EncLength = len(enc)

		decoded, err := base64.StdEncoding.DecodeString(enc)
		if err != nil {
			mi.FormatGuess = "base64-error: " + err.Error()
			out = append(out, mi)
			continue
		}
		mi.DecodedBytes = len(decoded)

		// First 16 bytes identify most binary containers.
		n := 16
		if len(decoded) < n {
			n = len(decoded)
		}
		mi.FirstBytesHex = fmt.Sprintf("% x", decoded[:n])
		mi.FormatGuess = guessFormat(decoded)
		mi.TextPreview = textPreview(decoded, 400)
		mi.ExifHits = exifSignalScan(decoded)

		out = append(out, mi)
	}

	res, _ := json.Marshal(map[string]interface{}{
		"success": true,
		"masters": out,
	})
	return string(res)
}

// guessFormat inspects magic bytes to name the container.
func guessFormat(b []byte) string {
	switch {
	case len(b) >= 6 && string(b[:6]) == "bplist":
		return "binary-plist"
	case len(b) >= 8 && string(b[:8]) == "<?xml ve":
		return "xml-plist"
	case len(b) >= 3 && b[0] == 0xFF && b[1] == 0xD8 && b[2] == 0xFF:
		return "jpeg(with-exif)"
	case len(b) >= 4 && (string(b[:4]) == "Exif" || string(b[:4]) == "MM\x00\x2a" || string(b[:4]) == "II\x2a\x00"):
		return "tiff-exif"
	case len(b) >= 1 && (b[0] == '{' || b[0] == '['):
		return "json"
	case len(b) >= 4 && string(b[:4]) == "PK\x03\x04":
		return "zip"
	}
	return "unknown"
}

// textPreview renders the payload as text when it looks printable, so a plist dictionary can be
// recognised by eye in logcat.
func textPreview(b []byte, max int) string {
	n := len(b)
	if n > max {
		n = max
	}
	printable := 0
	for _, c := range b[:n] {
		if c == '\n' || c == '\r' || c == '\t' || (c >= 0x20 && c < 0x7f) {
			printable++
		}
	}
	// Under 80% printable means it's binary; rendering it would just be noise.
	if n == 0 || printable*100/n < 80 {
		return ""
	}
	return strings.ReplaceAll(string(b[:n]), "\n", " ")
}

// exifSignalScan reports which EXIF tag names appear anywhere in the payload. Tags are stored as
// plain ASCII inside both XML plists and TIFF IFDs, so a substring scan is enough to tell us
// whether the data we need is actually present.
func exifSignalScan(b []byte) string {
	s := string(b)
	tags := []string{
		"FNumber", "ExposureTime", "ISOSpeedRatings", "FocalLength", "FocalLenIn35mmFilm",
		"LensModel", "GPSLatitude", "GPSLongitude", "BrightnessValue", "ExposureBiasValue",
		"DateTimeOriginal", "Make", "Model",
	}
	var hits []string
	for _, t := range tags {
		if strings.Contains(s, t) {
			hits = append(hits, t)
		}
	}
	if len(hits) == 0 {
		return "none"
	}
	return strings.Join(hits, ",")
}
