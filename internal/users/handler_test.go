package users

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
)

func doRequest(e *echo.Echo, method, path, body, token string) *httptest.ResponseRecorder {
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	if body != "" {
		req.Header.Set("Content-Type", "application/json")
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	return rec
}

func TestUsersHandlerGetMe(t *testing.T) {
	f := newUsersFixture(t)
	e := f.engine()
	token := f.issueToken(t)

	rec := doRequest(e, http.MethodGet, "/api/v1/users/me", "", token)
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /users/me = %d: %s", rec.Code, rec.Body.String())
	}
	var resp UserResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if resp.ID != f.user.HexID() || resp.Email != f.user.Email || resp.Name != f.user.Name {
		t.Errorf("unexpected profile: %+v", resp)
	}

	if rec := doRequest(e, http.MethodGet, "/api/v1/users/me", "", ""); rec.Code != http.StatusUnauthorized {
		t.Errorf("anonymous GET /users/me = %d, want 401", rec.Code)
	}
}

func TestUsersHandlerUpdateProfile(t *testing.T) {
	f := newUsersFixture(t)
	e := f.engine()
	token := f.issueToken(t)

	rec := doRequest(e, http.MethodPut, "/api/v1/users/me",
		`{"name":"Ana María","lastName":"López"}`, token)
	if rec.Code != http.StatusOK {
		t.Fatalf("PUT /users/me = %d: %s", rec.Code, rec.Body.String())
	}
	var resp UserResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if resp.Name != "Ana María" || resp.LastName != "López" {
		t.Errorf("unexpected profile in the response: %+v", resp)
	}
	if got := f.repo.byID[f.user.HexID()]; got.Name != "Ana María" || got.LastName != "López" {
		t.Errorf("the profile was not persisted: %+v", got)
	}

	if rec := doRequest(e, http.MethodPut, "/api/v1/users/me",
		`{"name":"Ana","lastName":"Demo"}`, ""); rec.Code != http.StatusUnauthorized {
		t.Errorf("anonymous PUT /users/me = %d, want 401", rec.Code)
	}

	invalid := doRequest(e, http.MethodPut, "/api/v1/users/me",
		`{"name":"A","lastName":"Demo"}`, token)
	if invalid.Code != http.StatusBadRequest {
		t.Fatalf("invalid profile = %d, want 400: %s", invalid.Code, invalid.Body.String())
	}
	var validation struct {
		Fields map[string]string `json:"fields"`
	}
	if err := json.Unmarshal(invalid.Body.Bytes(), &validation); err != nil {
		t.Fatalf("invalid profile body is not JSON: %v", err)
	}
	if _, ok := validation.Fields["name"]; !ok {
		t.Errorf("validation fields = %v, want a name error", validation.Fields)
	}

	// La misma ruta debe funcionar con la cookie de sesión y el token CSRF.
	csrf := f.jwt.CSRFToken(token)
	cookieReq := httptest.NewRequest(http.MethodPut, "/api/v1/users/me",
		strings.NewReader(`{"name":"Ana Sofía","lastName":"Demo"}`))
	cookieReq.Header.Set("Content-Type", "application/json")
	cookieReq.Header.Set(security.CSRFHeaderName, csrf)
	cookieReq.AddCookie(&http.Cookie{Name: security.SessionCookieName, Value: token})
	cookieReq.AddCookie(&http.Cookie{Name: security.CSRFCookieName, Value: csrf})
	cookieRec := httptest.NewRecorder()
	e.ServeHTTP(cookieRec, cookieReq)
	if cookieRec.Code != http.StatusOK {
		t.Fatalf("cookie PUT /users/me = %d: %s", cookieRec.Code, cookieRec.Body.String())
	}

	noCSRF := httptest.NewRequest(http.MethodPut, "/api/v1/users/me",
		strings.NewReader(`{"name":"Ana Sofía","lastName":"Demo"}`))
	noCSRF.Header.Set("Content-Type", "application/json")
	noCSRF.AddCookie(&http.Cookie{Name: security.SessionCookieName, Value: token})
	noCSRFRec := httptest.NewRecorder()
	e.ServeHTTP(noCSRFRec, noCSRF)
	if noCSRFRec.Code != http.StatusForbidden {
		t.Errorf("PUT without CSRF = %d, want 403", noCSRFRec.Code)
	}
}

func TestUsersHandlerChangePassword(t *testing.T) {
	f := newUsersFixture(t)
	e := f.engine()
	token := f.issueToken(t)

	if rec := doRequest(e, http.MethodPut, "/api/v1/users/me/password",
		`{"currentPassword":"Password2026","newPassword":"NewPassword2026"}`, ""); rec.Code != http.StatusUnauthorized {
		t.Errorf("anonymous password change = %d, want 401", rec.Code)
	}
	if rec := doRequest(e, http.MethodPut, "/api/v1/users/me/password",
		`{"currentPassword":"Password2026"}`, token); rec.Code != http.StatusBadRequest {
		t.Errorf("missing new password = %d, want 400", rec.Code)
	}
	if rec := doRequest(e, http.MethodPut, "/api/v1/users/me/password",
		`{"currentPassword":"Password2026","newPassword":"weak"}`, token); rec.Code != http.StatusBadRequest {
		t.Errorf("weak new password = %d, want 400: %s", rec.Code, rec.Body.String())
	}
	if rec := doRequest(e, http.MethodPut, "/api/v1/users/me/password",
		`{"currentPassword":"WrongPassword2026","newPassword":"NewPassword2026"}`, token); rec.Code != http.StatusBadRequest {
		t.Errorf("wrong current password = %d, want 400: %s", rec.Code, rec.Body.String())
	}

	rec := doRequest(e, http.MethodPut, "/api/v1/users/me/password",
		`{"currentPassword":"Password2026","newPassword":"NewPassword2026"}`, token)
	if rec.Code != http.StatusNoContent {
		t.Fatalf("password change = %d, want 204: %s", rec.Code, rec.Body.String())
	}
	if !security.PasswordMatches("NewPassword2026", f.repo.byID[f.user.HexID()].PasswordHash) {
		t.Error("the new password must be persisted")
	}
	if security.PasswordMatches("Password2026", f.repo.byID[f.user.HexID()].PasswordHash) {
		t.Error("the old password must stop working")
	}
}

func TestUsersHandlerDeleteAccount(t *testing.T) {
	f := newUsersFixture(t)
	e := f.engine()
	token := f.issueToken(t)

	if rec := doRequest(e, http.MethodDelete, "/api/v1/users/me", "", ""); rec.Code != http.StatusUnauthorized {
		t.Errorf("anonymous DELETE /users/me = %d, want 401", rec.Code)
	}

	rec := doRequest(e, http.MethodDelete, "/api/v1/users/me", "", token)
	if rec.Code != http.StatusNoContent {
		t.Fatalf("DELETE /users/me = %d: %s", rec.Code, rec.Body.String())
	}
	if _, ok := f.repo.byID[f.user.HexID()]; ok {
		t.Error("the account must be deleted")
	}
	var cleared bool
	for _, ck := range rec.Result().Cookies() {
		if ck.Name == security.SessionCookieName && ck.MaxAge < 0 {
			cleared = true
		}
	}
	if !cleared {
		t.Error("the session cookie must be cleared")
	}

	// El JWT sigue firmado, pero la cuenta ya no existe: el perfil responde 401.
	after := doRequest(e, http.MethodGet, "/api/v1/users/me", "", token)
	if after.Code != http.StatusUnauthorized {
		t.Errorf("GET /users/me after deletion = %d, want 401", after.Code)
	}
}
