// Package config carga toda la configuración desde variables de entorno, con los
// mismos nombres y valores por defecto que la API en Java (application.yml). En
// local, lee opcionalmente un archivo .env de la carpeta del proyecto.
package config

import (
	"encoding/base64"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/joho/godotenv"
)

// Config agrupa la configuración de todos los componentes.
type Config struct {
	Server    Server
	Security  Security
	Readings  Readings
	Database  Database
	Cache     Cache
	Mail      Mail
	MQTT      MQTT
	AI        AI
	Telegram  Telegram
	Simulator Simulator
	Channels  Channels
}

type Server struct {
	Port         int
	LogLevel     string
	WebBaseURL   string
	PublicAPIURL string
	DocsEnabled  bool
	// TrustProxy activa la IP real del cliente desde X-Forwarded-For, solo cuando la conexión llega desde
	// TrustedProxies (CIDR). Sin rangos, se confía en redes privadas, loopback y link-local.
	TrustProxy     bool
	TrustedProxies []string
}

type Security struct {
	JWTSecret               string
	JWTExpiration           time.Duration
	AESKey                  string
	CORSAllowedOrigins      []string
	RateLimitPerMinute      int
	AuthRateLimitPerMinute  int
	PasswordResetExpiration time.Duration
}

type Readings struct {
	MinInterval time.Duration
	MaxPageSize int
}

type Database struct {
	URL      string
	MaxConns int
}

// Cache agrupa la conexión al sistema de caché. Los nombres son neutrales
// (CACHE_*) porque el backend está detrás de un puerto; se aceptan los antiguos
// REDIS_* por compatibilidad.
type Cache struct {
	Host     string
	Port     int
	Username string
	Password string
	Database int
	Timeout  time.Duration
}

type Mail struct {
	Enabled  bool
	Host     string
	Port     int
	Username string
	Password string
	SMTPAuth bool
	STARTTLS bool
	From     string
}

type MQTT struct {
	Enabled        bool
	BrokerURI      string
	ClientID       string
	Username       string
	Password       string
	TopicPrefix    string
	PublicHost     string
	PublicPort     int
	PublicTLS      bool
	WebSocketURL   string
	CommandTimeout time.Duration
	// AuthnToken es el secreto que el broker (EMQX) envía al autenticar dispositivos contra la API.
	AuthnToken string
	// APIURL, APIKey y APISecret son la API REST de EMQX, que la API usa para cortar sesiones al rotar o borrar.
	APIURL                  string
	APIKey                  string
	APISecret               string
	SharedSubscriptionGroup string
}

type AI struct {
	Enabled            bool
	BaseURL            string
	Token              string
	Timeout            time.Duration
	EvaluationInterval time.Duration
	AutomationCooldown time.Duration
	HistorySize        int
	Timezone           string
	LearningEnabled    bool
	LearningFlush      time.Duration
	// GRPCSocket (socket UNIX) o GRPCAddr (host:puerto interno) activan gRPC hacia la IA; si faltan, solo REST.
	GRPCSocket string
	GRPCAddr   string
}

type Telegram struct {
	BotToken      string
	BotUsername   string
	Mode          string
	WebhookURL    string
	WebhookSecret string
	APIBaseURL    string
}

type Simulator struct {
	Enabled           bool
	BaseURL           string
	Token             string
	Timeout           time.Duration
	ReconcileInterval time.Duration
}

type Channels struct {
	DigestInterval time.Duration
}

// Load lee el .env opcional y arma la configuración. Falla si faltan los
// secretos obligatorios o tienen un formato inválido.
func Load() (Config, error) {
	// El .env es opcional: en producción la configuración viene del entorno.
	_ = godotenv.Load()

	var errs []string
	fail := func(format string, args ...any) {
		errs = append(errs, fmt.Sprintf(format, args...))
	}

	dur := func(key, def string) time.Duration {
		d, err := ParseDuration(env(key, def))
		if err != nil {
			fail("%s: %v", key, err)
		}
		return d
	}

	publicAPIURL := env("PUBLIC_API_URL", "http://localhost:8091")

	cfg := Config{
		Server: Server{
			Port:         envInt("PORT", 8091),
			LogLevel:     env("LOG_LEVEL", "INFO"),
			WebBaseURL:   env("WEB_BASE_URL", "http://localhost:5173"),
			PublicAPIURL: publicAPIURL,
			DocsEnabled:  envBool("API_DOCS_ENABLED", true),
			// Se aceptan los nombres de producción SERVER_* además de los neutrales.
			TrustProxy: envBool("TRUST_PROXY", envBool("SERVER_TRUST_PROXY",
				envBool("SMARTPOT_TRUST_PROXY", false))),
			TrustedProxies: envListFallback("TRUSTED_PROXY_CIDRS", "SERVER_TRUSTED_PROXIES", ""),
		},
		Security: Security{
			JWTSecret:               os.Getenv("SMARTPOT_JWT_SECRET"),
			JWTExpiration:           dur("JWT_EXPIRATION", "P7D"),
			AESKey:                  os.Getenv("SMARTPOT_AES_KEY"),
			CORSAllowedOrigins:      envList("CORS_ALLOWED_ORIGINS", "http://localhost:5173"),
			RateLimitPerMinute:      envInt("RATE_LIMIT_PER_MINUTE", 300),
			AuthRateLimitPerMinute:  envInt("AUTH_RATE_LIMIT_PER_MINUTE", 10),
			PasswordResetExpiration: dur("PASSWORD_RESET_EXPIRATION", "PT30M"),
		},
		Readings: Readings{
			MinInterval: dur("READINGS_MIN_INTERVAL", "PT5S"),
			MaxPageSize: envInt("READINGS_MAX_PAGE_SIZE", 2000),
		},
		Database: Database{
			URL: env("DATABASE_URL", fmt.Sprintf(
				"postgres://smartpot:smartpot@localhost:5432/smartpot?sslmode=%s",
				env("DB_SSLMODE", "prefer"),
			)),
			MaxConns: envInt("DATABASE_MAX_CONNS", 10),
		},
		Cache: Cache{
			Host:     envFallback("CACHE_HOST", "REDIS_HOST", "localhost"),
			Port:     envIntFallback("CACHE_PORT", "REDIS_PORT", 6379),
			Username: envFallback("CACHE_USERNAME", "REDIS_USERNAME", ""),
			Password: envFallback("CACHE_PASSWORD", "REDIS_PASSWORD", ""),
			Database: envIntFallback("CACHE_DATABASE", "REDIS_DATABASE", 0),
			Timeout:  2 * time.Second,
		},
		Mail: Mail{
			Enabled:  envBool("MAIL_ENABLED", true),
			Host:     env("MAIL_HOST", "localhost"),
			Port:     envInt("MAIL_PORT", 1025),
			Username: env("MAIL_USERNAME", ""),
			Password: env("MAIL_PASSWORD", ""),
			SMTPAuth: envBool("MAIL_SMTP_AUTH", false),
			STARTTLS: envBool("MAIL_SMTP_STARTTLS", false),
			From:     env("MAIL_FROM", "SmartPot <no-reply@smartpot.app>"),
		},
		MQTT: MQTT{
			Enabled:                 envBool("MQTT_ENABLED", true),
			BrokerURI:               env("MQTT_BROKER_URI", "tcp://localhost:1883"),
			ClientID:                env("MQTT_CLIENT_ID", "smartpot-api"),
			Username:                env("MQTT_ADMIN_USERNAME", "smartpot-api"),
			Password:                env("MQTT_ADMIN_PASSWORD", ""),
			TopicPrefix:             env("MQTT_TOPIC_PREFIX", "smartpot/v1"),
			PublicHost:              env("MQTT_PUBLIC_HOST", "localhost"),
			PublicPort:              envInt("MQTT_PUBLIC_PORT", 1883),
			PublicTLS:               envBool("MQTT_PUBLIC_TLS", false),
			WebSocketURL:            env("MQTT_WEBSOCKET_URL", "ws://localhost:9001"),
			CommandTimeout:          dur("MQTT_COMMAND_TIMEOUT", "PT2M"),
			AuthnToken:              os.Getenv("MQTT_AUTHN_TOKEN"),
			APIURL:                  env("MQTT_BROKER_API_URL", "http://broker-smartpot:18083"),
			APIKey:                  os.Getenv("MQTT_BROKER_API_KEY"),
			APISecret:               os.Getenv("MQTT_BROKER_API_SECRET"),
			SharedSubscriptionGroup: env("MQTT_SHARED_SUBSCRIPTION_GROUP", "smartpot-api-workers"),
		},
		AI: AI{
			Enabled:            envBool("AI_ENABLED", true),
			BaseURL:            env("AI_BASE_URL", "http://localhost:8000"),
			Token:              os.Getenv("SMARTPOT_AI_TOKEN"),
			Timeout:            dur("AI_TIMEOUT", "PT5S"),
			EvaluationInterval: dur("AI_EVALUATION_INTERVAL", "PT5M"),
			AutomationCooldown: dur("AI_AUTOMATION_COOLDOWN", "PT10M"),
			HistorySize:        envInt("AI_HISTORY_SIZE", 48),
			Timezone:           env("AI_TIMEZONE", "America/Bogota"),
			LearningEnabled:    envBool("AI_LEARNING_ENABLED", true),
			LearningFlush:      dur("AI_LEARNING_FLUSH_INTERVAL", "PT1M"),
			GRPCSocket:         os.Getenv("AI_GRPC_SOCKET"),
			GRPCAddr:           os.Getenv("AI_GRPC_ADDR"),
		},
		Telegram: Telegram{
			BotToken:      os.Getenv("TELEGRAM_BOT_TOKEN"),
			BotUsername:   os.Getenv("TELEGRAM_BOT_USERNAME"),
			Mode:          env("TELEGRAM_MODE", "polling"),
			WebhookURL:    env("TELEGRAM_WEBHOOK_URL", publicAPIURL+"/api/v1/channels/telegram/webhook"),
			WebhookSecret: os.Getenv("TELEGRAM_WEBHOOK_SECRET"),
			APIBaseURL:    env("TELEGRAM_API_BASE_URL", "https://api.telegram.org"),
		},
		Simulator: Simulator{
			Enabled:           envBool("SIMULATOR_ENABLED", true),
			BaseURL:           env("SIMULATOR_BASE_URL", "http://localhost:8081"),
			Token:             os.Getenv("SIMULATOR_TOKEN"),
			Timeout:           dur("SIMULATOR_TIMEOUT", "PT10S"),
			ReconcileInterval: dur("SIMULATOR_RECONCILE_INTERVAL", "PT1M"),
		},
		Channels: Channels{
			DigestInterval: dur("CHANNELS_DIGEST_INTERVAL", "PT1M"),
		},
	}

	// Secretos obligatorios, igual que en Java: sin ellos la API no arranca.
	if len(cfg.Security.JWTSecret) < 32 {
		fail("SMARTPOT_JWT_SECRET must have at least 32 characters")
	}
	if key, err := base64.StdEncoding.DecodeString(cfg.Security.AESKey); err != nil || len(key) != 32 {
		fail("SMARTPOT_AES_KEY must be a 32-byte Base64 string (AES-256)")
	}

	if len(errs) > 0 {
		return Config{}, fmt.Errorf("invalid configuration:\n  - %s", strings.Join(errs, "\n  - "))
	}
	return cfg, nil
}

func env(key, def string) string {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		return v
	}
	return def
}

func envInt(key string, def int) int {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		if n, err := strconv.Atoi(strings.TrimSpace(v)); err == nil {
			return n
		}
	}
	return def
}

// envFallback usa la variable nueva si existe; si no, la antigua; si no, el valor por defecto.
func envFallback(primary, fallback, def string) string {
	if v, ok := os.LookupEnv(primary); ok && v != "" {
		return v
	}
	return env(fallback, def)
}

func envIntFallback(primary, fallback string, def int) int {
	if v, ok := os.LookupEnv(primary); ok && v != "" {
		if n, err := strconv.Atoi(strings.TrimSpace(v)); err == nil {
			return n
		}
	}
	return envInt(fallback, def)
}

func envBool(key string, def bool) bool {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		if b, err := strconv.ParseBool(strings.TrimSpace(v)); err == nil {
			return b
		}
	}
	return def
}

// envListFallback usa la variable nueva si existe; si no, el alias antiguo.
func envListFallback(primary, fallback, def string) []string {
	if v, ok := os.LookupEnv(primary); ok && strings.TrimSpace(v) != "" {
		return envList(primary, def)
	}
	return envList(fallback, def)
}

func envList(key, def string) []string {
	raw := env(key, def)
	parts := strings.Split(raw, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		if t := strings.TrimSpace(p); t != "" {
			out = append(out, t)
		}
	}
	return out
}
