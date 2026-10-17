package icloudcore

import (
	"strings"
	"testing"
)

func TestNewSessionCookieParsing(t *testing.T) {
	cookieStr := `X-APPLE-WEBAUTH-TOKEN="v=2:t=AQAAAAB"; X-APPLE-WEBAUTH-HSA-TRUST="AQAAAAAAA="; X-APPLE-WEBAUTH-PCS-CloudKit="PCS_TOKEN_123=="; $Path=/; $Version=1`

	session, err := NewSession(cookieStr, true)
	if err != nil {
		t.Fatalf("NewSession failed: %v", err)
	}

	if session.CookieMap["X-APPLE-WEBAUTH-TOKEN"] != "v=2:t=AQAAAAB" {
		t.Errorf("expected trimmed token, got %s", session.CookieMap["X-APPLE-WEBAUTH-TOKEN"])
	}
	if session.CookieMap["X-APPLE-WEBAUTH-HSA-TRUST"] != "AQAAAAAAA=" {
		t.Errorf("expected trimmed hsa trust, got %s", session.CookieMap["X-APPLE-WEBAUTH-HSA-TRUST"])
	}
	if session.CookieMap["X-APPLE-WEBAUTH-PCS-CloudKit"] != "PCS_TOKEN_123==" {
		t.Errorf("expected trimmed pcs, got %s", session.CookieMap["X-APPLE-WEBAUTH-PCS-CloudKit"])
	}
	if _, exists := session.CookieMap["$Path"]; exists {
		t.Errorf("expected $Path to be skipped")
	}

	header := session.BuildCookieHeader()
	if !strings.Contains(header, "X-APPLE-WEBAUTH-TOKEN=v=2:t=AQAAAAB") {
		t.Errorf("header missing trimmed token: %s", header)
	}

	req, err := session.NewRequest("POST", "https://setup.icloud.com.cn/setup/ws/1/validate", "{}")
	if err != nil {
		t.Fatalf("NewRequest failed: %v", err)
	}
	reqCookie := req.Header.Get("Cookie")
	if !strings.Contains(reqCookie, "X-APPLE-WEBAUTH-TOKEN=v=2:t=AQAAAAB") {
		t.Errorf("expected request cookie to contain token, got: %s", reqCookie)
	}
	if req.Header.Get("Origin") != "https://www.icloud.com.cn" {
		t.Errorf("expected CN origin, got %s", req.Header.Get("Origin"))
	}
}

func TestCoreInitSessionFailureOutput(t *testing.T) {
	// An invalid cookie string or invalid URL should still return isChina=true
	resJSON := InitSession("invalid;cookie", true)
	if !strings.Contains(resJSON, `"isChina":true`) {
		t.Errorf("expected result to contain isChina:true, got: %s", resJSON)
	}
}

func TestSessionSummaryAndCookies(t *testing.T) {
	cookieStr := `X-APPLE-WEBAUTH-TOKEN="valid-token"; X-APPLE-WEBAUTH-HSA-TRUST="valid-trust"`
	session, err := NewSession(cookieStr, true)
	if err != nil {
		t.Fatalf("failed to create session: %v", err)
	}

	summary := GetSessionStateSummary()
	if !strings.Contains(summary, `"hasAuthToken":true`) {
		t.Errorf("summary missing hasAuthToken: %s", summary)
	}
	if !strings.Contains(summary, `"hasHsaTrust":true`) {
		t.Errorf("summary missing hasHsaTrust: %s", summary)
	}

	currentCookies := GetCurrentCookieString()
	if !strings.Contains(currentCookies, "X-APPLE-WEBAUTH-TOKEN=valid-token") {
		t.Errorf("GetCurrentCookieString missing token: %s", currentCookies)
	}

	_ = session
}
