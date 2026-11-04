package security

import (
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const testSecret = "test-key-of-at-least-32-characters"

func craft(t *testing.T, method jwt.SigningMethod, secret string, claims jwt.MapClaims) string {
	t.Helper()
	token := jwt.NewWithClaims(method, claims)
	var key any = []byte(secret)
	if method == jwt.SigningMethodNone {
		key = jwt.UnsafeAllowNoneSignatureType
	}
	signed, err := token.SignedString(key)
	if err != nil {
		t.Fatalf("craft: %v", err)
	}
	return signed
}

func TestIssueAndParse(t *testing.T) {
	svc := NewTokenService(testSecret, 7*24*time.Hour)
	issued, err := svc.Issue("user-123", "a@b.com", "USER")
	if err != nil {
		t.Fatalf("Issue: %v", err)
	}
	claims, err := svc.Parse(issued.Value)
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	if claims.UserID != "user-123" || claims.Email != "a@b.com" || claims.Role != "USER" {
		t.Errorf("claims inesperados: %+v", claims)
	}
	if !issued.ExpiresAt.After(time.Now()) {
		t.Error("ExpiresAt debía estar en el futuro")
	}
}

func TestParseRejectsInvalid(t *testing.T) {
	svc := NewTokenService(testSecret, time.Hour)
	now := time.Now()

	cases := map[string]string{
		"firma manipulada": func() string {
			tok, _ := svc.Issue("u", "e", "USER")
			return tok.Value[:len(tok.Value)-2] + "xx"
		}(),
		"wrong secret": craft(t, jwt.SigningMethodHS256, "another-secret-of-at-least-32-chars-xx", jwt.MapClaims{
			"iss": JWTIssuer, "sub": "u", "exp": now.Add(time.Hour).Unix(),
		}),
		"wrong issuer": craft(t, jwt.SigningMethodHS256, testSecret, jwt.MapClaims{
			"iss": "otro", "sub": "u", "exp": now.Add(time.Hour).Unix(),
		}),
		"expirado": craft(t, jwt.SigningMethodHS256, testSecret, jwt.MapClaims{
			"iss": JWTIssuer, "sub": "u", "exp": now.Add(-time.Hour).Unix(),
		}),
		"sin expiración": craft(t, jwt.SigningMethodHS256, testSecret, jwt.MapClaims{
			"iss": JWTIssuer, "sub": "u",
		}),
		"algoritmo none": craft(t, jwt.SigningMethodNone, testSecret, jwt.MapClaims{
			"iss": JWTIssuer, "sub": "u", "exp": now.Add(time.Hour).Unix(),
		}),
	}
	for name, token := range cases {
		if _, err := svc.Parse(token); err == nil {
			t.Errorf("Parse debía rechazar: %s", name)
		}
	}
}
