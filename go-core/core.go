package icloudcore

import (
	"encoding/json"
)

// GetVersion returns the version of Go core layer
func GetVersion() string {
	return "0.1.0-alpha"
}

// Ping returns a simple pong message for smoke testing JNI linkage
func Ping(name string) string {
	return "Hello from Go core, " + name + "!"
}

// GetSessionStateSummary returns a sanitized diagnostic summary of the current session state.
func GetSessionStateSummary() string {
	s := GetSession()
	if s == nil {
		return "No active session in Go core"
	}
	s.mu.RLock()
	defer s.mu.RUnlock()

	var cookieKeys []string
	for k := range s.CookieMap {
		cookieKeys = append(cookieKeys, k)
	}

	summary := map[string]interface{}{
		"isChina":        s.IsChinaMainland,
		"baseURL":        s.BaseURL,
		"dsid":           s.DSID,
		"ckDatabaseURL":  s.CKDatabaseURL,
		"cookieCount":    len(s.CookieMap),
		"cookieNames":    cookieKeys,
		"hasAuthToken":   s.CookieMap["X-APPLE-WEBAUTH-TOKEN"] != "",
		"hasHsaTrust":    s.CookieMap["X-APPLE-WEBAUTH-HSA-TRUST"] != "",
		"hasPcsCloudKit": s.CookieMap["X-APPLE-WEBAUTH-PCS-CloudKit"] != "",
	}
	bytes, _ := json.Marshal(summary)
	return string(bytes)
}

// GetAccountInfoJSON returns the raw JSON from validate/accountLogin (contains webservices dict).
func GetAccountInfoJSON() string {
	s := GetSession()
	if s == nil {
		return "{}"
	}
	s.mu.RLock()
	defer s.mu.RUnlock()
	if s.AccountInfoJSON == "" {
		return "{}"
	}
	return s.AccountInfoJSON
}

// InitSession initializes a new iCloud session with cookie string and region setting.
// Returns a JSON string containing SessionInitResult.
func InitSession(cookieStr string, isChinaMainland bool) string {
	session, err := NewSession(cookieStr, isChinaMainland)
	if err != nil {
		res, _ := json.Marshal(SessionInitResult{
			Success:      false,
			ErrorMessage: err.Error(),
			IsChina:      isChinaMainland,
		})
		return string(res)
	}

	result, err := session.ValidateSession()
	if err != nil {
		res, _ := json.Marshal(SessionInitResult{
			Success:      false,
			ErrorMessage: err.Error(),
			IsChina:      isChinaMainland,
		})
		return string(res)
	}

	resBytes, _ := json.Marshal(result)
	return string(resBytes)
}
