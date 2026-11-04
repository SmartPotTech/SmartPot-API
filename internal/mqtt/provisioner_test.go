package mqtt

import (
	"context"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
)

type fakeBroker struct {
	mu       sync.Mutex
	listed   []string // usuarios consultados
	deleted  []string // clientids desconectados
	authOK   bool
	clientID string
}

func newFakeBroker(clientID string) (*fakeBroker, *httptest.Server) {
	b := &fakeBroker{clientID: clientID}
	mux := http.NewServeMux()
	mux.HandleFunc("/api/v5/clients", func(w http.ResponseWriter, r *http.Request) {
		if u, p, ok := r.BasicAuth(); ok && u == "key" && p == "secret" {
			b.authOK = true
		}
		b.mu.Lock()
		b.listed = append(b.listed, r.URL.Query().Get("username"))
		b.mu.Unlock()
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"data":[{"clientid":"` + b.clientID + `"}]}`))
	})
	mux.HandleFunc("/api/v5/clients/", func(w http.ResponseWriter, r *http.Request) {
		id := strings.TrimPrefix(r.URL.Path, "/api/v5/clients/")
		b.mu.Lock()
		b.deleted = append(b.deleted, id)
		b.mu.Unlock()
		w.WriteHeader(http.StatusNoContent)
	})
	return b, httptest.NewServer(mux)
}

func newProvisioner(url string) *Provisioner {
	return NewProvisioner(config.MQTT{APIURL: url, APIKey: "key", APISecret: "secret"},
		slog.New(slog.DiscardHandler))
}

func TestProvision_WithoutRotateDoesNothing(t *testing.T) {
	b, srv := newFakeBroker("smartpot-abc")
	defer srv.Close()
	if err := newProvisioner(srv.URL).Provision(context.Background(), "abc", "key", false); err != nil {
		t.Fatalf("Provision returned error: %v", err)
	}
	if len(b.listed) != 0 || len(b.deleted) != 0 {
		t.Fatalf("create without rotate should not touch broker; listed=%v deleted=%v", b.listed, b.deleted)
	}
}

func TestProvision_RotateKicksSessions(t *testing.T) {
	cropID := "6718f0a1b2c3d4e5f6a7b8c9"
	b, srv := newFakeBroker("smartpot-" + cropID)
	defer srv.Close()
	if err := newProvisioner(srv.URL).Provision(context.Background(), cropID, "key", true); err != nil {
		t.Fatalf("Provision returned error: %v", err)
	}
	if !b.authOK {
		t.Error("API must authenticate with Basic against broker")
	}
	if len(b.listed) != 1 || b.listed[0] != cropID {
		t.Errorf("expected listing for crop %s, obtuve %v", cropID, b.listed)
	}
	if len(b.deleted) != 1 || b.deleted[0] != "smartpot-"+cropID {
		t.Errorf("expected crop session disconnect, got %v", b.deleted)
	}
}

func TestDeprovision_KicksSessions(t *testing.T) {
	cropID := "6718f0a1b2c3d4e5f6a7b8c9"
	b, srv := newFakeBroker("smartpot-sim-" + cropID)
	defer srv.Close()
	if err := newProvisioner(srv.URL).Deprovision(context.Background(), cropID); err != nil {
		t.Fatalf("Deprovision devolvió error: %v", err)
	}
	if len(b.deleted) != 1 || b.deleted[0] != "smartpot-sim-"+cropID {
		t.Errorf("expected simulated session disconnect, got %v", b.deleted)
	}
}

func TestProvision_WithoutRESTConfigIsNoop(t *testing.T) {
	p := NewProvisioner(config.MQTT{}, slog.New(slog.DiscardHandler))
	if err := p.Deprovision(context.Background(), "abc"); err != nil {
		t.Fatalf("without configured REST API should not fail: %v", err)
	}
}
