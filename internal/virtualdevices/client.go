package virtualdevices

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
)

const (
	simUnavailable   = "The virtual crop simulator is not available at this moment"
	simDisabled      = "Virtual crops are not enabled on this server"
	placesFreshTTL   = 15 * time.Minute
	placesStaleTTL   = 24 * time.Hour
	placesMaxEntries = 2048
)

type cachedPlaces struct {
	places    []PlaceResponse
	fetchedAt time.Time
}

type placesFlight struct {
	done   chan struct{}
	places []PlaceResponse
	err    error
}

// SimulatorClient es el cliente de la API interna del simulador de dispositivos.
type SimulatorClient struct {
	available bool
	baseURL   string
	token     string
	http      *http.Client

	placesMu       sync.Mutex
	placesCache    map[string]cachedPlaces
	placesInflight map[string]*placesFlight
}

// NewSimulatorClient crea el cliente del simulador.
func NewSimulatorClient(cfg config.Simulator) *SimulatorClient {
	base := cfg.BaseURL
	if base == "" {
		base = "http://localhost:8081"
	}
	available := cfg.Enabled && cfg.BaseURL != "" && cfg.Token != ""
	return &SimulatorClient{
		available: available,
		baseURL:   strings.TrimRight(base, "/"),
		token:     cfg.Token,
		http:      &http.Client{Timeout: cfg.Timeout},
	}
}

// IsAvailable indica si los cultivos virtuales están habilitados.
func (c *SimulatorClient) IsAvailable() bool { return c.available }

// Put crea o actualiza la simulación de un cultivo.
func (c *SimulatorClient) Put(ctx context.Context, cropID string, req SimulatorPotRequest) (*SimulatorPot, error) {
	if err := c.requireAvailable(); err != nil {
		return nil, err
	}
	var pot SimulatorPot
	status, err := c.do(ctx, http.MethodPut, "/v1/pots/"+cropID, req, &pot)
	if err != nil {
		if status >= 400 && status < 500 {
			return nil, apperr.BadRequest("Simulator rejected configuration: " + http.StatusText(status))
		}
		slog.Warn("virtual crop simulation failed", "cropId", cropID, "err", err)
		return nil, apperr.Unavailable(simUnavailable)
	}
	return &pot, nil
}

// Get devuelve el estado en vivo; nil si el cultivo no existe o el simulador no responde.
func (c *SimulatorClient) Get(ctx context.Context, cropID string) (*SimulatorPot, bool) {
	if !c.available {
		return nil, false
	}
	var pot SimulatorPot
	if _, err := c.do(ctx, http.MethodGet, "/v1/pots/"+cropID, nil, &pot); err != nil {
		return nil, false
	}
	return &pot, true
}

// List devuelve todas las simulaciones del simulador.
func (c *SimulatorClient) List(ctx context.Context) ([]SimulatorPot, error) {
	if err := c.requireAvailable(); err != nil {
		return nil, err
	}
	var pots []SimulatorPot
	if _, err := c.do(ctx, http.MethodGet, "/v1/pots", nil, &pots); err != nil {
		return nil, apperr.Unavailable(simUnavailable)
	}
	return pots, nil
}

// Delete retira la simulación de un cultivo (ignora que no exista).
func (c *SimulatorClient) Delete(ctx context.Context, cropID string) {
	if !c.available {
		return
	}
	if status, err := c.do(ctx, http.MethodDelete, "/v1/pots/"+cropID, nil, nil); err != nil && status != http.StatusNotFound {
		slog.Warn("could not remove crop simulation", "cropId", cropID, "status", status)
	}
}

// Places busca lugares para el modo clima, con caché en memoria, fusión de consultas concurrentes y respaldo ante fallos transitorios.
func (c *SimulatorClient) Places(ctx context.Context, query string) ([]PlaceResponse, error) {
	if err := c.requireAvailable(); err != nil {
		return nil, err
	}
	trimmed := strings.TrimSpace(query)
	key := strings.ToLower(trimmed)

	c.placesMu.Lock()
	if c.placesCache != nil {
		if entry, ok := c.placesCache[key]; ok && time.Since(entry.fetchedAt) < placesFreshTTL {
			c.placesMu.Unlock()
			return clonePlaces(entry.places), nil
		}
	}
	if c.placesInflight != nil {
		if flight, ok := c.placesInflight[key]; ok {
			c.placesMu.Unlock()
			select {
			case <-flight.done:
			case <-ctx.Done():
				return nil, ctx.Err()
			}
			if flight.err != nil {
				return nil, flight.err
			}
			return clonePlaces(flight.places), nil
		}
	}
	if c.placesInflight == nil {
		c.placesInflight = make(map[string]*placesFlight)
	}
	flight := &placesFlight{done: make(chan struct{})}
	c.placesInflight[key] = flight
	c.placesMu.Unlock()

	opTimeout := 8 * time.Second
	if c.http != nil && c.http.Timeout > 0 {
		opTimeout = c.http.Timeout
	}
	opCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), opTimeout)
	var places []PlaceResponse
	path := "/v1/places?" + url.Values{"q": {trimmed}}.Encode()
	_, callErr := c.do(opCtx, http.MethodGet, path, nil, &places)
	cancel()

	c.placesMu.Lock()
	delete(c.placesInflight, key)
	if callErr == nil {
		if c.placesCache == nil {
			c.placesCache = make(map[string]cachedPlaces)
		}
		if len(c.placesCache) >= placesMaxEntries {
			c.placesCache = make(map[string]cachedPlaces)
		}
		c.placesCache[key] = cachedPlaces{
			places:    clonePlaces(places),
			fetchedAt: time.Now(),
		}
		flight.places = clonePlaces(places)
	} else {
		if entry, ok := c.placesCache[key]; ok && time.Since(entry.fetchedAt) < placesStaleTTL {
			slog.Warn("serving stale places cache after simulator error", "query", trimmed, "err", callErr)
			flight.places = clonePlaces(entry.places)
			callErr = nil
		} else {
			flight.err = apperr.Unavailable("Could not query location service")
		}
	}
	close(flight.done)
	c.placesMu.Unlock()

	if flight.err != nil {
		return nil, flight.err
	}
	return clonePlaces(flight.places), nil
}

// Weather devuelve el clima de un lugar; nil si no hay simulador o no pudo consultarlo.
func (c *SimulatorClient) Weather(ctx context.Context, latitude, longitude float64) (*Weather, bool) {
	if !c.available {
		return nil, false
	}
	params := url.Values{
		"latitude":  {strconv.FormatFloat(latitude, 'f', -1, 64)},
		"longitude": {strconv.FormatFloat(longitude, 'f', -1, 64)},
	}
	var weather Weather
	if _, err := c.do(ctx, http.MethodGet, "/v1/weather?"+params.Encode(), nil, &weather); err != nil {
		return nil, false
	}
	return &weather, true
}

// IsHealthy consulta /health del simulador.
func (c *SimulatorClient) IsHealthy() bool {
	if !c.available {
		return false
	}
	ctx, cancel := context.WithTimeout(context.Background(), c.http.Timeout)
	defer cancel()
	_, err := c.do(ctx, http.MethodGet, "/health", nil, nil)
	return err == nil
}

func (c *SimulatorClient) requireAvailable() error {
	if !c.available {
		return apperr.Unavailable(simDisabled)
	}
	return nil
}

func clonePlaces(in []PlaceResponse) []PlaceResponse {
	if len(in) == 0 {
		return []PlaceResponse{}
	}
	out := make([]PlaceResponse, len(in))
	copy(out, in)
	return out
}

// do ejecuta una petición HTTP; devuelve el código de estado y un error si no fue 2xx.
func (c *SimulatorClient) do(ctx context.Context, method, path string, body, out any) (int, error) {
	var reader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return 0, err
		}
		reader = bytes.NewReader(data)
	}
	req, err := http.NewRequestWithContext(ctx, method, c.baseURL+path, reader)
	if err != nil {
		return 0, err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return resp.StatusCode, &httpError{status: resp.StatusCode}
	}
	if out != nil {
		if err := json.NewDecoder(resp.Body).Decode(out); err != nil {
			return resp.StatusCode, err
		}
	} else {
		_, _ = io.Copy(io.Discard, resp.Body)
	}
	return resp.StatusCode, nil
}

type httpError struct{ status int }

func (e *httpError) Error() string { return "respuesta HTTP " + strconv.Itoa(e.status) }
