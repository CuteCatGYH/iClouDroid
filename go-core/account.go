package icloudcore

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
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
	PcsRequired bool     `json:"pcsRequired"`
	TrustTokens []string `json:"trustTokens"`
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
	result, err := s.doValidate()
	if err == nil && result != nil {
		return result, nil
	}

	// Check if doValidate captured a new trustToken in CookieMap. If so, retry doValidate.
	s.mu.RLock()
	hasHsaTrust := s.CookieMap["X-APPLE-WEBAUTH-HSA-TRUST"] != ""
	s.mu.RUnlock()
	if hasHsaTrust {
		retryResult, retryErr := s.doValidate()
		if retryErr == nil && retryResult != nil {
			return retryResult, nil
		}
	}

	// If validate failed with 421 or error, attempt alternate regional endpoint (CN <-> Global)
	s.mu.Lock()
	origBaseURL := s.BaseURL
	origOrigin := s.Origin
	origReferer := s.Referer
	origIsChina := s.IsChinaMainland

	if s.IsChinaMainland {
		s.BaseURL = GlobalBaseURL
		s.Origin = GlobalOrigin
		s.Referer = GlobalReferer
		s.IsChinaMainland = false
	} else {
		s.BaseURL = ChinaMainlandBaseURL
		s.Origin = ChinaMainlandOrigin
		s.Referer = ChinaMainlandReferer
		s.IsChinaMainland = true
	}
	s.mu.Unlock()

	altResult, altErr := s.doValidate()
	if altErr == nil && altResult != nil {
		return altResult, nil
	}

	// Restore original region settings if alternate region also failed
	s.mu.Lock()
	s.BaseURL = origBaseURL
	s.Origin = origOrigin
	s.Referer = origReferer
	s.IsChinaMainland = origIsChina
	s.mu.Unlock()

	// Try accountLogin remediation using HSA-TRUST token or auth token if available
	remedyResult, remedyErr := s.TryAccountLoginRemediation()
	if remedyErr == nil && remedyResult != nil {
		return remedyResult, nil
	}

	return nil, fmt.Errorf("AUTH_EXPIRED: iCloud 登录凭证已失效或 2FA 信任到期，请重新登录。")
}

func (s *Session) doValidate() (*SessionInitResult, error) {
	s.mu.RLock()
	baseURL := s.BaseURL
	isChina := s.IsChinaMainland
	s.mu.RUnlock()

	clientID := generateClientID()
	validateURL := fmt.Sprintf(
		"%s/setup/ws/1/validate?clientBuildNumber=2422Hotfix23&clientMasteringNumber=2422Hotfix23&clientId=%s&clientVersion=2.1",
		baseURL,
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
			IsChina:       isChina,
		}, nil
	}

	// Unmarshal error response to check if Apple returned updated trustTokens
	var errResp ValidateResponse
	if err := json.Unmarshal(bodyBytes, &errResp); err == nil && len(errResp.TrustTokens) > 0 {
		s.mu.Lock()
		s.CookieMap["X-APPLE-WEBAUTH-HSA-TRUST"] = errResp.TrustTokens[0]
		s.UpdatedCookies = true
		s.mu.Unlock()
	}

	preview := string(bodyBytes)
	if len(preview) > 256 {
		preview = preview[:256] + "..."
	}
	return nil, fmt.Errorf("validate HTTP %d: %s", resp.StatusCode, preview)
}

// TryAccountLoginRemediation tries to renew session credentials via /setup/ws/1/accountLogin
// using X-APPLE-WEBAUTH-HSA-TRUST or session token if available.
func (s *Session) TryAccountLoginRemediation() (*SessionInitResult, error) {
	s.mu.RLock()
	hsaTrust := s.CookieMap["X-APPLE-WEBAUTH-HSA-TRUST"]
	token := s.CookieMap["X-APPLE-WEBAUTH-TOKEN"]
	dsWebToken := s.CookieMap["X-APPLE-DS-WEB-SESSION-TOKEN"]
	authUser := s.CookieMap["X-APPLE-WEBAUTH-USER"]
	authLogin := s.CookieMap["X-APPLE-WEBAUTH-LOGIN"]
	dsid := s.DSID
	s.mu.RUnlock()

	if hsaTrust == "" && token == "" && dsWebToken == "" {
		return nil, fmt.Errorf("no trust or auth token available for renewal")
	}

	effectiveToken := token
	if effectiveToken == "" {
		effectiveToken = dsWebToken
	}

	accountName := authUser
	if strings.HasPrefix(accountName, "d=") {
		accountName = strings.TrimPrefix(accountName, "d=")
	}
	if accountName == "" {
		accountName = authLogin
	}
	if accountName == "" {
		accountName = dsid
	}

	loginPayload := map[string]interface{}{
		"extended_login": true,
	}
	if accountName != "" {
		loginPayload["accountName"] = accountName
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
