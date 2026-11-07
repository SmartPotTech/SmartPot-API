package users

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

const (
	testSecret   = "0123456789abcdef0123456789abcdef"
	testPassword = "Password2026"
)

// fakeRepository guarda las cuentas en memoria y registra las escrituras.
type fakeRepository struct {
	byID      map[string]*User
	byEmail   map[string]*User
	updates   int
	deletes   int
	updateErr error
	deleteErr error
}

func newFakeRepository() *fakeRepository {
	return &fakeRepository{byID: map[string]*User{}, byEmail: map[string]*User{}}
}

func (r *fakeRepository) FindByEmail(_ context.Context, email string) (*User, error) {
	return r.byEmail[email], nil
}

func (r *fakeRepository) ExistsByEmail(_ context.Context, email string) (bool, error) {
	_, ok := r.byEmail[email]
	return ok, nil
}

func (r *fakeRepository) FindByID(_ context.Context, id string) (*User, error) {
	return r.byID[id], nil
}

func (r *fakeRepository) Insert(_ context.Context, u *User) error {
	if u.ID.IsZero() {
		u.ID = primitive.NewObjectID()
	}
	r.byID[u.HexID()] = u
	r.byEmail[u.Email] = u
	return nil
}

func (r *fakeRepository) Update(_ context.Context, u *User) error {
	if r.updateErr != nil {
		return r.updateErr
	}
	r.updates++
	r.byID[u.HexID()] = u
	r.byEmail[u.Email] = u
	return nil
}

func (r *fakeRepository) Delete(_ context.Context, id primitive.ObjectID) error {
	if r.deleteErr != nil {
		return r.deleteErr
	}
	if u := r.byID[id.Hex()]; u != nil {
		delete(r.byID, id.Hex())
		delete(r.byEmail, u.Email)
	}
	r.deletes++
	return nil
}

type usersFixture struct {
	repo    *fakeRepository
	service *Service
	user    *User
	now     time.Time
	jwt     *security.TokenService
}

func newUsersFixture(t *testing.T) *usersFixture {
	t.Helper()
	now := time.Date(2026, 11, 6, 12, 0, 0, 0, time.UTC)
	hash, err := security.HashPassword(testPassword)
	if err != nil {
		t.Fatalf("hash test password: %v", err)
	}
	repo := newFakeRepository()
	u := &User{
		ID:           primitive.NewObjectID(),
		Name:         "Ana",
		LastName:     "Demo",
		Email:        "ana@smartpot.app",
		PasswordHash: hash,
		Role:         RoleUser,
		CreatedAt:    now.Add(-time.Hour),
		UpdatedAt:    now.Add(-time.Hour),
	}
	if err := repo.Insert(context.Background(), u); err != nil {
		t.Fatalf("seed user: %v", err)
	}
	return &usersFixture{
		repo:    repo,
		service: NewService(repo, func() time.Time { return now }),
		user:    u,
		now:     now,
		jwt:     security.NewTokenService(testSecret, time.Hour),
	}
}

func (f *usersFixture) engine() *echo.Echo {
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	e := httpserver.New(logger)
	authed := e.Group("/api/v1")
	authed.Use(f.jwt.Guard())
	NewHandler(f.service).Register(authed)
	return e
}

func (f *usersFixture) issueToken(t *testing.T) string {
	t.Helper()
	issued, err := f.jwt.Issue(f.user.HexID(), f.user.Email, string(f.user.Role))
	if err != nil {
		t.Fatalf("issue token: %v", err)
	}
	return issued.Value
}

func statusOf(err error) int {
	var apiErr *apperr.Error
	if errors.As(err, &apiErr) {
		return apiErr.Status
	}
	return 0
}

func TestGetByID(t *testing.T) {
	f := newUsersFixture(t)

	got, err := f.service.GetByID(context.Background(), f.user.HexID())
	if err != nil {
		t.Fatalf("GetByID: %v", err)
	}
	if got.HexID() != f.user.HexID() || got.Email != f.user.Email {
		t.Errorf("unexpected user: %+v", got)
	}

	_, err = f.service.GetByID(context.Background(), primitive.NewObjectID().Hex())
	if got := statusOf(err); got != 401 {
		t.Errorf("missing user status = %d, want 401 (%v)", got, err)
	}
}

func TestUpdateProfileTrimsAndPersists(t *testing.T) {
	f := newUsersFixture(t)

	updated, err := f.service.UpdateProfile(context.Background(), f.user.HexID(), UpdateProfileRequest{
		Name:     "  Ana María ",
		LastName: "  López  ",
	})
	if err != nil {
		t.Fatalf("UpdateProfile: %v", err)
	}
	if updated.Name != "Ana María" || updated.LastName != "López" {
		t.Errorf("names must be trimmed: %+v", updated)
	}
	if !updated.UpdatedAt.Equal(f.now) {
		t.Errorf("UpdatedAt = %v, want %v", updated.UpdatedAt, f.now)
	}
	if f.repo.updates != 1 {
		t.Errorf("updates = %d, want 1", f.repo.updates)
	}
	persisted := f.repo.byID[f.user.HexID()]
	if persisted.Name != "Ana María" || persisted.LastName != "López" {
		t.Errorf("the change was not persisted: %+v", persisted)
	}

	_, err = f.service.UpdateProfile(context.Background(), primitive.NewObjectID().Hex(), UpdateProfileRequest{
		Name:     "Ana",
		LastName: "Demo",
	})
	if got := statusOf(err); got != 401 {
		t.Errorf("missing user status = %d, want 401 (%v)", got, err)
	}
	if f.repo.updates != 1 {
		t.Errorf("a missing user must not be updated")
	}
}

func TestChangePasswordRejectsWrongCurrentPassword(t *testing.T) {
	f := newUsersFixture(t)

	err := f.service.ChangePassword(context.Background(), f.user.HexID(), ChangePasswordRequest{
		CurrentPassword: "WrongPassword2026",
		NewPassword:     "NewPassword2026",
	})
	if got := statusOf(err); got != 400 {
		t.Fatalf("wrong current password status = %d, want 400 (%v)", got, err)
	}
	if err.Error() != "Current password is incorrect" {
		t.Errorf("message = %q", err.Error())
	}
	if f.repo.updates != 0 || !security.PasswordMatches(testPassword, f.user.PasswordHash) {
		t.Error("the password must not change")
	}
}

func TestChangePasswordRejectsWeakNewPassword(t *testing.T) {
	f := newUsersFixture(t)

	for name, password := range map[string]string{
		"too short":      "weak",
		"without number": "WeakPassword",
		"without upper":  "weakpassword2026",
	} {
		t.Run(name, func(t *testing.T) {
			err := f.service.ChangePassword(context.Background(), f.user.HexID(), ChangePasswordRequest{
				CurrentPassword: testPassword,
				NewPassword:     password,
			})
			if got := statusOf(err); got != 400 {
				t.Errorf("status = %d, want 400 (%v)", got, err)
			}
			if f.repo.updates != 0 {
				t.Errorf("a rejected password must not be persisted")
			}
		})
	}
}

func TestChangePasswordRejectsTheCurrentPassword(t *testing.T) {
	f := newUsersFixture(t)

	err := f.service.ChangePassword(context.Background(), f.user.HexID(), ChangePasswordRequest{
		CurrentPassword: testPassword,
		NewPassword:     testPassword,
	})
	if got := statusOf(err); got != 400 {
		t.Fatalf("same password status = %d, want 400 (%v)", got, err)
	}
	if err.Error() != "New password must be different from the current password" {
		t.Errorf("message = %q", err.Error())
	}
	if f.repo.updates != 0 {
		t.Error("the hash must not be rewritten with the same password")
	}
}

func TestChangePasswordRevokesEverySession(t *testing.T) {
	f := newUsersFixture(t)
	var revoked []string
	f.service.AddSessionRevoker(func(_ context.Context, userID string) error {
		revoked = append(revoked, "first:"+userID)
		return nil
	})
	f.service.AddSessionRevoker(func(_ context.Context, userID string) error {
		revoked = append(revoked, "second:"+userID)
		return nil
	})

	err := f.service.ChangePassword(context.Background(), f.user.HexID(), ChangePasswordRequest{
		CurrentPassword: testPassword,
		NewPassword:     "NewPassword2026",
	})
	if err != nil {
		t.Fatalf("ChangePassword: %v", err)
	}

	if len(revoked) != 2 {
		t.Fatalf("revokers called = %d, want 2", len(revoked))
	}
	want := []string{"first:" + f.user.HexID(), "second:" + f.user.HexID()}
	for i := range want {
		if revoked[i] != want[i] {
			t.Errorf("revoker %d = %q, want %q", i, revoked[i], want[i])
		}
	}
	if !security.PasswordMatches("NewPassword2026", f.repo.byID[f.user.HexID()].PasswordHash) {
		t.Error("the new password must work")
	}
	if security.PasswordMatches(testPassword, f.repo.byID[f.user.HexID()].PasswordHash) {
		t.Error("the old password must stop working")
	}
	if f.repo.updates != 1 {
		t.Errorf("updates = %d, want 1", f.repo.updates)
	}
}

func TestChangePasswordPropagatesRevokerError(t *testing.T) {
	f := newUsersFixture(t)
	sentinel := errors.New("revocation failed")
	var calls []string
	f.service.AddSessionRevoker(func(context.Context, string) error {
		calls = append(calls, "first")
		return nil
	})
	f.service.AddSessionRevoker(func(context.Context, string) error {
		calls = append(calls, "second")
		return sentinel
	})
	f.service.AddSessionRevoker(func(context.Context, string) error {
		calls = append(calls, "third")
		return nil
	})

	err := f.service.ChangePassword(context.Background(), f.user.HexID(), ChangePasswordRequest{
		CurrentPassword: testPassword,
		NewPassword:     "NewPassword2026",
	})
	if !errors.Is(err, sentinel) {
		t.Fatalf("error = %v, want the revoker error", err)
	}
	if len(calls) != 2 || calls[0] != "first" || calls[1] != "second" {
		t.Errorf("revocation stopped at the wrong hook: %v", calls)
	}
}

func TestDeleteAccountRunsEveryHookInOrder(t *testing.T) {
	f := newUsersFixture(t)
	var order []string
	f.service.AddAccountDeleter(func(_ context.Context, userID string) error {
		order = append(order, "delete:crops:"+userID)
		return nil
	})
	f.service.AddAccountDeleter(func(_ context.Context, userID string) error {
		order = append(order, "delete:notifications:"+userID)
		return nil
	})
	f.service.AddSessionRevoker(func(context.Context, string) error {
		order = append(order, "revoke:sessions")
		return nil
	})
	f.service.AddSessionRevoker(func(context.Context, string) error {
		order = append(order, "revoke:connections")
		return nil
	})
	f.service.AddAccountFinalizer(func(_ context.Context, userID string) error {
		order = append(order, "finalize:cache:"+userID)
		return nil
	})

	if err := f.service.DeleteAccount(context.Background(), f.user.HexID()); err != nil {
		t.Fatalf("DeleteAccount: %v", err)
	}

	want := []string{
		"delete:crops:" + f.user.HexID(),
		"delete:notifications:" + f.user.HexID(),
		"revoke:sessions",
		"revoke:connections",
		"finalize:cache:" + f.user.HexID(),
	}
	if len(order) != len(want) {
		t.Fatalf("hook calls = %v, want %v", order, want)
	}
	for i := range want {
		if order[i] != want[i] {
			t.Errorf("hook %d = %q, want %q", i, order[i], want[i])
		}
	}
	if f.repo.deletes != 1 {
		t.Errorf("repository deletes = %d, want 1", f.repo.deletes)
	}
	if _, ok := f.repo.byID[f.user.HexID()]; ok {
		t.Error("the user must be removed last")
	}
}

func TestDeleteAccountStopsAtTheFirstError(t *testing.T) {
	f := newUsersFixture(t)
	sentinel := errors.New("cascade failed")
	var calls []string
	f.service.AddAccountDeleter(func(context.Context, string) error {
		calls = append(calls, "first")
		return nil
	})
	f.service.AddAccountDeleter(func(context.Context, string) error {
		calls = append(calls, "second")
		return sentinel
	})
	f.service.AddAccountDeleter(func(context.Context, string) error {
		calls = append(calls, "third")
		return nil
	})
	f.service.AddSessionRevoker(func(context.Context, string) error {
		calls = append(calls, "revoke")
		return nil
	})

	err := f.service.DeleteAccount(context.Background(), f.user.HexID())
	if !errors.Is(err, sentinel) {
		t.Fatalf("error = %v, want the deleter error", err)
	}
	if len(calls) != 2 || calls[0] != "first" || calls[1] != "second" {
		t.Errorf("hooks = %v, want only the first two", calls)
	}
	if f.repo.deletes != 0 {
		t.Error("the account must not be deleted when a cascade fails")
	}
	if _, ok := f.repo.byID[f.user.HexID()]; !ok {
		t.Error("the user must remain after a failed cascade")
	}
}

func TestDeleteAccountRejectsMissingUser(t *testing.T) {
	f := newUsersFixture(t)
	called := false
	f.service.AddSessionRevoker(func(context.Context, string) error {
		called = true
		return nil
	})

	err := f.service.DeleteAccount(context.Background(), primitive.NewObjectID().Hex())
	if got := statusOf(err); got != 401 {
		t.Errorf("missing user status = %d, want 401 (%v)", got, err)
	}
	if called || f.repo.deletes != 0 {
		t.Error("no hook or delete must run for a missing user")
	}
}
