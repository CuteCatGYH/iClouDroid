package icloudcore

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"testing"
)

func TestClassifyHTTPStatus(t *testing.T) {
	cases := []struct {
		status int
		want   string
	}{
		{http.StatusUnauthorized, ErrCodeAuthExpired},       // 401
		{http.StatusMisdirectedRequest, ErrCodeAuthExpired}, // 421
		{http.StatusNotFound, ErrCodeNotFound},              // 404
		{http.StatusTooManyRequests, ErrCodeRateLimited},    // 429
		{http.StatusInternalServerError, ErrCodeUnknown},    // 500
		{http.StatusBadGateway, ErrCodeUnknown},             // 502
		{http.StatusOK, ErrCodeUnknown},                     // 200 is not a failure, but if asked
		{http.StatusForbidden, ErrCodeUnknown},              // 403 = dead CDN signature, not auth
		{http.StatusGone, ErrCodeUnknown},                   // 410 likewise
	}

	for _, c := range cases {
		if got := ClassifyHTTPStatus(c.status); got != c.want {
			t.Errorf("ClassifyHTTPStatus(%d) = %q, want %q", c.status, got, c.want)
		}
	}
}

func TestClassifyNetErrNil(t *testing.T) {
	if got := ClassifyNetErr(nil); got != "" {
		t.Errorf("ClassifyNetErr(nil) = %q, want empty", got)
	}
}

func TestClassifyNetErrTimeoutSentinels(t *testing.T) {
	if got := ClassifyNetErr(context.DeadlineExceeded); got != ErrCodeTimeout {
		t.Errorf("ClassifyNetErr(DeadlineExceeded) = %q, want %q", got, ErrCodeTimeout)
	}
	if got := ClassifyNetErr(context.Canceled); got != ErrCodeTimeout {
		t.Errorf("ClassifyNetErr(Canceled) = %q, want %q", got, ErrCodeTimeout)
	}
}

func TestClassifyNetErrWrappedTimeout(t *testing.T) {
	wrapped := fmt.Errorf("validate HTTP request failed: %w", context.DeadlineExceeded)
	if got := ClassifyNetErr(wrapped); got != ErrCodeTimeout {
		t.Errorf("ClassifyNetErr(wrapped) = %q, want %q", got, ErrCodeTimeout)
	}
}

func TestClassifyNetErrMessageFallback(t *testing.T) {
	// gomobile flattens errors to strings; the phrase-level fallback must still work.
	cases := []struct {
		msg  string
		want string
	}{
		{"context deadline exceeded (Client.Timeout exceeded while awaiting headers)", ErrCodeTimeout},
		{"Get \"https://p203-ckdatabasews.icloud.com.cn\": dial tcp: i/o timeout", ErrCodeTimeout},
		{"dial tcp 1.2.3.4:443: connect: connection refused", ErrCodeNetwork},
		{"tls: failed to verify certificate", ErrCodeNetwork},
		{"some totally unrelated failure", ErrCodeNetwork},
	}

	for _, c := range cases {
		if got := ClassifyNetErr(errors.New(c.msg)); got != c.want {
			t.Errorf("ClassifyNetErr(%q) = %q, want %q", c.msg, got, c.want)
		}
	}
}

func TestFailBaseShape(t *testing.T) {
	base := failBase(ErrCodeAuthExpired, 401, "boom")
	if base.Success {
		t.Error("failBase must not set Success")
	}
	if base.ErrorCode != ErrCodeAuthExpired {
		t.Errorf("ErrorCode = %q, want %q", base.ErrorCode, ErrCodeAuthExpired)
	}
	if base.HttpStatus != 401 {
		t.Errorf("HttpStatus = %d, want 401", base.HttpStatus)
	}
	if base.ErrorMessage != "boom" {
		t.Errorf("ErrorMessage = %q, want %q", base.ErrorMessage, "boom")
	}
}

func TestOkBaseShape(t *testing.T) {
	base := okBase()
	if !base.Success {
		t.Error("okBase must set Success")
	}
	if base.ErrorCode != "" {
		t.Errorf("okBase must not carry an ErrorCode, got %q", base.ErrorCode)
	}
}
