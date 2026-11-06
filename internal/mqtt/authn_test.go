package mqtt

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/objectid"
	"github.com/labstack/echo/v4"
)

const testToken = "broker-authn-token-1234567890"

type fakeVerifier struct {
	crop   *crops.Crop
	key    string
	hasKey bool
	err    error
}

func (f fakeVerifier) Find(context.Context, string) (*crops.Crop, error) { return f.crop, f.err }
func (f fakeVerifier) DeviceKey(*crops.Crop) (string, bool)              { return f.key, f.hasKey }

func cropWithID(t *testing.T, hex string) *crops.Crop {
	t.Helper()
	oid, ok := objectid.Parse(hex)
	if !ok {
		t.Fatalf("invalid test id: %s", hex)
	}
	return &crops.Crop{ID: oid}
}

func newTestController(v DeviceVerifier) *AuthnController {
	cfg := config.MQTT{AuthnToken: testToken}
	return NewAuthnController(cfg, v, nil, NewTopicResolver("smartpot/v1"), slog.New(slog.DiscardHandler))
}

func doAuthn(c *AuthnController, token, body string) *httptest.ResponseRecorder {
	e := echo.New()
	c.Register(e)
	req := httptest.NewRequest(http.MethodPost, "/internal/mqtt/authn", strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set(brokerTokenHeader, token)
	}
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	return rec
}

func decode(t *testing.T, rec *httptest.ResponseRecorder) authnResponse {
	t.Helper()
	var resp authnResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("respuesta no es JSON válido: %v (%s)", err, rec.Body.String())
	}
	return resp
}

func TestAuthn_AllowsValidDeviceWithScopedACL(t *testing.T) {
	id := "6718f0a1b2c3d4e5f6a7b8c9"
	v := fakeVerifier{crop: cropWithID(t, id), key: "device-key-123", hasKey: true}
	rec := doAuthn(newTestController(v), testToken, `{"username":"`+id+`","password":"device-key-123","clientid":"smartpot-`+id+`"}`)

	if rec.Code != http.StatusOK {
		t.Fatalf("esperaba 200, obtuve %d", rec.Code)
	}
	resp := decode(t, rec)
	if resp.Result != "allow" {
		t.Fatalf("esperaba allow, obtuve %q", resp.Result)
	}
	want := []aclRule{
		{"allow", "publish", "smartpot/v1/" + id + "/telemetry"},
		{"allow", "publish", "smartpot/v1/" + id + "/status"},
		{"allow", "publish", "smartpot/v1/" + id + "/commands/ack"},
		{"allow", "subscribe", "smartpot/v1/" + id + "/commands"},
		{"allow", "publish", "smartpot/v1/" + id + "/capabilities"},
		{"deny", "all", "#"},
	}
	if len(resp.ACL) != len(want) {
		t.Fatalf("esperaba %d reglas, obtuve %d", len(want), len(resp.ACL))
	}
	for i, r := range want {
		if resp.ACL[i] != r {
			t.Errorf("regla %d: esperaba %+v, obtuve %+v", i, r, resp.ACL[i])
		}
	}
}

func TestAuthn_Denies(t *testing.T) {
	id := "6718f0a1b2c3d4e5f6a7b8c9"
	valid := fakeVerifier{crop: cropWithID(t, id), key: "device-key-123", hasKey: true}
	body := `{"username":"` + id + `","password":"device-key-123"}`

	cases := []struct {
		name     string
		verifier DeviceVerifier
		token    string
		body     string
	}{
		{"sin token", valid, "", body},
		{"wrong token", valid, "otro-token", body},
		{"invalid body", valid, testToken, "no-es-json"},
		{"nonexistent crop", fakeVerifier{crop: nil}, testToken, body},
		{"no device key (virtual)", fakeVerifier{crop: cropWithID(t, id), hasKey: false}, testToken, body},
		{"wrong key", fakeVerifier{crop: cropWithID(t, id), key: "otra", hasKey: true}, testToken, body},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			rec := doAuthn(newTestController(tc.verifier), tc.token, tc.body)
			if rec.Code != http.StatusOK {
				t.Fatalf("esperaba 200, obtuve %d", rec.Code)
			}
			if resp := decode(t, rec); resp.Result != "deny" {
				t.Fatalf("esperaba deny, obtuve %q", resp.Result)
			}
		})
	}
}

func TestAuthn_IgnoresOnDatabaseError(t *testing.T) {
	id := "6718f0a1b2c3d4e5f6a7b8c9"
	v := fakeVerifier{err: errors.New("database down")}
	rec := doAuthn(newTestController(v), testToken,
		`{"username":"`+id+`","password":"x","clientid":"smartpot-`+id+`"}`)
	// 5xx: EMQX lo trata como "ignore" y no cachee un rechazo.
	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("esperaba 503, obtuve %d", rec.Code)
	}
}

func TestAuthn_EmptyTokenDeniesAll(t *testing.T) {
	id := "6718f0a1b2c3d4e5f6a7b8c9"
	c := NewAuthnController(config.MQTT{AuthnToken: ""}, fakeVerifier{crop: cropWithID(t, id), key: "k", hasKey: true},
		nil, NewTopicResolver("smartpot/v1"), slog.New(slog.DiscardHandler))
	rec := doAuthn(c, "", `{"username":"`+id+`","password":"k"}`)
	if resp := decode(t, rec); resp.Result != "deny" {
		t.Fatalf("with empty token all must be rejected; got %q", resp.Result)
	}
}

func TestAuthnCachesOnlyAnHMACOfTheDeviceKey(t *testing.T) {
	const id = "66f5a1000000000000000101"
	const key = "demo-lettuce-device-key-2026"
	store := cache.NewStore(nil)
	cfg := config.MQTT{AuthnToken: testToken}
	c := NewAuthnController(cfg, fakeVerifier{crop: cropWithID(t, id), key: key, hasKey: true}, store,
		NewTopicResolver("smartpot/v1"), slog.New(slog.DiscardHandler))

	body := `{"username":"` + id + `","password":"` + key + `","clientid":"smartpot-sim-` + id + `"}`
	if got := decode(t, doAuthn(c, testToken, body)); got.Result != "allow" {
		t.Fatalf("the correct key should be allowed, got %q", got.Result)
	}
	cached, ok := store.Get(deviceKeyCachePrefix + id)
	if !ok {
		t.Fatal("verification should be cached with the normalized id")
	}
	if strings.Contains(cached, key) {
		t.Fatal("cache must not store device key in plaintext")
	}
	wrong := `{"username":"` + id + `","password":"otra-clave","clientid":"x"}`
	if got := decode(t, doAuthn(c, testToken, wrong)); got.Result != "deny" {
		t.Fatalf("una clave distinta debía rechazarse desde la caché, se obtuvo %q", got.Result)
	}
	c.Invalidate(id)
	if _, ok := store.Get(deviceKeyCachePrefix + id); ok {
		t.Fatal("Invalidate should delete the cached entry")
	}
}

func TestAuthnRateLimitsPerUsername(t *testing.T) {
	const id = "6718f0a1b2c3d4e5f6a7b8c9"
	store := cache.NewStore(nil)
	c := NewAuthnController(config.MQTT{AuthnToken: testToken},
		fakeVerifier{crop: cropWithID(t, id), key: "device-key-123", hasKey: true}, store,
		NewTopicResolver("smartpot/v1"), slog.New(slog.DiscardHandler))
	body := `{"username":"` + id + `","password":"device-key-123","clientid":"smartpot-` + id + `"}`
	for i := 0; i < authnRatePerMinute; i++ {
		if got := decode(t, doAuthn(c, testToken, body)); got.Result != "allow" {
			t.Fatalf("attempt %d should be allowed, got %q", i+1, got.Result)
		}
	}
	if got := decode(t, doAuthn(c, testToken, body)); got.Result != "deny" {
		t.Fatalf("attempts above the per-crop limit must be denied, got %q", got.Result)
	}
}

func TestAuthnRejectsUppercaseOrSpacedUsername(t *testing.T) {
	const id = "6718f0a1b2c3d4e5f6a7b8c9"
	v := fakeVerifier{crop: cropWithID(t, id), key: "device-key-123", hasKey: true}
	c := newTestController(v)
	for _, username := range []string{strings.ToUpper(id), " " + id, id + " "} {
		body := `{"username":"` + username + `","password":"device-key-123","clientid":"smartpot-` + id + `"}`
		if got := decode(t, doAuthn(c, testToken, body)); got.Result != "deny" {
			t.Fatalf("username %q must be rejected exactly, got %q", username, got.Result)
		}
	}
}

func TestAuthnRejectsClientIDFromAnotherCrop(t *testing.T) {
	const id = "6718f0a1b2c3d4e5f6a7b8c9"
	const other = "6718f0a1b2c3d4e5f6a7b8d0"
	v := fakeVerifier{crop: cropWithID(t, id), key: "device-key-123", hasKey: true}
	c := newTestController(v)
	for _, clientID := range []string{"smartpot-" + other, "smartpot-sim-" + other, "smartpot-", id} {
		body := `{"username":"` + id + `","password":"device-key-123","clientid":"` + clientID + `"}`
		if got := decode(t, doAuthn(c, testToken, body)); got.Result != "deny" {
			t.Fatalf("clientid %q must be rejected, got %q", clientID, got.Result)
		}
	}
}
