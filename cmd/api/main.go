// Command api es el punto de entrada de SmartPot-API en Go: carga la
// configuración, cablea los componentes y expone la API HTTP.
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/actuators"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/ai"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/auth"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache/valkey"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/channels"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/channels/telegram"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/commands"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/connections"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/health"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/mail"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/mqtt"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/openapi"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/overview"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/events"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/geo"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/logging"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/storage/postgres"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/users"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/virtualdevices"
)

func main() {
	// Subcomando para el HEALTHCHECK de Docker (lo usa el propio binario; la
	// imagen de ejecución no trae wget ni curl).
	if len(os.Args) > 1 && os.Args[1] == "healthcheck" {
		os.Exit(healthcheck())
	}
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, "fatal startup error:", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}

	logger := logging.Setup(cfg.Server.LogLevel)
	logger.Info("starting SmartPot-API", "port", cfg.Server.Port)

	clock := func() time.Time { return time.Now().UTC() }

	connectCtx, cancelConnect := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancelConnect()
	// Adaptador de almacenamiento: PostgreSQL + TimescaleDB. La conexión es perezosa (la API arranca aunque la
	// base no responda todavía) y /health informa el estado real.
	pool, err := postgres.Connect(connectCtx, cfg.Database.URL, cfg.Database.MaxConns)
	if err != nil {
		return err
	}

	// Adaptador de caché: Valkey detrás del puerto cache.Backend. El Store añade
	// respaldo en memoria local si el backend no responde.
	cacheBackend := valkey.New(
		fmt.Sprintf("%s:%d", cfg.Cache.Host, cfg.Cache.Port),
		cfg.Cache.Username, cfg.Cache.Password, cfg.Cache.Database, cfg.Cache.Timeout)
	cacheStore := cache.NewStore(cacheBackend)

	bus := events.NewBus()

	tokenService := security.NewTokenService(cfg.Security.JWTSecret, cfg.Security.JWTExpiration)
	// Revocación server-side de JWT: jti en logout y versión por usuario al
	// cambiar o restablecer la contraseña.
	tokenService.UseSessionStore(cacheStore)
	encryptor, err := security.NewEncryptor(cfg.Security.AESKey)
	if err != nil {
		return err
	}

	mailer, err := mail.NewMailer(cfg.Mail, cfg.Server.WebBaseURL, logger)
	if err != nil {
		return err
	}

	// Repositorios: adaptadores de PostgreSQL que implementan los puertos de cada dominio. El esquema (tablas,
	// hipertabla e índices) lo crea la imagen SmartPot-DB-Postgre, no la API.
	userRepo := postgres.NewUserRepository(pool.Pool)
	tokenRepo := postgres.NewTokenRepository(pool.Pool)
	notificationRepo := postgres.NewNotificationRepository(pool.Pool)
	cropRepo := postgres.NewCropRepository(pool.Pool)
	readingRepo := postgres.NewReadingRepository(pool.Pool)
	actuatorRepo := postgres.NewActuatorRepository(pool.Pool)
	commandRepo := postgres.NewCommandRepository(pool.Pool)
	vdRepo := postgres.NewVirtualDeviceRepository(pool.Pool)
	channelLinkRepo := postgres.NewChannelLinkRepository(pool.Pool)
	cropChannelRepo := postgres.NewCropChannelRepository(pool.Pool)

	// Servicios.
	notificationService := notifications.NewService(notificationRepo, cacheStore, bus, clock)
	topics := mqtt.NewTopicResolver(cfg.MQTT.TopicPrefix)
	mqttPublic := crops.MQTTPublic{Host: cfg.MQTT.PublicHost, Port: cfg.MQTT.PublicPort, TLS: cfg.MQTT.PublicTLS,
		WebSocketURL: cfg.MQTT.WebSocketURL, TopicPrefix: cfg.MQTT.TopicPrefix}
	cropService := crops.NewService(cropRepo, encryptor, mqttPublic, noopProvisioner{}, noopActuatorSeeder{},
		notificationService, bus, clock)
	cropService.SetQuotas(cfg.Quotas.MaxCropsPerUser, cfg.Quotas.MaxVirtualCropsPerUser)
	readingService := readings.NewService(readingRepo, cropService, cacheStore, bus, clock,
		cfg.Readings.MinInterval, cfg.Readings.MaxPageSize)
	simulatorClient := virtualdevices.NewSimulatorClient(cfg.Simulator)
	weatherService := crops.NewWeatherService(simulatorClient, cacheStore)
	actuatorService := actuators.NewService(actuatorRepo, cropService, clock)
	cropService.SetActuatorSeeder(actuatorService)
	commandService := commands.NewService(commandRepo, cropService, actuatorService, notificationService, clock,
		cfg.MQTT.CommandTimeout)
	virtualDeviceService := virtualdevices.NewService(vdRepo, virtualCropsAdapter{crops: cropService}, simulatorClient, clock)
	virtualDeviceService.SetMaxPerAccount(cfg.Quotas.MaxVirtualCropsPerUser)

	// MQTT: gateway, enrutado de mensajes y corte de sesiones en el broker. Con EMQX la clave del
	// dispositivo vive en la base y el broker la consulta al conectar; no hay que registrar cuentas.
	gateway := mqtt.NewGateway(cfg.MQTT, topics, logger)
	provisioner := mqtt.NewProvisioner(cfg.MQTT, logger)
	mqttHandler := mqtt.NewMessageHandler(topics, readingService, commandService, cropService, logger)
	gateway.SetRouter(mqttHandler.Route)
	cropService.SetProvisioner(provisioner)
	commandService.SetPublisher(gateway)
	gateway.Start()

	// Asistente de IA: evaluación, agente de automatización y aprendizaje continuo.
	timezone, err := time.LoadLocation(cfg.AI.Timezone)
	if err != nil {
		logger.Warn("invalid AI timezone; using UTC", "timezone", cfg.AI.Timezone, "err", err)
		timezone = time.UTC
	}
	aiClient := ai.NewClient(cfg.AI, cacheStore, clock)
	insightService := ai.NewInsightService(aiClient, cropService, readingService, actuatorService, weatherService,
		cacheStore, clock, cfg.AI.HistorySize, timezone)
	agent := ai.NewAutomationAgent(insightService, commandService, notificationService, cacheStore, aiClient,
		cropService, cfg.AI.EvaluationInterval, cfg.AI.AutomationCooldown)
	learningFeed := ai.NewLearningFeed(aiClient, cropService, cfg.AI.LearningEnabled, timezone)
	overviewService := overview.NewService(cropService, readingService, actuatorService, commandService,
		notificationService, aiClient, cacheStore, clock, timezone)
	events.Subscribe(bus, func(e readings.ReadingRecordedEvent) { go agent.OnReading(e) })
	events.Subscribe(bus, func(e readings.ReadingRecordedEvent) { learningFeed.OnReading(e) })
	events.Subscribe(bus, func(e crops.DeletedEvent) { go learningFeed.OnCropDeleted(e) })

	userService := users.NewService(userRepo, clock)
	userService.AddSessionRevoker(func(_ context.Context, userID string) error {
		tokenService.BumpUserVersion(userID)
		return nil
	})
	userService.AddAccountDeleter(notificationService.DeleteAllForUser)
	userService.AddAccountDeleter(tokenRepo.DeleteByUserID)
	userService.AddAccountDeleter(cropService.DeleteAllOwnedBy)
	userService.AddAccountFinalizer(func(_ context.Context, userID string) error {
		tokenService.ForgetUser(userID)
		return nil
	})
	// Conexiones personales (clientes MCP y similares): el guard las acepta además de la sesión.
	connectionService := connections.NewService(postgres.NewAccessTokenRepository(pool.Pool), clock)
	connectionService.SetMaxPerUser(cfg.Quotas.MaxConnectionsPerUser)
	tokenService.UseConnections(connectionService)
	userService.AddAccountDeleter(connectionService.DeleteAllForUser)
	authService := auth.NewService(userRepo, tokenRepo, tokenService, mailer, notificationService, clock,
		cfg.Security.PasswordResetExpiration)

	// Borrado en cascada de los datos de un cultivo.
	events.Subscribe(bus, func(e crops.DeletedEvent) {
		ctx := context.Background()
		if err := readingService.DeleteByCrop(ctx, e.CropID); err != nil {
			logger.Warn("could not delete crop readings", "cropId", e.CropID, "err", err)
		}
		if err := commandService.DeleteByCrop(ctx, e.CropID); err != nil {
			logger.Warn("could not delete crop commands", "cropId", e.CropID, "err", err)
		}
		if err := actuatorService.DeleteByCrop(ctx, e.CropID); err != nil {
			logger.Warn("could not delete crop actuators", "cropId", e.CropID, "err", err)
		}
		if err := notificationService.DeleteAllForCrop(ctx, e.CropID); err != nil {
			logger.Warn("could not delete crop notifications", "cropId", e.CropID, "err", err)
		}
	})
	events.Subscribe(bus, func(e crops.DeletedEvent) { go virtualDeviceService.OnCropDeleted(e.CropID) })
	events.Subscribe(bus, func(e crops.PlacementChangedEvent) { go virtualDeviceService.OnPlacementChanged(e.CropID) })
	events.Subscribe(bus, func(e crops.DeviceKeyRotatedEvent) { go virtualDeviceService.OnKeyRotated(e.CropID) })

	// Canales externos (Telegram): vínculos de cuenta, avisos por cultivo y resúmenes.
	telegramClient := telegram.NewClient(cfg.Telegram)
	telegramChannel := telegram.NewChannel(cfg.Telegram, telegramClient)
	channelService := channels.NewService(channelLinkRepo, []channels.NotificationChannel{telegramChannel}, cacheStore, clock)
	cropChannelService := channels.NewCropChannelService(cropChannelRepo, channelService, cropService, cacheStore, clock)
	channelSender := channels.NewSender(channelService, cropChannelService)
	dispatcher := channels.NewDispatcher(channelService, cropChannelService, channelSender, cfg.Server.WebBaseURL)
	digest := channels.NewDigest(cropChannelRepo, channelService, channelSender, cropService, notificationRepo,
		readingService, commandService, clock, timezone, cfg.Server.WebBaseURL)
	telegramBot := telegram.NewBot(channelService, cropChannelService, cropService, telegramClient)
	telegramListener := telegram.NewListener(cfg.Telegram, telegramClient, telegramBot)
	telegramWebhook := telegram.NewWebhookController(cfg.Telegram, telegramListener)
	userService.AddAccountDeleter(channelService.DeleteAllForUser)
	userService.AddAccountDeleter(cropChannelService.DeleteAllForOwner)
	events.Subscribe(bus, func(e notifications.CreatedEvent) { go dispatcher.OnNotification(e) })
	events.Subscribe(bus, func(e crops.DeletedEvent) { go cropChannelService.OnCropDeleted(e.CropID) })

	// Servidor HTTP.
	rateLimiter := security.NewRateLimiter(cacheStore, cfg.Security.RateLimitPerMinute, cfg.Security.AuthRateLimitPerMinute)
	engine := httpserver.New(logger, httpserver.Options{
		TrustProxy:     cfg.Server.TrustProxy,
		TrustedProxies: cfg.Server.TrustedProxies,
	})
	// El broker (EMQX) autentica los dispositivos contra este endpoint interno con caché. Va antes del límite de
	// tasa y de CORS: lo llama el propio broker en cada conexión, no un navegador.
	authnController := mqtt.NewAuthnController(cfg.MQTT, cropService, cacheStore, topics, logger)
	authnController.Register(engine)
	events.Subscribe(bus, func(e crops.DeletedEvent) { authnController.Invalidate(e.CropID) })
	events.Subscribe(bus, func(e crops.DeviceKeyRotatedEvent) { authnController.Invalidate(e.CropID) })
	engine.Use(security.CORS(cfg.Security.CORSAllowedOrigins), rateLimiter.Middleware())

	checker := health.Checker{
		Database: func() bool {
			ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
			defer cancel()
			return pool.Ping(ctx) == nil
		},
		Cache:     cacheStore.Available,
		Broker:    func() (bool, bool) { return gateway.IsEnabled(), gateway.IsConnected() },
		AI:        func() (bool, bool) { return aiClient.IsEnabled(), aiClient.IsHealthy() },
		Simulator: func() (bool, bool) { return simulatorClient.IsAvailable(), simulatorClient.IsHealthy() },
	}
	engine.GET("/health", checker.Handler())

	// Documentación OpenAPI en /v3/api-docs y la interfaz de Swagger en /docs.
	openapi.Register(engine, cfg.Server.PublicAPIURL, cfg.Server.DocsEnabled)

	// Rutas públicas y autenticadas bajo /api/v1.
	v1 := engine.Group("/api/v1")
	authHandler := auth.NewHandler(authService)
	authHandler.Register(v1)
	aiHandler := ai.NewHandler(insightService, aiClient)
	aiHandler.RegisterPublic(v1)
	telegramWebhook.Register(v1)

	authed := v1.Group("")
	authed.Use(tokenService.Guard())
	authHandler.RegisterAuthed(authed)
	users.NewHandler(userService).Register(authed)
	notifications.NewHandler(notificationService).Register(authed)
	crops.NewHandler(cropService, readingService, virtualDeviceService, weatherService).Register(authed)
	readings.NewHandler(readingService).Register(authed)
	actuators.NewHandler(actuatorService, clock).Register(authed)
	commands.NewHandler(commandService).Register(authed)
	aiHandler.RegisterAuthed(authed)
	overview.NewHandler(overviewService).Register(authed)
	virtualdevices.NewHandler(virtualDeviceService).Register(authed)
	channels.NewHandler(channelService, cropChannelService).Register(authed)
	connections.NewHandler(connectionService).Register(authed)

	// Completa el tipo, la forma y el lugar de los cultivos anteriores (idempotente).
	runCropBackfill(logger, vdRepo, cropService)

	// Trabajos en segundo plano (sin cron en el servidor).
	jobsCtx, stopJobs := context.WithCancel(context.Background())
	defer stopJobs()
	readingService.StartBatchWorker(jobsCtx)
	go tick(jobsCtx, time.Minute, cacheStore.PurgeExpired)
	go tick(jobsCtx, 30*time.Second, func() { commandService.ExpireStale(context.Background()) })
	go tick(jobsCtx, cfg.AI.LearningFlush, func() { learningFeed.Flush(context.Background()) })
	go tick(jobsCtx, cfg.Simulator.ReconcileInterval, func() { virtualDeviceService.Reconcile(context.Background()) })
	go tick(jobsCtx, cfg.Channels.DigestInterval, func() { digest.Run(context.Background()) })
	// Limpieza periódica (reemplaza los índices TTL de Mongo): tokens vencidos, notificaciones a 90 días y
	// comandos a 180 días. Las lecturas las purga la política de retención de TimescaleDB en la propia base.
	go tick(jobsCtx, time.Hour, func() {
		ctx := context.Background()
		now := clock()
		if _, err := tokenRepo.DeleteExpired(ctx, now); err != nil {
			logger.Warn("could not purge expired tokens", "err", err)
		}
		if _, err := notificationRepo.DeleteOlderThan(ctx, now.Add(-90*24*time.Hour)); err != nil {
			logger.Warn("could not purge old notifications", "err", err)
		}
		if _, err := commandRepo.DeleteOlderThan(ctx, now.Add(-180*24*time.Hour)); err != nil {
			logger.Warn("could not purge old commands", "err", err)
		}
	})

	// Recepción de mensajes del bot de Telegram (sondeo largo o webhook).
	telegramListener.Start()

	srv := &http.Server{
		Addr:              fmt.Sprintf(":%d", cfg.Server.Port),
		Handler:           engine,
		ReadHeaderTimeout: 10 * time.Second,
	}

	serverErr := make(chan error, 1)
	go func() {
		logger.Info("API listening", "addr", srv.Addr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			serverErr <- err
		}
	}()

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	select {
	case err := <-serverErr:
		return fmt.Errorf("HTTP server failed: %w", err)
	case <-ctx.Done():
		logger.Info("shutting down (signal received)")
	}

	// 1) Cerrar los flujos SSE activos: si no, srv.Shutdown espera hasta 20 s
	// y Docker puede enviar SIGKILL a los 10 s (H-17).
	readingService.CloseStreams()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("server shutdown error", "err", err)
	}
	// 2) Dejar de aceptar MQTT, drenar los shards y recién entonces desconectar.
	gateway.Close()
	// 3) Vaciar la cola de lecturas por lotes con la base todavía disponible.
	readingService.StopBatchWorker()
	stopJobs()
	telegramListener.Stop()
	_ = aiClient.Close()
	if err := cacheStore.Close(); err != nil {
		logger.Error("cache close error", "err", err)
	}
	pool.Close()
	logger.Info("shutdown complete")
	return nil
}

// runCropBackfill completa el tipo, la forma y el lugar de los cultivos
// anteriores a esos campos, usando las simulaciones para saber cuáles eran virtuales.
func runCropBackfill(logger *slog.Logger, vdRepo virtualdevices.Repository, cropService *crops.Service) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	vds, err := vdRepo.FindAll(ctx)
	if err != nil {
		logger.Warn("could not backfill legacy crops", "err", err)
		return
	}
	virtualCropIDs := make([]string, 0, len(vds))
	var placements []crops.PlacementBackfill
	for i := range vds {
		vd := &vds[i]
		cropID := vd.CropID.Hex()
		virtualCropIDs = append(virtualCropIDs, cropID)
		if vd.Location == nil {
			continue
		}
		placement := geo.Placement{Location: &geo.Location{Name: vd.Location.Name, Latitude: vd.Location.Latitude, Longitude: vd.Location.Longitude}}
		if vd.Mode == virtualdevices.ModeWeather {
			setting, exposure := geo.Outdoor, geo.FullSun
			placement.Setting = &setting
			placement.Exposure = &exposure
		}
		placements = append(placements, crops.PlacementBackfill{CropID: cropID, Placement: placement})
	}
	if err := cropService.Backfill(ctx, virtualCropIDs, placements); err != nil {
		logger.Warn("could not backfill legacy crops", "err", err)
	}
}

// tick ejecuta fn cada intervalo hasta que se cancela el contexto. Reemplaza a
// los @Scheduled de Spring sin usar cron en el servidor.
func tick(ctx context.Context, interval time.Duration, fn func()) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			fn()
		}
	}
}
