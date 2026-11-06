package mqtt

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
)

// Provisioner implementa crops.DeviceProvisioner para el broker EMQX. Con EMQX la
// autenticación de los dispositivos la resuelve la API en vivo (la clave vive en la
// base y EMQX la consulta al conectar), así que crear un cultivo no necesita registrar
// nada en el broker. Solo al rotar o borrar la clave hay que cortar las sesiones vivas
// para que la clave vieja deje de servir de inmediato, y eso se hace por la API REST de EMQX.
type Provisioner struct {
	baseURL string
	key     string
	secret  string
	http    *http.Client
	log     *slog.Logger
}

// clientListResponse es la lista de sesiones que devuelve GET /api/v5/clients.
type clientListResponse struct {
	Data []struct {
		ClientID string `json:"clientid"`
	} `json:"data"`
	Meta struct {
		HasNext bool `json:"hasnext"`
	} `json:"meta"`
}

const clientPageSize = 100

// NewProvisioner crea el aprovisionador contra la API REST de EMQX.
func NewProvisioner(cfg config.MQTT, logger *slog.Logger) *Provisioner {
	return &Provisioner{
		baseURL: strings.TrimRight(cfg.APIURL, "/"),
		key:     cfg.APIKey,
		secret:  cfg.APISecret,
		http:    &http.Client{Timeout: 5 * time.Second},
		log:     logger,
	}
}

// Provision no registra nada en el broker: la clave ya está en la base y EMQX la
// consulta al conectar. Al rotar, corta las sesiones vivas para invalidar la clave anterior.
func (p *Provisioner) Provision(ctx context.Context, cropID, _ string, rotate bool) error {
	if rotate {
		p.kick(ctx, cropID)
	}
	return nil
}

// Deprovision corta las sesiones vivas del cultivo; su cuenta ya no existe en la base,
// así que tampoco podrá volver a autenticarse.
func (p *Provisioner) Deprovision(ctx context.Context, cropID string) error {
	p.kick(ctx, cropID)
	return nil
}

// kick desconecta todas las sesiones cuyo usuario es el id del cultivo (el dispositivo
// real y, si lo hubiera, el simulado). Es best-effort: si falla, se registra y la clave
// nueva igual aplicará en la próxima conexión.
func (p *Provisioner) kick(ctx context.Context, cropID string) {
	if p.baseURL == "" || p.key == "" {
		return // API REST del broker no configurada: nada que cortar.
	}
	clients, err := p.clientsByUsername(ctx, cropID)
	if err != nil {
		p.log.Warn("could not list crop sessions in broker", "cropId", cropID, "err", err)
		return
	}
	for _, clientID := range clients {
		if err := p.disconnect(ctx, clientID); err != nil {
			p.log.Warn("could not disconnect session in broker", "cropId", cropID, "clientId", clientID, "err", err)
		}
	}
	if len(clients) > 0 {
		p.log.Info("crop sessions disconnected in broker", "cropId", cropID, "sessions", len(clients))
	}
}

// clientsByUsername recorre todas las páginas mientras meta.hasnext sea
// verdadero: con más de 100 sesiones (por ejemplo, reconexiones zombie) una
// sola página dejaría sesiones vivas con la clave anterior (M-01).
func (p *Provisioner) clientsByUsername(ctx context.Context, cropID string) ([]string, error) {
	seen := make(map[string]struct{})
	ids := make([]string, 0)
	for page := 1; page <= 100; page++ {
		endpoint := fmt.Sprintf("%s/api/v5/clients?username=%s&fields=clientid&limit=%d&page=%d",
			p.baseURL, url.QueryEscape(cropID), clientPageSize, page)
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
		if err != nil {
			return nil, err
		}
		req.SetBasicAuth(p.key, p.secret)
		resp, err := p.http.Do(req)
		if err != nil {
			return nil, err
		}
		if resp.StatusCode != http.StatusOK {
			body, _ := io.ReadAll(io.LimitReader(resp.Body, 512))
			resp.Body.Close()
			return nil, fmt.Errorf("broker returned %d while listing sessions: %s", resp.StatusCode, strings.TrimSpace(string(body)))
		}
		var parsed clientListResponse
		decodeErr := json.NewDecoder(resp.Body).Decode(&parsed)
		resp.Body.Close()
		if decodeErr != nil {
			return nil, decodeErr
		}
		for _, c := range parsed.Data {
			if c.ClientID == "" {
				continue
			}
			if _, ok := seen[c.ClientID]; ok {
				continue
			}
			seen[c.ClientID] = struct{}{}
			ids = append(ids, c.ClientID)
		}
		if !parsed.Meta.HasNext {
			return ids, nil
		}
	}
	return ids, nil
}

func (p *Provisioner) disconnect(ctx context.Context, clientID string) error {
	endpoint := fmt.Sprintf("%s/api/v5/clients/%s", p.baseURL, url.PathEscape(clientID))
	req, err := http.NewRequestWithContext(ctx, http.MethodDelete, endpoint, nil)
	if err != nil {
		return err
	}
	req.SetBasicAuth(p.key, p.secret)
	resp, err := p.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	// 204 al desconectar, 404 si la sesión ya no existe: ambos son aceptables.
	if resp.StatusCode != http.StatusNoContent && resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNotFound {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 512))
		return fmt.Errorf("broker returned %d: %s", resp.StatusCode, strings.TrimSpace(string(body)))
	}
	return nil
}
