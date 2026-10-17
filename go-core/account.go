package icloudcore

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
)

type ValidateResponse struct {
	DSInfo struct {
		DSID       string `json:"dsid"`
		HsaVersion int    `json:"hsaVersion"`
	} `json:"dsInfo"`
	Webservices map[string]struct {
		URL    string `json:"url"`
		Status string `json:"status"`
	} `json:"webservices"`
	PcsRequired bool `json:"pcsRequired"`
}

type SessionInitResult struct {
	Success       bool   `json:"success"`
	ErrorMessage  string `json:"errorMessage,omitempty"`
	DSID          string `json:"dsid,omitempty"`
	CKDatabaseURL string `json:"ckDatabaseUrl,omitempty"`
	IsChina       bool   `json:"isChina"`
}

// generateClientID generates a random UUID-like string for iCloud client identification
func generateClientID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	b[6] = (b[6] & 0x0f) | 0x40 // Version 4
	b[8] = (b[8] & 0x3f) | 0x80 // Variant RFC4122
	h := hex.EncodeToString(b)
	return fmt.Sprintf("%s-%s-%s-%s-%s", h[0:8], h[8:12], h[12:16], h[16:20], h[20:32])
}

// ValidateSession performs the setup/ws/1/validate handshake using established cookies.
func (s *Session) ValidateSession() (*SessionInitResult, error) {
	clientID := generateClientID()
	validateURL := fmt.Sprintf(
		"%s/setup/ws/1/validate?clientBuildNumber=2422Hotfix23&clientMasteringNumber=2422Hotfix23&clientId=%s&clientVersion=2.1",
		s.BaseURL,
		clientID,
	)

	req, err := s.NewRequest(http.MethodPost, validateURL, "{}")
	if err != nil {
		return nil, fmt.Errorf("failed to create validate request: %w", err)
	}

	resp, err := s.Client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("validate HTTP request failed: %w", err)
	}
	defer resp.Body.Close()

	// Extract any refreshed cookies (e.g. rotated session tokens)
	s.ExtractAndSaveCookies(resp)

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read validate response body: %w", err)
	}

	if resp.StatusCode == http.StatusOK {
		var validateResp ValidateResponse
		if err := json.Unmarshal(bodyBytes, &validateResp); err != nil {
			return nil, fmt.Errorf("failed to parse validate JSON: %w", err)
		}

		s.mu.Lock()
		s.DSID = validateResp.DSInfo.DSID
		if ckWS, ok := validateResp.Webservices["ckdatabasews"]; ok {
			s.CKDatabaseURL = ckWS.URL
		}
		s.AccountInfoJSON = string(bodyBytes)
		s.mu.Unlock()

		return &SessionInitResult{
			Success:       true,
			DSID:          s.DSID,
			CKDatabaseURL: s.CKDatabaseURL,
			IsChina:       s.IsChinaMainland,
		}, nil
	}

	// If validate failed with 421 (Authentication required) or 401,
	// attempt remediation using accountLogin if HSA-TRUST token is available.
	if resp.StatusCode == 421 || resp.StatusCode == 401 {
		remedyResult, remedyErr := s.TryAccountLoginRemediation()
		if remedyErr == nil && remedyResult != nil {
			return remedyResult, nil
		}
		// If remediation failed, format clear AuthExpired error message
		return nil, fmt.Errorf("AUTH_EXPIRED: session validation failed (HTTP %d). HSA-TRUST renewal failed: %v", resp.StatusCode, remedyErr)
	}

	preview := string(bodyBytes)
	if len(preview) > 512 {
		preview = preview[:512] + "..."
	}
	return nil, fmt.Errorf("validate failed with status %d: %s", resp.StatusCode, preview)
}

// TryAccountLoginRemediation tries to renew session credentials via /setup/ws/1/accountLogin
// using X-APPLE-WEBAUTH-HSA-TRUST or session token if available.
func (s *Session) TryAccountLoginRemediation() (*SessionInitResult, error) {
	s.mu.RLock()
	hsaTrust := s.CookieMap["X-APPLE-WEBAUTH-HSA-TRUST"]
	token := s.CookieMap["X-APPLE-WEBAUTH-TOKEN"]
	dsWebToken := s.CookieMap["X-APPLE-DS-WEB-SESSION-TOKEN"]
	s.mu.RUnlock()

	if hsaTrust == "" && token == "" && dsWebToken == "" {
		return nil, fmt.Errorf("no trust or auth token available for renewal")
	}

	effectiveToken := token
	if effectiveToken == "" {
		effectiveToken = dsWebToken
	}

	loginPayload := map[string]interface{}{
		"extended_login": true,
	}
	if effectiveToken != "" {
		loginPayload["dsWebAuthToken"] = effectiveToken
	}
	if hsaTrust != "" {
		loginPayload["trustToken"] = hsaTrust
	}

	reqBody, _ := json.Marshal(loginPayload)
	loginURL := fmt.Sprintf("%s/setup/ws/1/accountLogin", s.BaseURL)
	req, err := s.NewRequest(http.MethodPost, loginURL, string(reqBody))
	if err != nil {
		return nil, fmt.Errorf("failed to create accountLogin request: %w", err)
	}

	resp, err := s.Client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("accountLogin request failed: %w", err)
	}
	defer resp.Body.Close()

	s.ExtractAndSaveCookies(resp)

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read accountLogin response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("accountLogin HTTP %d: %s", resp.StatusCode, string(bodyBytes))
	}

	var validateResp ValidateResponse
	if err := json.Unmarshal(bodyBytes, &validateResp); err != nil {
		return nil, fmt.Errorf("failed to parse accountLogin response: %w", err)
	}

	s.mu.Lock()
	s.DSID = validateResp.DSInfo.DSID
	if ckWS, ok := validateResp.Webservices["ckdatabasews"]; ok {
		s.CKDatabaseURL = ckWS.URL
	}
	s.AccountInfoJSON = string(bodyBytes)
	s.mu.Unlock()

	return &SessionInitResult{
		Success:       true,
		DSID:          s.DSID,
		CKDatabaseURL: s.CKDatabaseURL,
		IsChina:       s.IsChinaMainland,
	}, nil
}
