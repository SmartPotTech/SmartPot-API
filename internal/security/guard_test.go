package security

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/labstack/echo/v4"
)

func TestGuard(t *testing.T) {
	svc := NewTokenService(testSecret, time.Hour)
	issued, _ := svc.Issue("user-123", "a@b.com", "USER")

	e := httpserver.New(discardLogger())
	grp := e.Group("/api/v1")
	grp.Use(svc.Guard())
	grp.GET("/me", func(c echo.Context) error {
		id := CurrentIdentity(c)
		if !id.Authenticated || id.Email != "a@b.com" || id.Role != RoleUser || id.ExpiresAt.IsZero() {
			t.Errorf("identidad tipada incompleta: %+v", id)
		}
		return c.String(http.StatusOK, CurrentUserID(c))
	})
	grp.GET("/admin", func(c echo.Context) error {
		return c.String(http.StatusOK, "admin")
	}, RequireRole(RoleAdmin))

	send := func(authHeader string) *httptest.ResponseRecorder {
		rec := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/api/v1/me", nil)
		if authHeader != "" {
			req.Header.Set("Authorization", authHeader)
		}
		e.ServeHTTP(rec, req)
		return rec
	}

	if rec := send(""); rec.Code != http.StatusUnauthorized {
		t.Errorf("without token: code %d, expected 401", rec.Code)
	}
	if rec := send("Bearer no-es-un-token"); rec.Code != http.StatusUnauthorized {
		t.Errorf("invalid token: code %d, expected 401", rec.Code)
	}
	rec := send("Bearer " + issued.Value)
	if rec.Code != http.StatusOK {
		t.Fatalf("valid token: code %d, expected 200", rec.Code)
	}
	if rec.Body.String() != "user-123" {
		t.Errorf("CurrentUserID = %q, se esperaba user-123", rec.Body.String())
	}
}

func TestGuardCookieAndCSRF(t *testing.T) {
	svc := NewTokenService(testSecret, time.Hour)
	issued, err := svc.Issue("user-456", "ana@smartpot.app", RoleUser)
	if err != nil {
		t.Fatalf("error issuing token: %v", err)
	}
	csrf := svc.CSRFToken(issued.Value)

	e := httpserver.New(discardLogger())
	e.POST("/login-cookie", func(c echo.Context) error {
		svc.SetSessionCookies(c, issued, true)
		return c.NoContent(http.StatusOK)
	})
	e.POST("/logout-cookie", func(c echo.Context) error {
		ClearSessionCookies(c)
		return c.NoContent(http.StatusNoContent)
	})

	grp := e.Group("/api/v1")
	grp.Use(svc.Guard())
	grp.GET("/me", func(c echo.Context) error {
		return c.String(http.StatusOK, CurrentUserID(c))
	})
	grp.POST("/crops", func(c echo.Context) error {
		return c.String(http.StatusCreated, "creado")
	})

	// 1. SetSessionCookies emite smartpot_session (HttpOnly) y smartpot_csrf + cabecera X-CSRF-Token.
	loginRec := httptest.NewRecorder()
	loginReq := httptest.NewRequest(http.MethodPost, "/login-cookie", nil)
	e.ServeHTTP(loginRec, loginReq)
	if loginRec.Header().Get(CSRFHeaderName) != csrf {
		t.Fatalf("cabecera X-CSRF-Token = %q, se esperaba %q", loginRec.Header().Get(CSRFHeaderName), csrf)
	}
	var sessionCookie, csrfCookie *http.Cookie
	for _, ck := range loginRec.Result().Cookies() {
		switch ck.Name {
		case SessionCookieName:
			sessionCookie = ck
		case CSRFCookieName:
			csrfCookie = ck
		}
	}
	if sessionCookie == nil || !sessionCookie.HttpOnly || sessionCookie.Value != issued.Value {
		t.Fatalf("invalid HttpOnly session cookie: %+v", sessionCookie)
	}
	if csrfCookie == nil || csrfCookie.HttpOnly || csrfCookie.Value != csrf {
		t.Fatalf("invalid CSRF cookie: %+v", csrfCookie)
	}

	// 2. Lectura GET con cookie HttpOnly funciona sin cabecera X-CSRF-Token.
	getRec := httptest.NewRecorder()
	getReq := httptest.NewRequest(http.MethodGet, "/api/v1/me", nil)
	getReq.AddCookie(sessionCookie)
	e.ServeHTTP(getRec, getReq)
	if getRec.Code != http.StatusOK || getRec.Body.String() != "user-456" {
		t.Fatalf("GET with HttpOnly cookie: code %d, body %q", getRec.Code, getRec.Body.String())
	}

	// 3. Escritura POST con cookie de sesión pero sin token CSRF se rechaza con 403.
	noCsrfRec := httptest.NewRecorder()
	noCsrfReq := httptest.NewRequest(http.MethodPost, "/api/v1/crops", nil)
	noCsrfReq.AddCookie(sessionCookie)
	e.ServeHTTP(noCsrfRec, noCsrfReq)
	if noCsrfRec.Code != http.StatusForbidden {
		t.Fatalf("POST sin CSRF debía responder 403, respondió %d", noCsrfRec.Code)
	}

	// 4. Escritura POST con token CSRF alterado se rechaza con 403.
	badCsrfRec := httptest.NewRecorder()
	badCsrfReq := httptest.NewRequest(http.MethodPost, "/api/v1/crops", nil)
	badCsrfReq.AddCookie(sessionCookie)
	badCsrfReq.AddCookie(csrfCookie)
	badCsrfReq.Header.Set(CSRFHeaderName, "token-csrf-falso")
	e.ServeHTTP(badCsrfRec, badCsrfReq)
	if badCsrfRec.Code != http.StatusForbidden {
		t.Fatalf("POST con CSRF falso debía responder 403, respondió %d", badCsrfRec.Code)
	}

	// 5. Escritura POST con cookie HttpOnly + cookie CSRF + cabecera X-CSRF-Token válida responde 201.
	okPostRec := httptest.NewRecorder()
	okPostReq := httptest.NewRequest(http.MethodPost, "/api/v1/crops", nil)
	okPostReq.AddCookie(sessionCookie)
	okPostReq.AddCookie(csrfCookie)
	okPostReq.Header.Set(CSRFHeaderName, csrf)
	e.ServeHTTP(okPostRec, okPostReq)
	if okPostRec.Code != http.StatusCreated {
		t.Fatalf("POST con CSRF válido debía responder 201, respondió %d", okPostRec.Code)
	}

	// 6. ClearSessionCookies invalida ambas cookies con MaxAge < 0.
	logoutRec := httptest.NewRecorder()
	logoutReq := httptest.NewRequest(http.MethodPost, "/logout-cookie", nil)
	e.ServeHTTP(logoutRec, logoutReq)
	for _, ck := range logoutRec.Result().Cookies() {
		if ck.MaxAge >= 0 || ck.Value != "" {
			t.Errorf("cookie %s no quedó limpia tras logout: %+v", ck.Name, ck)
		}
	}
}

func TestGuardRejectsChannelReadsForConnectionTokens(t *testing.T) {
	svc := NewTokenService("0123456789abcdef0123456789abcdef", time.Hour)
	svc.UseConnections(fakeConnections{
		"spt_test_token": {ConnectionID: "c1", UserID: "user-123", Scope: "CONTROL"},
	})

	e := httpserver.New(discardLogger())
	g := e.Group("/api/v1")
	g.Use(svc.Guard())
	g.GET("/channels/links", func(c echo.Context) error { return c.String(http.StatusOK, "links") })
	g.GET("/channels/telegram/setup", func(c echo.Context) error { return c.String(http.StatusOK, "setup") })

	for _, path := range []string{"/api/v1/channels/links", "/api/v1/channels/telegram/setup"} {
		req := httptest.NewRequest(http.MethodGet, path, nil)
		req.Header.Set("Authorization", "Bearer spt_test_token")
		rec := httptest.NewRecorder()
		e.ServeHTTP(rec, req)
		if rec.Code != http.StatusForbidden {
			t.Fatalf("%s with connection token = %d, want 403", path, rec.Code)
		}
	}
}
