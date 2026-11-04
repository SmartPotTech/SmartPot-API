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

	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
)

const (
	simUnavailable = "The virtual crop simulator is not available at this moment"
	simDisabled    = "Virtual crops are not enabled on this server"
)

// SimulatorClient es el cliente de la API interna del simulador de dispositivos.
type SimulatorClient struct {
	available bool
	baseURL   string
	token     string
	http      *http.Client
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

// Places busca lugares para el modo clima.
func (c *SimulatorClient) Places(ctx context.Context, query string) ([]PlaceResponse, error) {
	if err := c.requireAvailable(); err != nil {
		return nil, err
	}
	var places []PlaceResponse
	path := "/v1/places?" + url.Values{"q": {query}}.Encode()
	if _, err := c.do(ctx, http.MethodGet, path, nil, &places); err != nil {
		return nil, apperr.Unavailable("Could not query location service")
	}
	return places, nil
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
