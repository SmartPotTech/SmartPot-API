package ai

import (
	"context"
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	smartpotv1 "github.com/SmartPotTech/SmartPot-API-Go/internal/proto/v1"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"google.golang.org/grpc/test/bufconn"
)

const testAIToken = "test-token-for-smartpot-ai-service"

// fakeAI responde por gRPC lo que recibe, para comprobar que el JSON y el token llegan intactos.
type fakeAI struct {
	smartpotv1.UnimplementedSmartPotAIServiceServer
	received map[string]any
	fail     bool
}

func (f *fakeAI) EvaluateInsight(ctx context.Context, in *smartpotv1.JsonBody) (*smartpotv1.JsonBody, error) {
	md, _ := metadata.FromIncomingContext(ctx)
	if got := md.Get("authorization"); len(got) != 1 || got[0] != "Bearer "+testAIToken {
		return nil, status.Error(codes.Unauthenticated, "token")
	}
	if f.fail {
		return nil, status.Error(codes.Unavailable, "unavailable")
	}
	f.received = map[string]any{}
	_ = json.Unmarshal(in.GetJson(), &f.received)
	return &smartpotv1.JsonBody{Json: []byte(`{"cropType":"LETTUCE","summary":"via gRPC","health":{"index":71.5}}`)}, nil
}

func newTestClient(t *testing.T, ai *fakeAI, rest http.HandlerFunc) *Client {
	t.Helper()
	srv := httptest.NewServer(rest)
	t.Cleanup(srv.Close)
	c := NewClient(config.AI{Enabled: true, BaseURL: srv.URL, Token: testAIToken, Timeout: 2 * time.Second},
		cache.NewStore(nil), time.Now)

	listener := bufconn.Listen(1 << 20)
	server := grpc.NewServer()
	smartpotv1.RegisterSmartPotAIServiceServer(server, ai)
	go func() { _ = server.Serve(listener) }()
	t.Cleanup(server.Stop)
	conn, err := grpc.NewClient("passthrough:///bufnet",
		grpc.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) { return listener.DialContext(ctx) }),
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = conn.Close() })
	c.grpcConn = conn
	c.grpcClient = smartpotv1.NewSmartPotAIServiceClient(conn)
	return c
}

func TestInsightsTravelOverGRPCWithoutInventingZeros(t *testing.T) {
	ai := &fakeAI{}
	c := newTestClient(t, ai, func(w http.ResponseWriter, _ *http.Request) {
		t.Error("when gRPC is available REST should not be used")
		w.WriteHeader(http.StatusInternalServerError)
	})
	zero := 0.0
	resp, err := c.Insights(context.Background(), InsightRequest{CropType: "LETTUCE",
		Measures: readings.Measures{Temperature: &zero}})
	if err != nil || resp.Summary != "via gRPC" || resp.Health == nil || resp.Health.Index != 71.5 {
		t.Fatalf("unexpected response: %+v %v", resp, err)
	}
	measures := ai.received["measures"].(map[string]any)
	if v, ok := measures["temperature"]; !ok || v != 0.0 {
		t.Fatalf("0 °C should arrive as 0: %v", measures)
	}
	if _, ok := measures["ph"]; ok {
		t.Fatal("missing sensor should not arrive as 0")
	}
	if _, ok := ai.received["localHour"]; ok {
		t.Fatal("missing hour should not arrive as midnight")
	}
}

func TestInsightsFallBackToRESTWhenGRPCFails(t *testing.T) {
	ai := &fakeAI{fail: true}
	c := newTestClient(t, ai, func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer "+testAIToken || !strings.HasSuffix(r.URL.Path, "/v1/insights") {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		_, _ = w.Write([]byte(`{"summary":"via REST"}`))
	})
	resp, err := c.Insights(context.Background(), InsightRequest{CropType: "LETTUCE"})
	if err != nil || resp.Summary != "via REST" {
		t.Fatalf("should respond via REST: %+v %v", resp, err)
	}
}

func TestGRPCTargetPrefersExplicitConfiguration(t *testing.T) {
	if got := grpcTarget(config.AI{GRPCSocket: "/tmp/ai.sock", GRPCAddr: "ai:50051"}); got != "unix:///tmp/ai.sock" {
		t.Fatalf("target %q", got)
	}
	if got := grpcTarget(config.AI{GRPCAddr: "ai:50051"}); got != "ai:50051" {
		t.Fatalf("target %q", got)
	}
	if got := grpcTarget(config.AI{}); got != "" && got != "unix://"+defaultSocket {
		t.Fatal("without configuration it can only use the shared socket")
	}
}
