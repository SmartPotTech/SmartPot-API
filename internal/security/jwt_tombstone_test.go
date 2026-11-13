package security

import (
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
)

func TestForgetUserKeepsExistingTokensRevoked(t *testing.T) {
	svc := NewTokenService("0123456789abcdef0123456789abcdef", time.Hour)
	svc.UseSessionStore(cache.NewStore(nil))

	issued, err := svc.Issue("user-deleted", "ana@smartpot.app", RoleUser)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := svc.Parse(issued.Value); err != nil {
		t.Fatalf("newly issued token should parse before account deletion: %v", err)
	}

	// Simulate DeleteAccount sequence: revokers bump version, then finalizers call ForgetUser.
	svc.BumpUserVersion("user-deleted")
	svc.ForgetUser("user-deleted")

	if _, err := svc.Parse(issued.Value); err == nil {
		t.Fatal("token issued before account deletion must remain invalid after ForgetUser")
	}
}
