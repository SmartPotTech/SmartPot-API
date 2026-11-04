package ai

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	smartpotv1 "github.com/SmartPotTech/SmartPot-API-Go/internal/proto/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

const (
	unavailableMsg = "The AI assistant is not available at this moment"
	disabledMsg    = "The AI assistant is disabled on this server"
	profilesTTL    = time.Hour
	learningTTL    = time.Minute
	healthTTL      = 30 * time.Second
)

// Client es el cliente HTTP y gRPC del servicio interno de IA con Circuit Breaker, Retry exponencial y Singleflight.
type Client struct {
	enabled bool
	baseURL string
	token   string
	timeout time.Duration
	http    *http.Client
	cache   cache.Cache
	clock   func() time.Time

	cb *CircuitBreaker
	sf *Singleflight

	grpcConn     *grpc.ClientConn
	grpcClient   smartpotv1.SmartPotAIServiceClient
	lastFallback atomic.Int64

	mu              sync.Mutex
	healthCheckedAt time.Time
	healthy         bool
}

// defaultSocket es donde la IA abre su socket gRPC cuando comparten el volumen /var/run/smartpot.
const defaultSocket = "/var/run/smartpot/ai.sock"

// NewClient crea el cliente del servicio de IA. Usa gRPC (HTTP/2) por socket UNIX o TCP interno cuando está
// configurado y, ante cualquier fallo, REST por HTTP/1.1, que pasa por cualquier proxy.
func NewClient(cfg config.AI, store cache.Cache, clock func() time.Time) *Client {
	c := &Client{
		enabled: cfg.Enabled,
		baseURL: strings.TrimRight(cfg.BaseURL, "/"),
		token:   cfg.Token,
		timeout: cfg.Timeout,
		http:    &http.Client{Timeout: cfg.Timeout},
		cache:   store,
		clock:   clock,
		cb:      NewCircuitBreaker(5, 10*time.Second, clock),
		sf:      NewSingleflight(),
	}
	if !cfg.Enabled {
		return c
	}
	target := grpcTarget(cfg)
	if target == "" {
		return c
	}
	conn, err := grpc.NewClient(target, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		slog.Warn("could not initialize gRPC client for AI; using REST", "target", target, "err", err)
		return c
	}
	c.grpcConn = conn
	c.grpcClient = smartpotv1.NewSmartPotAIServiceClient(conn)
	slog.Info("gRPC client for AI initialized", "target", target)
	return c
}

// grpcTarget elige el destino gRPC: socket configurado, dirección TCP configurada o el socket compartido si existe.
func grpcTarget(cfg config.AI) string {
	switch {
	case cfg.GRPCSocket != "":
		return "unix://" + cfg.GRPCSocket
	case cfg.GRPCAddr != "":
		return cfg.GRPCAddr
	}
	if _, err := os.Stat(defaultSocket); err == nil {
		return "unix://" + defaultSocket
	}
	return ""
}

// Close libera las conexiones gRPC activas.
func (c *Client) Close() error {
	if c.grpcConn != nil {
		return c.grpcConn.Close()
	}
	return nil
}

// IsEnabled indica si el asistente está habilitado.
func (c *Client) IsEnabled() bool { return c.enabled }

// CircuitState devuelve el estado actual del Circuit Breaker (CLOSED, HALF-OPEN, OPEN).
func (c *Client) CircuitState() CircuitState {
	if c.cb == nil {
		return StateClosed
	}
	return c.cb.State()
}

// Insights evalúa un cultivo: gRPC si está disponible y, si falla, REST con el mismo JSON.
func (c *Client) Insights(ctx context.Context, req InsightRequest) (*InsightResponse, error) {
	if err := c.requireEnabled(); err != nil {
		return nil, err
	}
	var resp InsightResponse
	if c.callGRPC(ctx, c.evaluateInsight, req, &resp) {
		return &resp, nil
	}
	if err := c.do(ctx, http.MethodPost, "/v1/insights", req, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

type grpcCall func(ctx context.Context, in *smartpotv1.JsonBody, opts ...grpc.CallOption) (*smartpotv1.JsonBody, error)

func (c *Client) evaluateInsight(ctx context.Context, in *smartpotv1.JsonBody, opts ...grpc.CallOption) (*smartpotv1.JsonBody, error) {
	return c.grpcClient.EvaluateInsight(ctx, in, opts...)
}

func (c *Client) analyzeFleet(ctx context.Context, in *smartpotv1.JsonBody, opts ...grpc.CallOption) (*smartpotv1.JsonBody, error) {
	return c.grpcClient.AnalyzeFleet(ctx, in, opts...)
}

// callGRPC envía el cuerpo JSON por gRPC con el token en los metadatos. Devuelve false si no hay gRPC o si la
// llamada falla, para que el llamador use REST.
func (c *Client) callGRPC(ctx context.Context, call grpcCall, req, out any) bool {
	if c.grpcClient == nil {
		return false
	}
	body, err := json.Marshal(req)
	if err != nil {
		return false
	}
	callCtx, cancel := context.WithTimeout(metadata.AppendToOutgoingContext(ctx, "authorization", "Bearer "+c.token), c.timeout)
	defer cancel()
	res, err := call(callCtx, &smartpotv1.JsonBody{Json: body})
	if err == nil {
		err = json.Unmarshal(res.GetJson(), out)
	}
	if err != nil {
		c.warnFallback(err)
		return false
	}
	return true
}

// warnFallback avisa como mucho una vez por minuto que gRPC falló y se usa REST.
func (c *Client) warnFallback(err error) {
	now := c.clock().UnixNano()
	last := c.lastFallback.Load()
	if now-last < int64(time.Minute) || !c.lastFallback.CompareAndSwap(last, now) {
		return
	}
	slog.Warn("gRPC client for AI did not respond; using REST", "code", status.Code(err).String(), "err", err)
}

// Fleet analiza todos los cultivos de una cuenta.
func (c *Client) Fleet(ctx context.Context, req FleetRequest) (*FleetResponse, error) {
	if err := c.requireEnabled(); err != nil {
		return nil, err
	}
	var resp FleetResponse
	if c.callGRPC(ctx, c.analyzeFleet, req, &resp) {
		return &resp, nil
	}
	if err := c.do(ctx, http.MethodPost, "/v1/fleet", req, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// Learn envía un lote de lecturas reales para el aprendizaje continuo.
func (c *Client) Learn(ctx context.Context, batch LearningBatch) error {
	if err := c.requireEnabled(); err != nil {
		return err
	}
	return c.do(ctx, http.MethodPost, "/v1/learning/readings", batch, nil)
}

// Forget borra del aprendizaje las lecturas de un cultivo eliminado (best-effort).
func (c *Client) Forget(ctx context.Context, cropID string) {
	if !c.enabled {
		return
	}
	if err := c.do(ctx, http.MethodDelete, "/v1/learning/crops/"+cropID, nil, nil); err != nil {
		slog.Warn("could not forget crop readings in AI service", "cropId", cropID, "err", err)
	}
}

// LearningStatusJSON devuelve lo aprendido (agregado por especie), cacheado 1 min y protegido contra Cache Stampede.
func (c *Client) LearningStatusJSON(ctx context.Context) (string, error) {
	if err := c.requireEnabled(); err != nil {
		return "", err
	}
	return c.cachedGet(ctx, "ai:learning-status", "/v1/learning/status", learningTTL)
}

// CropProfilesJSON devuelve los rangos óptimos por especie, cacheados 1 h y protegidos contra Cache Stampede.
func (c *Client) CropProfilesJSON(ctx context.Context) (string, error) {
	if err := c.requireEnabled(); err != nil {
		return "", err
	}
	return c.cachedGet(ctx, "ai:crop-profiles", "/v1/crop-profiles", profilesTTL)
}

// IsHealthy consulta /health del servicio de IA, cacheando el resultado 30 s.
func (c *Client) IsHealthy() bool {
	if !c.enabled {
		return false
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	now := c.clock()
	if now.Before(c.healthCheckedAt.Add(healthTTL)) {
		return c.healthy
	}
	ctx, cancel := context.WithTimeout(context.Background(), c.http.Timeout)
	defer cancel()
	c.healthy = c.do(ctx, http.MethodGet, "/health", nil, nil) == nil
	c.healthCheckedAt = now
	return c.healthy
}

func (c *Client) cachedGet(ctx context.Context, key, path string, ttl time.Duration) (string, error) {
	if cached, ok := c.cache.Get(key); ok {
		return cached, nil
	}

	// Singleflight: si hay múltiples solicitudes concurrentes, solo una hace la petición HTTP
	val, err := c.sf.Do(key, func() (any, error) {
		if cached, ok := c.cache.Get(key); ok {
			return cached, nil
		}
		body, err := c.getRaw(ctx, path)
		if err != nil {
			return "", err
		}
		c.cache.Put(key, body, ttl)
		return body, nil
	})
	if err != nil {
		return "", err
	}
	return val.(string), nil
}

func (c *Client) getRaw(ctx context.Context, path string) (string, error) {
	var body string
	err := c.do(ctx, http.MethodGet, path, nil, &body)
	return body, err
}

func (c *Client) do(ctx context.Context, method, path string, body, out any) error {
	isIdempotent := method == http.MethodGet

	return c.cb.Execute(func() error {
		return RetryWithBackoff(ctx, 3, 50*time.Millisecond, func(err error) bool {
			return isIdempotent && isTransientError(err)
		}, func() error {
			return c.doRaw(ctx, method, path, body, out)
		})
	})
}

func (c *Client) doRaw(ctx context.Context, method, path string, body, out any) error {
	var reader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return apperr.Unavailable(unavailableMsg)
		}
		reader = bytes.NewReader(data)
	}
	req, err := http.NewRequestWithContext(ctx, method, c.baseURL+path, reader)
	if err != nil {
		return apperr.Unavailable(unavailableMsg)
	}
	req.Header.Set("Authorization", "Bearer "+c.token)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	resp, err := c.http.Do(req)
	if err != nil {
		slog.Warn("AI service request failed", "path", path, "err", err)
		return apperr.Unavailable(unavailableMsg)
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return apperr.Unavailable(unavailableMsg)
	}
	if out != nil {
		if strPtr, ok := out.(*string); ok {
			rawBytes, _ := io.ReadAll(resp.Body)
			*strPtr = string(rawBytes)
			return nil
		}
		if err := json.NewDecoder(resp.Body).Decode(out); err != nil {
			return apperr.Unavailable(unavailableMsg)
		}
	} else {
		_, _ = io.Copy(io.Discard, resp.Body)
	}
	return nil
}

func (c *Client) requireEnabled() error {
	if !c.enabled {
		return apperr.Unavailable(disabledMsg)
	}
	return nil
}
