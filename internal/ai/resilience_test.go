package ai

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

func TestCircuitBreakerOpensExactlyAtThreshold(t *testing.T) {
	now := time.Unix(1_700_000_000, 0)
	cb := NewCircuitBreaker(5, 10*time.Second, func() time.Time { return now })
	failure := errors.New("upstream down")

	for i := 1; i <= 4; i++ {
		_ = cb.Execute(func() error { return failure })
		if state := cb.State(); state != StateClosed {
			t.Fatalf("after %d failures the circuit must stay CLOSED, got %s", i, state)
		}
	}
	_ = cb.Execute(func() error { return failure })
	if state := cb.State(); state != StateOpen {
		t.Fatalf("the fifth consecutive failure must open the circuit, got %s", state)
	}

	called := false
	if err := cb.Execute(func() error { called = true; return nil }); err == nil || called {
		t.Fatal("an OPEN circuit must fail fast without calling the operation")
	}

	now = now.Add(11 * time.Second)
	if state := cb.State(); state != StateHalfOpen {
		t.Fatalf("after the cooldown the circuit must be HALF-OPEN, got %s", state)
	}
	if err := cb.Execute(func() error { return nil }); err != nil {
		t.Fatalf("a successful probe must close the circuit: %v", err)
	}
	if state := cb.State(); state != StateClosed {
		t.Fatalf("circuit should be CLOSED after a successful probe, got %s", state)
	}
}

func TestCircuitBreakerIgnoresClientErrors(t *testing.T) {
	cb := NewCircuitBreaker(3, time.Second, time.Now)
	for i := 0; i < 10; i++ {
		_ = cb.Execute(func() error { return apperr.BadRequest("invalid input") })
	}
	if state := cb.State(); state != StateClosed {
		t.Fatalf("4xx responses are not transport failures; circuit must stay CLOSED, got %s", state)
	}
}

func TestSingleflightRecoversFromPanic(t *testing.T) {
	sf := NewSingleflight()
	if _, err := sf.Do("key", func() (any, error) { panic("boom") }); err == nil {
		t.Fatal("a panic must be returned as an error")
	}

	done := make(chan struct{})
	go func() {
		defer close(done)
		value, err := sf.Do("key", func() (any, error) { return "ok", nil })
		if err != nil || value != "ok" {
			t.Errorf("the next call must run normally: value=%v err=%v", value, err)
		}
	}()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("singleflight entry stayed locked after a panic")
	}
}

func TestSingleflightDeduplicatesConcurrentCalls(t *testing.T) {
	sf := NewSingleflight()
	var calls atomic.Int32
	start := make(chan struct{})
	var wg sync.WaitGroup
	for i := 0; i < 20; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			value, err := sf.Do("same", func() (any, error) {
				calls.Add(1)
				time.Sleep(20 * time.Millisecond)
				return "shared", nil
			})
			if err != nil || value != "shared" {
				t.Errorf("unexpected singleflight result: %v %v", value, err)
			}
		}()
	}
	close(start)
	wg.Wait()
	if got := calls.Load(); got != 1 {
		t.Fatalf("concurrent callers must share one execution, got %d", got)
	}
}

func TestInsightFlightKeyVersionsWithCropAndReading(t *testing.T) {
	crop := &crops.Crop{ID: primitive.NewObjectID(), UpdatedAt: time.Unix(100, 0)}
	latest := readings.Reading{ID: primitive.NewObjectID(), MeasuredAt: time.Unix(200, 0)}
	key := insightCacheKey(crop, latest)
	if flight := insightFlightKey(key); flight != "flight:"+key {
		t.Fatalf("flight key must contain the versioned cache key, got %q", flight)
	}

	other := *crop
	other.UpdatedAt = crop.UpdatedAt.Add(time.Second)
	if insightCacheKey(&other, latest) == key {
		t.Fatal("a crop mutation must produce a new cache key")
	}
	otherReading := latest
	otherReading.ID = primitive.NewObjectID()
	if insightCacheKey(crop, otherReading) == key {
		t.Fatal("a new reading must produce a new cache key")
	}
}

func TestLearnRequeuesWhenTrainerStoredNothing(t *testing.T) {
	calls := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		w.Header().Set("Content-Type", "application/json")
		if calls == 1 {
			_, _ = w.Write([]byte(`{"received":3,"stored":0}`))
			return
		}
		_, _ = w.Write([]byte(`{"received":3,"stored":3}`))
	}))
	defer srv.Close()

	client := NewClient(config.AI{Enabled: true, BaseURL: srv.URL, Token: "token", Timeout: 2 * time.Second},
		cache.NewStore(nil), time.Now)
	batch := LearningBatch{Readings: []LearningReading{{CropID: primitive.NewObjectID().Hex(), CropType: "LETTUCE"}}}

	if err := client.Learn(context.Background(), batch); err == nil {
		t.Fatal("received > 0 with stored == 0 must be an error so the feed requeues the batch")
	}
	if err := client.Learn(context.Background(), batch); err != nil {
		t.Fatalf("stored readings should succeed: %v", err)
	}
}

func TestDoRawMapsUpstreamAuthErrorTo503(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer srv.Close()

	client := NewClient(config.AI{Enabled: true, BaseURL: srv.URL, Token: "bad-internal-token", Timeout: 2 * time.Second},
		cache.NewStore(nil), time.Now)
	_, err := client.LearningStatusJSON(context.Background())
	var apiErr *apperr.Error
	if !errors.As(err, &apiErr) || apiErr.Status != http.StatusServiceUnavailable {
		t.Fatalf("upstream 401 must map to 503 Unavailable, got %v", err)
	}
}

func TestCachedGetFallsBackToStaleAndIsolatesLeaderCancel(t *testing.T) {
	var failUpstream atomic.Bool
	var upstreamCalls atomic.Int32
	entered := make(chan struct{}, 1)
	release := make(chan struct{})

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		upstreamCalls.Add(1)
		if failUpstream.Load() {
			w.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		select {
		case entered <- struct{}{}:
		default:
		}
		<-release
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"samples":12}`))
	}))
	defer srv.Close()

	store := cache.NewStore(nil)
	client := NewClient(config.AI{Enabled: true, BaseURL: srv.URL, Token: "token", Timeout: 2 * time.Second},
		store, time.Now)

	// 1. Leader cancels while in flight; follower sharing the singleflight call must still succeed.
	leaderCtx, cancelLeader := context.WithCancel(context.Background())
	followerDone := make(chan error, 1)
	go func() {
		_, err := client.cachedGet(leaderCtx, "test:key", "/v1/learning/status", time.Millisecond)
		_ = err
	}()
	<-entered
	go func() {
		body, err := client.cachedGet(context.Background(), "test:key", "/v1/learning/status", time.Millisecond)
		if err == nil && body != `{"samples":12}` {
			err = errors.New("unexpected body: " + body)
		}
		followerDone <- err
	}()
	cancelLeader()
	close(release)
	if err := <-followerDone; err != nil {
		t.Fatalf("follower failed after leader canceled: %v", err)
	}

	// 2. When fresh key expires and upstream fails, stale cache is returned.
	store.Delete("test:key")
	failUpstream.Store(true)
	staleBody, err := client.cachedGet(context.Background(), "test:key", "/v1/learning/status", time.Millisecond)
	if err != nil || staleBody != `{"samples":12}` {
		t.Fatalf("expected stale fallback, got body=%q err=%v", staleBody, err)
	}
}
