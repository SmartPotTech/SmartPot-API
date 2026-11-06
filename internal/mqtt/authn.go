package mqtt

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/objectid"
	"github.com/labstack/echo/v4"
)

// brokerTokenHeader es la cabecera con el secreto compartido que el broker envía en cada llamada.
const brokerTokenHeader = "x-smartpot-broker-token"

// DeviceVerifier es lo que el controlador necesita para autenticar un dispositivo: buscar el
// cultivo por id y obtener su clave en claro. Lo implementa crops.Service.
type DeviceVerifier interface {
	Find(ctx context.Context, cropID string) (*crops.Crop, error)
	DeviceKey(crop *crops.Crop) (string, bool)
}

// AuthnController es el endpoint interno que el broker (EMQX) llama para autenticar
// dispositivos. La API verifica la clave del cultivo contra su base y devuelve el
// permiso y un ACL acotado a ese cultivo. No lleva JWT ni límite de tasa: lo protegen
// el secreto compartido (x-smartpot-broker-token) y la red interna de Docker.
type AuthnController struct {
	token  string
	crops  DeviceVerifier
	cache  cache.Cache
	topics *TopicResolver
	log    *slog.Logger
}

// NewAuthnController crea el controlador de autenticación de dispositivos con soporte de caché.
func NewAuthnController(cfg config.MQTT, cropsSvc DeviceVerifier, store cache.Cache, topics *TopicResolver, logger *slog.Logger) *AuthnController {
	if cfg.AuthnToken == "" {
		logger.Warn("MQTT_AUTHN_TOKEN is empty: broker will not be able to authenticate devices (all will be rejected)")
	}
	return &AuthnController{token: cfg.AuthnToken, crops: cropsSvc, cache: store, topics: topics, log: logger}
}

// Register monta la ruta interna fuera de /api/v1 (sin JWT ni límite de tasa).
func (c *AuthnController) Register(engine *echo.Echo) {
	engine.POST("/internal/mqtt/authn", c.authenticate)
	// Sonda de salud: el broker comprueba que el endpoint responde; así marca el recurso como conectado.
	engine.GET("/internal/mqtt/authn", func(ctx echo.Context) error {
		return ctx.JSON(http.StatusOK, echo.Map{"status": "ok"})
	})
}

type authnRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
	ClientID string `json:"clientid"`
}

// aclRule es una regla del ACL que EMQX adjunta a la conexión (campo acl, EMQX 5.8+).
type aclRule struct {
	Permission string `json:"permission"`
	Action     string `json:"action"`
	Topic      string `json:"topic"`
}

type authnResponse struct {
	Result string    `json:"result"`
	ACL    []aclRule `json:"acl,omitempty"`
}

var deny = authnResponse{Result: "deny"}

const (
	deviceKeyCachePrefix = "crop:key-hmac:"
	deviceKeyCacheTTL    = 5 * time.Minute
	// Cada cultivo tiene su propio cubo: una ráfaga de un dispositivo no puede
	// bloquear la autenticación de toda la flota.
	authnRatePerMinute = 30
)

func (c *AuthnController) authenticate(ctx echo.Context) error {
	if !c.authorized(ctx.Request().Header.Get(brokerTokenHeader)) {
		return ctx.JSON(http.StatusOK, deny)
	}
	var req authnRequest
	if err := json.NewDecoder(ctx.Request().Body).Decode(&req); err != nil {
		return ctx.JSON(http.StatusOK, deny)
	}

	// El usuario MQTT debe ser EXACTAMENTE el ObjectId hexadecimal en minúsculas.
	// Aceptar mayúsculas o espacios permitía que el filtro de desconexión de EMQX
	// no encontrara la sesión vieja y la revocación de clave quedara eludida (H-01).
	oid, ok := objectid.Parse(req.Username)
	if !ok || req.Password == "" {
		return ctx.JSON(http.StatusOK, deny)
	}
	cropID := oid.Hex()
	if req.Username != cropID {
		return ctx.JSON(http.StatusOK, deny)
	}
	// El clientid queda ligado al cultivo: impide el takeover de una sesión
	// ajena con un id predecible (M-06).
	if req.ClientID != "smartpot-"+cropID && req.ClientID != "smartpot-sim-"+cropID {
		return ctx.JSON(http.StatusOK, deny)
	}
	if !c.allowAttempt(req.Username) {
		return ctx.JSON(http.StatusOK, deny)
	}

	// La caché evita ir a PostgreSQL en cada reconexión, pero solo guarda un HMAC de la clave: quien lea Valkey
	// no obtiene credenciales de dispositivo.
	cacheKey := deviceKeyCachePrefix + cropID
	presented := c.digest(req.Password)
	if c.cache != nil {
		if cached, hit := c.cache.Get(cacheKey); hit {
			if subtle.ConstantTimeCompare([]byte(cached), []byte(presented)) != 1 {
				return ctx.JSON(http.StatusOK, deny)
			}
			return ctx.JSON(http.StatusOK, authnResponse{Result: "allow", ACL: c.deviceACL(cropID)})
		}
	}

	crop, err := c.crops.Find(ctx.Request().Context(), cropID)
	if err != nil {
		// Error real de la base: 5xx para que EMQX lo trate como "ignore" y no cachee un rechazo.
		c.log.Warn("could not verify device against database", "cropId", req.Username, "err", err)
		return ctx.JSON(http.StatusServiceUnavailable, authnResponse{})
	}
	if crop == nil {
		return ctx.JSON(http.StatusOK, deny)
	}
	key, ok := c.crops.DeviceKey(crop)
	if !ok || subtle.ConstantTimeCompare([]byte(key), []byte(req.Password)) != 1 {
		return ctx.JSON(http.StatusOK, deny)
	}
	if c.cache != nil {
		c.cache.Put(cacheKey, presented, deviceKeyCacheTTL)
	}
	return ctx.JSON(http.StatusOK, authnResponse{Result: "allow", ACL: c.deviceACL(crop.HexID())})
}

// Invalidate invalida la clave del dispositivo en caché (por borrado o rotación).
func (c *AuthnController) Invalidate(cropID string) {
	if c.cache == nil {
		return
	}
	if oid, ok := objectid.Parse(strings.TrimSpace(cropID)); ok {
		c.cache.Delete(deviceKeyCachePrefix + oid.Hex())
	}
}

// digest es el HMAC-SHA256 de la clave presentada, con el secreto compartido con el broker como llave.
func (c *AuthnController) digest(password string) string {
	mac := hmac.New(sha256.New, []byte(c.token))
	mac.Write([]byte(password))
	return hex.EncodeToString(mac.Sum(nil))
}

// deviceACL acota el dispositivo a los tópicos de su propio cultivo; lo demás se niega.
func (c *AuthnController) deviceACL(cropID string) []aclRule {
	return []aclRule{
		{Permission: "allow", Action: "publish", Topic: c.topics.Telemetry(cropID)},
		{Permission: "allow", Action: "publish", Topic: c.topics.Status(cropID)},
		{Permission: "allow", Action: "publish", Topic: c.topics.CommandAck(cropID)},
		{Permission: "allow", Action: "subscribe", Topic: c.topics.Commands(cropID)},
		{Permission: "allow", Action: "publish", Topic: c.topics.Capabilities(cropID)},
		{Permission: "deny", Action: "all", Topic: "#"},
	}
}

// allowAttempt aplica una ventana fija de un minuto por usuario de dispositivo.
// Si la caché no está disponible, la caché local del Store sigue contando.
func (c *AuthnController) allowAttempt(username string) bool {
	if c.cache == nil {
		return true
	}
	epoch := time.Now().Unix() / 60
	return c.cache.Increment("authn:rate:"+username+":"+strconv.FormatInt(epoch, 10), time.Minute) <= authnRatePerMinute
}

func (c *AuthnController) authorized(token string) bool {
	if c.token == "" || token == "" {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(c.token), []byte(token)) == 1
}
