# SmartPot-API

[English](#english) | [Español](#español)

<a id="english"></a>
## Project Status

[![Go CI](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/ci.yml/badge.svg)](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/ci.yml)
[![CodeQL](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/codeql.yml/badge.svg)](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/codeql.yml)
[![Publish Docker Images](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/packaging.yml/badge.svg)](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/packaging.yml)

## Description

SmartPot-API is the core of **SmartPot** rewritten in **Go 1.26** with **Echo**: a REST and MQTT API that connects hydroponic crops, real or virtual, with the web application. It receives telemetry via **MQTT**, persists it in **PostgreSQL + TimescaleDB**, sends commands to actuators, manages device credentials on the broker, and queries the **AI assistant** to diagnose each crop and, when permitted by the user, act autonomously.

It provides a **seamless** migration from the Spring Boot version: it preserves all external contracts — REST routes, JSON payloads, error handling, MQTT topics and broker provisioning, cache keys, SMTP, and HTTP calls to AI, simulator, and Telegram — ensuring the web client, broker, hardware devices, AI, and simulator operate **without disruption**. The database is an internal detail behind a port: the API is the control plane and the persistence engine (**PostgreSQL + TimescaleDB**) can be swapped without touching the business domain. The cache follows the same pattern: behind the `Cache` port is an adapter (**Valkey**, the open-source Redis replacement) that can be swapped without altering services. Existing JWT tokens and device keys remain valid (same HS256 secret, cost-12 BCrypt, and AES-256-GCM). Internally it leverages Go concurrency: the MQTT client, Telegram bot, scheduled workers, and in-memory event bus run in parallel without thread pools or cron daemons.

```mermaid
flowchart LR
  web["SmartPot-Web<br/>PWA"] -->|"REST + JWT"| api["SmartPot-API<br/>:8091"]
  esp["Real crop<br/>ESP32 or Wokwi"] -->|"MQTT"| broker["SmartPot-Broker"]
  broker <-->|"telemetry · commands · ACK"| api
  api --> db[("PostgreSQL<br/>+ TimescaleDB")]
  api --> cache[("Valkey")]
  api --> mail["Mailpit / SMTP"]
  api -->|"evaluation · continual learning"| ai["SmartPot-AI"]
  api -->|"virtual crops & weather"| sim["SmartPot-DataGenerator"]
  sim -->|"MQTT"| broker
  api -->|"notifications & digests"| tg["Telegram"]

  classDef core fill:#067A52,stroke:#06281C,color:#fff;
  classDef ext fill:#DDF5EA,stroke:#067A52,color:#17261F;
  class api core;
  class web,esp,broker,db,cache,mail,ai,sim,tg ext;
```

## Project Structure

Code is organized inside `internal/`, one package per domain. Each domain packages `handler.go`, `service.go`, `model.go`, `dto.go`, and `port.go` — the `Repository` interface required by the service from the data layer; implementations reside separately in `internal/storage/postgres`. Thus domain logic remains unaware of database specifics (hexagonal architecture / ports and adapters). The cache layer follows the same standard: services depend on `cache.Cache` and external access is handled via `cache.Backend` implemented in `internal/cache/valkey`. `cmd/api` serves as the entry point wiring components and launching the HTTP/MQTT listeners.

```text
cmd/api/                 # Entry point: configuration loading, dependency wiring, and runtime bootstrap
internal/
├── config/              # Environment loading and ISO-8601 duration parser (PT5M, P7D)
├── httpserver/          # Echo engine, typed RequestContext (sync.Pool), decoupled validator, centralized HTTPErrorHandler
├── security/            # JWT HS256, BCrypt, AES-GCM, CORS, rate limiter, and typed guards
├── cache/               # Cache + Backend ports and resilient in-memory fallback store
├── cache/valkey/        # Valkey (open Redis replacement) Backend adapter via go-redis
├── storage/postgres/    # Repository adapters for PostgreSQL + TimescaleDB (pool and SQL)
├── mqtt/                # Paho client, v1 topics, telemetry parser, and Mosquitto/EMQX provisioning
├── mail/                # Welcome and password reset notifications
├── openapi/             # OpenAPI 3 document at /v3/api-docs and Swagger UI at /docs
├── platform/            # Cross-cutting concerns: event bus, validation, ObjectId, geo, and logging
├── actuators/           # Pump, UV light, fan, humidifier, and doser management
├── ai/                  # AI service client, health evaluation, autonomous agent, and continual learning
├── auth/                # Registration, authentication, and password recovery
├── channels/            # External notification channels (Telegram) and interactive bot
├── commands/            # MQTT commands: PENDING → SENT → EXECUTED | FAILED | EXPIRED
├── crops/               # Real and virtual crops, cultivation form, automation mode, and credentials
├── health/              # /health probes for orchestrators and load balancers
├── notifications/       # Crop alerts, device status, and assistant activity notices
├── overview/            # Dashboard: aggregate statistics, comparative time series, and fleet analysis
├── readings/            # Telemetry readings, statistical summaries, and CSV export
├── users/               # Account profile, password updates, and account deletion
└── virtualdevices/      # Virtual crop simulation: configuration, pausing, and simulator proxy
```

Key HTTP layer architecture decisions (`internal/httpserver` & `internal/security`):

- **Compile-time Typed Context (`*httpserver.RequestContext`).** Each HTTP request wraps the raw context in a pooled `RequestContext` (`sync.Pool`) providing typed fields for identity (`Identity`) and tracing (`TelemetryTrace`), eliminating stringly typed lookups (`c.Get`/`c.Set`).
- **Explicit Error Returns and Centralized `HTTPErrorHandler`.** Handlers return idiomatic `error` (`*apperr.Error` or `*apperr.ValidationError`), processed centrally into standardized JSON responses.
- **Linear Middleware Composition.** Functional pipeline `func(next echo.HandlerFunc) echo.HandlerFunc` for logging, panic recovery, security headers, CORS, rate limiting, and auth guards.
- **Zero-Allocation Radix Router.** Route resolution and parameter extraction run on Echo's radix tree without dynamic heap allocations.
- **Interchangeable Validation (`echo.Validator`).** Decoupled validation interface delegating to `platform/validation`.

## REST Contract

Local base `http://localhost:8091`, production `https://api.smartpot.app`. Business routes reside under `/api/v1` and require `Authorization: Bearer <token>`. Interactive Swagger UI is hosted at `/docs` and the OpenAPI spec at `/v3/api-docs`.

| Method          | Route                                                                                                                    | Access             |
|-----------------|--------------------------------------------------------------------------------------------------------------------------|--------------------|
| GET             | `/health` → `{"status","database","broker","cache","ai","simulator"}` (503 on database down)                              | Public             |
| POST            | `/api/v1/auth/register` `{name,lastName,email,password}` → `{token,expiresAt,user}`                                      | Public             |
| POST            | `/api/v1/auth/login` `{email,password}`                                                                                  | Public             |
| POST            | `/api/v1/auth/password/forgot` `{email}` → 202                                                                           | Public             |
| POST            | `/api/v1/auth/password/reset` `{token,password}` → 204                                                                   | Public             |
| GET             | `/api/v1/crop-profiles` (optimal ranges per crop species)                                                                | Public             |
| GET PUT DELETE  | `/api/v1/users/me` (PUT `{name,lastName}`)                                                                               | JWT                |
| PUT             | `/api/v1/users/me/password` `{currentPassword,newPassword}`                                                              | JWT                |
| GET POST        | `/api/v1/crops` (POST `{name,type,kind,form,placement?,virtual?}`; returns device key once for real crops)              | JWT                |
| PUT             | `/api/v1/crops/automation` `{cropIds?,enabled}` (bulk automation toggle)                                                 | JWT                |
| GET PUT DELETE  | `/api/v1/crops/{id}` (PUT `{name,type,form?,placement?}`)                                                                | JWT, owner only    |
| PUT             | `/api/v1/crops/{id}/automation` `{enabled}`                                                                              | JWT, owner only    |
| GET             | `/api/v1/crops/{id}/weather` (location weather; 204 if unlocated or unavailable)                                          | JWT, owner only    |
| GET             | `/api/v1/crops/{id}/device` (broker host, username, and topic prefixes; 204 for virtual crops)                           | JWT, owner only    |
| POST            | `/api/v1/crops/{id}/device/key` (rotates key and disconnects active broker session; real crops only)                      | JWT, owner only    |
| GET POST        | `/api/v1/crops/{id}/readings` (`from`, `to`, `limit`)                                                                    | JWT, owner only    |
| GET             | `/api/v1/crops/{id}/readings/latest`, `/summary?hours=24`, `/export` (CSV)                                               | JWT, owner only    |
| GET POST DELETE | `/api/v1/crops/{id}/actuators`, `/actuators/{actuatorId}`                                                                | JWT, owner only    |
| GET POST        | `/api/v1/crops/{id}/commands` (POST `{actuatorId,action,durationSeconds}` → 202; 409 on state conflict)                    | JWT, owner only    |
| GET             | `/api/v1/crops/{id}/insights` (AI assistant diagnosis and predictions)                                                   | JWT, owner only    |
| GET             | `/api/v1/overview` (account totals and crops with latest telemetry)                                                      | JWT                |
| GET             | `/api/v1/overview/series?metric=temperature&hours=24` (comparative interval averages)                                    | JWT                |
| GET             | `/api/v1/overview/fleet` (fleet ranking, shared issues, clusters, and bulk recommendations)                              | JWT                |
| GET             | `/api/v1/commands?limit=50` (command history across all crops)                                                           | JWT                |
| POST            | `/api/v1/commands/bulk` `{cropIds?,actuatorType,action,durationSeconds}` → 202 per crop status (`SENT`, `SKIPPED`, `FAILED`) | JWT            |
| GET             | `/api/v1/notifications` (`unreadOnly`, `limit`), `/unread-count`                                                         | JWT                |
| PUT DELETE      | `/api/v1/notifications/{id}/read`, `/read-all`, `/{id}`                                                                  | JWT                |
| GET             | `/api/v1/channels` (configured notification channels and links)                                                          | JWT                |
| POST            | `/api/v1/channels/telegram/link` → `{code,url,expiresAt}` (single-use linking code, 10 minutes)                          | JWT                |
| PUT POST DELETE | `/api/v1/channels/links/{id}` `{enabled?,events?}`, `/links/{id}/test`, `/links/{id}`                                    | JWT, owner only    |
| POST            | `/api/v1/channels/telegram/webhook` (webhook mode, authenticated with `X-Telegram-Bot-Api-Secret-Token`)                 | Telegram           |
| GET PUT         | `/api/v1/crops/{id}/channels`, `/channels/{type}` `{enabled?,events?,delivery?,digestHours?,dailySummaryAt?}`            | JWT, owner only    |
| POST DELETE     | `/api/v1/crops/{id}/channels/{type}/recipients` → `{code,url,expiresAt}` (chat sharing link), `/recipients/{recipientId}`| JWT, owner only |
| GET PUT DELETE  | `/api/v1/crops/{id}/virtual-device` (virtual crops only; PUT updates mode, DELETE pauses)                                 | JWT, owner only    |
| GET             | `/api/v1/virtual-devices/places?q=` (weather location search)                                                            | JWT                |
| GET             | `/api/v1/ai/learning` (continual learning model status: aggregated species metrics)                                      | JWT                |

- Supported Species: `TOMATO`, `LETTUCE`, `STRAWBERRY`, `BASIL`, `SPINACH`, `PEPPER`.
- Crop Kind: `REAL` or `VIRTUAL`, immutable upon creation (`REAL` by default; 400 on modification attempt).
- Cultivation Form: `POT`, `NFT`, `TOWER`, `RAFT` (`POT` by default; updates UI illustration).
- Placement: `setting` `INDOOR` or `OUTDOOR`, `exposure` `FULL_SUN`, `PARTIAL_SUN`, or `SHADE`, plus location.
- Actuator Types: `WATER_PUMP`, `UV_LIGHT`, `FAN`, `HUMIDIFIER`, `NUTRIENT_DOSER`, `PH_DOSER`. Real crops initialize with pump, UV light, and fan; virtual crops initialize with all six.
- Password Policy: 8 characters to 72 bytes requiring uppercase, lowercase, and numbers (BCrypt cost 12).
- Privacy: Queries on crops belonging to other accounts return **404**, preventing ID enumeration.
- Rate Limits: 300 requests/min per IP, 10 requests/min for `/api/v1/auth/*` (429 with `Retry-After`).

## MQTT

The message broker is [SmartPot-Broker-EMQX](https://github.com/SmartPotTech/SmartPot-Broker-EMQX), delegating device authentication to the API (`POST /internal/mqtt/authn` using shared secret `MQTT_AUTHN_TOKEN`). The API validates the crop device key (AES-256-GCM encrypted) and returns an ACL constrained to its specific topics. The verification cache stores an HMAC of the key. Devices connect with **username = crop ID** and **password = device key**; rotating or deleting keys terminates active EMQX sessions.

| Topic                               | Direction                                | Payload                                                                                                        |
|-------------------------------------|------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `smartpot/v1/{cropId}/telemetry`    | Device → API                             | `{"temperature":24.5,"humidity":61,"brightness":710,"ph":6.1,"tds":820,"atmosphere":1012.8,"soilMoisture":55}` |
| `smartpot/v1/{cropId}/commands`     | API → Device                             | `{"id":"…","actuator":"WATER_PUMP","action":"ACTIVATE","durationSeconds":30}`                                  |
| `smartpot/v1/{cropId}/commands/ack` | Device → API                             | `{"id":"…","status":"EXECUTED","message":"Water pump active for 15 s"}`                                        |
| `smartpot/v1/{cropId}/status`       | Device (retained / LWT)                  | `online` / `offline`                                                                                           |

Telemetry can also arrive as **compact binary v2 packets** (27 bytes, same topic) containing sensor presence bitmasks, 32-bit light, and pressure fields (`internal/mqtt/parser.go`). Telemetry outside valid physical ranges is dropped; each crop records at most one sample per 5 seconds. Commands without an ACK within 2 minutes transition to `EXPIRED`.

## AI Assistant

Incoming readings trigger the autonomous agent, evaluating crops every 30 seconds when automation is enabled (every 5 minutes otherwise). Readings are forwarded to [SmartPot-AI](https://github.com/SmartPotTech/SmartPot-AI) alongside historical series and actuator states. The AI service responds with a fuzzy health index, expert rule diagnoses, learned model predictions, and recommended actions. The API persists health status, issues notifications for critical conditions, and under **automation mode** executes actions with a 10-minute actuator cooldown.

Readings from real crops are buffered for **continual learning**: every minute the API delivers batches to the AI engine (`/v1/learning/readings`) with local hour context. Fleet insights aggregate time-series data with TimescaleDB `time_bucket` to generate cross-crop rankings and collective actions.

## Telegram Notifications

PWA alerts and notifications are forwarded to linked **external channels**. Channels implement `NotificationChannel`, allowing modular addition of Slack or WhatsApp integrations. Currently Telegram is supported natively:

1. The PWA requests a linking token (`POST /api/v1/channels/telegram/link`) and opens `https://t.me/<bot>?start=<code>`.
2. The bot parses `/start <code>`, validates the one-time code, and associates the chat with the user's account.
3. Interactive `/status` lists crops, and `/unlink` detaches the chat.

In development `TELEGRAM_MODE=polling` runs long-polling; production uses `webhook` with signature verification.

## AI Assistant Connections (MCP)

Users can generate personal connection tokens (`spt_...`) from the PWA (Profile › Connections) to interface with external AI agents such as [SmartPot-MCP](https://github.com/SmartPotTech/SmartPot-MCP): `GET/POST /api/v1/connections` and `DELETE /api/v1/connections/{id}`. Tokens are revealed once upon creation; hashes are stored securely.

| Scope     | Permissions                                                |
|-----------|------------------------------------------------------------|
| `READ`    | Read-only queries (`GET`)                                  |
| `CONTROL` | Create, update, delete crops and trigger actuators         |

Connection tokens are strictly blocked from changing user account settings, managing other tokens, viewing device credentials, or accessing admin operations.

## Real and Virtual Crops

Crops specify their data origin upon creation:
- **Real Crops** generate a unique device key for ESP32 firmware running on hardware or simulated via Wokwi.
- **Virtual Crops** require no hardware: [SmartPot-DataGenerator](https://github.com/SmartPotTech/SmartPot-DataGenerator) executes continuous simulations using internal credentials. Virtual crops can be paused, resumed, and configured with manual or weather-based environmental profiles (up to 5 virtual crops per user).

## Installation Guide

### One-Command Deployment

The container image is publicly available at `ghcr.io/smartpottech/smartpot-api`. The full platform can be started with a single command without downloading source code:

```bash
curl -fsSL https://raw.githubusercontent.com/SmartPotTech/.github/main/docker/demo-beta/compose.yaml | docker compose -f - up -d --wait
```

Access http://localhost:5173 with credentials `demo@smartpot.app` / `SmartPot2026`. Stop with `docker compose -p smartpot-demo-beta down` (add `-v` to wipe data). See the [demo beta guide](https://github.com/SmartPotTech/.github/tree/main/docker/demo-beta).

### Prerequisites

- Go 1.26 (or Docker)
- PostgreSQL + TimescaleDB ([SmartPot-DB-Postgre](https://github.com/SmartPotTech/SmartPot-DB-Postgre))
- Valkey ([SmartPot-Cache-Valkey](https://github.com/SmartPotTech/SmartPot-Cache-Valkey))
- Mailpit, MQTT broker, and AI service: complete stack available in [SmartPotTech/.github](https://github.com/SmartPotTech/.github)

### Development

```bash
cp .env.example .env    # Configure secrets and connection parameters
go run ./cmd/api
```

`SMARTPOT_JWT_SECRET` (at least 32 characters) and `SMARTPOT_AES_KEY` (32-byte Base64 key) are mandatory.

### Testing

```bash
go test ./...
go test -race ./...   # requires CGO and a C compiler
```

The test suite covers encryption/decryption parity, JWT handling and revocation, password policies, telemetry ranges, MQTT topics, retention and provisioning, the conditional command state machine, account quotas, actuator switches (409 conflict detection), agricultural thresholds, agent rules, continual learning pipelines, Telegram bot handlers, OpenAPI schemas, and caching behaviors.

The PostgreSQL adapters also have opt-in integration tests against a real TimescaleDB:

```bash
SMARTPOT_TEST_DATABASE_URL='postgres://user:pass@localhost:5432/smartpot?sslmode=disable' \
  go test -tags=integration ./internal/storage/postgres
```

### Docker Image

```bash
docker build -t smartpot-api .
docker pull ghcr.io/smartpottech/smartpot-api:latest
```

The Dockerfile builds a statically linked binary copied into a minimal `scratch` image (no shell, package manager, or network binaries). Runs under UID 1000 with a read-only root filesystem and embedded `healthcheck` command.

## Documentation

- OpenAPI interactive Swagger UI: `/docs`
- OpenAPI JSON specification: `/v3/api-docs`
- Platform architecture & protocol docs: [Platform Documentation](https://github.com/SmartPotTech/.github/blob/main/docs/SmartPot_Technical_Documentation.md)

Global platform diagrams:
- [Complete Architecture](https://github.com/SmartPotTech/.github/blob/main/docs/diagrams/SmartPot_Global_01_Architecture.svg)
- [Operational Sequence](https://github.com/SmartPotTech/.github/blob/main/docs/diagrams/SmartPot_Global_02_Operation_Sequence.svg)
- [State Machines](https://github.com/SmartPotTech/.github/blob/main/docs/diagrams/SmartPot_Global_05_State_Machines.svg)
- [Domain Model](https://github.com/SmartPotTech/.github/blob/main/docs/diagrams/SmartPot_Global_06_Domain_Model.svg)

## Transport and Performance

- **gRPC AI Service with REST Fallback.** Configured via `AI_GRPC_SOCKET` (UNIX domain socket) or `AI_GRPC_ADDR` (HTTP/2 internal TCP). Falls back seamlessly to REST HTTP/1.1 if gRPC is unavailable.
- **Server-Sent Events (SSE).** `GET /api/v1/crops/{cropId}/stream` delivers real-time telemetry events (`X-Accel-Buffering: no` compatible).
- **Batch Telemetry Ingestion.** MQTT telemetry is ingested in batches of up to 100 readings or every 10 ms using PostgreSQL binary `COPY`.
- **Deadband Filtering.** The autonomous agent avoids redundant AI evaluations if environmental variations remain below deadband thresholds (0.1 °C, 0.4% RH, 0.03 pH, 15 ppm, 0.5% moisture, 0.5 hPa, 5% light).
- **Reverse Proxy Support.** When `TRUST_PROXY=true` (or the production alias `SERVER_TRUST_PROXY`), client IP addresses are resolved from `X-Forwarded-For` strictly against `TRUSTED_PROXY_CIDRS` (alias `SERVER_TRUSTED_PROXIES`).

## Reliability and Concurrency Guarantees

- **Atomic command state machine.** Transitions use conditional `UPDATE ... RETURNING`; a fast ACK is never overwritten by dispatch or expiration. The partial unique index `commands_one_inflight_per_actuator` is the authority and maps to `409 Conflict`.
- **Atomic account quotas.** Crop creation takes `pg_advisory_xact_lock` per owner and counts total and virtual crops inside the same transaction (20 total, 5 virtual).
- **Batch latest readings.** `/crops`, `/overview`, and fleet analysis resolve every crop's latest reading with one `DISTINCT ON` query plus Valkey, removing the N+1.
- **Ordered MQTT shutdown.** SIGTERM closes SSE streams first, stops accepting messages, drains the shard queues and the readings batch queue, and only then disconnects from the broker. Retained telemetry and ACKs are discarded; retained status (LWT) is processed.
- **AI resilience.** The circuit breaker opens after 5 consecutive failures, gRPC and REST share one operation budget, gRPC has a 1.5 s deadline with immediate REST fallback, and singleflight is panic-safe and keyed by reading and crop version.
- **Session security.** JWTs carry a `jti` and a per-user version; logout revokes the current token and password changes/resets invalidate every previous token. MQTT authentication requires the exact lowercase crop ID and a crop-bound client ID.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

---

<a id="español"></a>
## Estado del Proyecto

[![Go CI](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/ci.yml/badge.svg)](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/ci.yml)
[![CodeQL](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/codeql.yml/badge.svg)](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/codeql.yml)
[![Publish Docker Images](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/packaging.yml/badge.svg)](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/packaging.yml)

## Descripción

SmartPot-API es el núcleo de **SmartPot** reescrito en **Go 1.26** con **Echo**: una API REST y MQTT que conecta los
cultivos hidropónicos, reales o virtuales, con la aplicación web. Recibe la telemetría por **MQTT**, la guarda en
**PostgreSQL + TimescaleDB**, envía comandos a los actuadores, administra las credenciales de cada dispositivo en el
broker y consulta al **asistente de IA** para diagnosticar cada cultivo y, si el usuario lo permite, actuar por su
cuenta.

Es una migración **transparente** de la versión en Spring Boot: reproduce idénticos todos los contratos externos —rutas
REST, formas JSON, códigos y mensajes en español, tópicos MQTT y aprovisionamiento en Mosquitto, claves de la caché, SMTP y
las llamadas HTTP a la IA, al simulador y a Telegram—, de modo que la web, el broker, los dispositivos, la IA y el
simulador siguen funcionando **sin cambios**. La base de datos es un detalle interno detrás de un puerto: la API es el
centro de control y el motor (hoy **PostgreSQL + TimescaleDB**, antes MongoDB) se puede cambiar sin tocar el dominio.
La caché sigue el mismo patrón: detrás del puerto `Cache` hay un adaptador (hoy **Valkey**, el reemplazo libre de
Redis) que se puede cambiar sin tocar los servicios.
Los tokens JWT y las claves de dispositivo ya
emitidos siguen siendo válidos (mismo secreto HS256, BCrypt de costo 12 y AES-256-GCM). Por dentro aprovecha las
goroutines: el cliente MQTT, el bot de Telegram, los trabajos periódicos y el bus de eventos corren en paralelo sin
hilos ni cron en el servidor.

```mermaid
flowchart LR
  web["SmartPot-Web<br/>PWA"] -->|"REST + JWT"| api["SmartPot-API<br/>:8091"]
  esp["Cultivo real<br/>ESP32 o Wokwi"] -->|"MQTT"| broker["SmartPot-Broker"]
  broker <-->|"telemetría · comandos · ACK"| api
  api --> db[("PostgreSQL<br/>+ TimescaleDB")]
  api --> cache[("Valkey")]
  api --> mail["Mailpit / SMTP"]
  api -->|"evaluación · lecturas para aprender"| ai["SmartPot-AI"]
  api -->|"cultivos virtuales y clima"| sim["SmartPot-DataGenerator"]
  sim -->|"MQTT"| broker
  api -->|"avisos y resúmenes"| tg["Telegram"]

  classDef core fill:#067A52,stroke:#06281C,color:#fff;
  classDef ext fill:#DDF5EA,stroke:#067A52,color:#17261F;
  class api core;
  class web,esp,broker,db,cache,mail,ai,sim,tg ext;
```

## Estructura del Proyecto

El código vive en `internal/`, un paquete por dominio (los mismos de la versión en Java, para rastrear la
equivalencia). Cada dominio reúne `handler.go`, `service.go`, `model.go`, `dto.go` y `port.go` —la interfaz
`Repository` que el servicio necesita de la capa de datos—; las implementaciones de esos puertos sobre PostgreSQL
viven aparte, en `internal/storage/postgres`. Así el dominio no conoce el motor de base de datos (inversión de
dependencias / puertos y adaptadores). La caché aplica el mismo patrón: los servicios dependen del puerto `cache.Cache`
y el acceso al sistema remoto está detrás del puerto de salida `cache.Backend`, cuyo adaptador vive en
`internal/cache/valkey`; cambiar de sistema de caché es solo otro adaptador. `cmd/api` es el punto de entrada que
cablea todo y arranca el servidor.

```text
cmd/api/                 # Punto de entrada: carga la config, cablea los componentes y arranca
internal/
├── config/              # Carga del entorno y parser de duraciones ISO-8601 (PT5M, P7D)
├── httpserver/          # Motor Echo, RequestContext tipado (sync.Pool), validador desacoplado y HTTPErrorHandler central
├── security/            # JWT HS256, BCrypt, AES-GCM, CORS, límite de peticiones y guardas tipadas
├── cache/               # Puerto Cache + Backend y almacén resiliente con respaldo en memoria (contadores y enfriamientos)
├── cache/valkey/        # Adaptador del puerto Backend sobre Valkey (reemplazo libre de Redis) vía go-redis
├── storage/postgres/    # Adaptadores de los puertos Repository sobre PostgreSQL + TimescaleDB (pool y SQL)
├── mqtt/                # Cliente Paho, tópicos v1, parser de telemetría y aprovisionamiento en Mosquitto
├── mail/                # Correos de bienvenida y de recuperación de contraseña
├── openapi/             # Documento OpenAPI en /v3/api-docs e interfaz de Swagger en /docs
├── platform/            # Piezas transversales: bus de eventos, validación, ObjectId, geo y logging
├── actuators/           # Bomba, luz ultravioleta, ventilador y demás salidas de cada cultivo
├── ai/                  # Cliente del servicio de IA, evaluación de salud, agente y aprendizaje continuo
├── auth/                # Registro, inicio de sesión y recuperación de contraseña
├── channels/            # Canales externos de notificación (hoy Telegram) y su bot
├── commands/            # Comandos por MQTT: PENDING → SENT → EXECUTED | FAILED | EXPIRED
├── crops/               # Cultivos reales o virtuales, su forma, modo automático y credenciales
├── health/              # /health para despliegues y balanceadores
├── notifications/       # Alertas del cultivo, del dispositivo y del asistente
├── overview/            # Panel general: totales, series comparativas y análisis de flota de la IA
├── readings/            # Lecturas, resumen estadístico y exportación CSV
├── users/               # Perfil, cambio de contraseña y borrado de cuenta
└── virtualdevices/      # Simulación de los cultivos virtuales: configuración, pausa y proxy al simulador
```

Spring aporta beans, `@Scheduled`, eventos y validación declarativa; aquí eso se traduce en: cableado explícito en
`cmd/api`, goroutines con `time.Ticker` canceladas por `context` al apagar, un bus de eventos en proceso
(`platform/events`) y un validador propio con los mismos mensajes en español.

La capa HTTP (`internal/httpserver` e `internal/security`) se apoya en cinco decisiones de diseño propias de Echo:

- **Contexto tipado en tiempo de compilación (`*httpserver.RequestContext`).** Como `echo.Context` es una interfaz desacoplada, cada petición envuelve el contexto base en un `RequestContext` reciclado mediante `sync.Pool` que expone campos tipados para la identidad del usuario, las credenciales JWT (`Identity`) y la traza de telemetría (`TelemetryTrace`), sin mapas de cadenas (`c.Set`/`c.Get`) ni conversiones de tipo en tiempo de ejecución.
- **Retorno explícito de error y `HTTPErrorHandler` centralizado.** Todos los manejadores usan la firma idiomática `func(echo.Context) error` y devuelven directamente los errores de dominio (`*apperr.Error`) o de validación (`*apperr.ValidationError`) para que el `HTTPErrorHandler` central los transforme en respuestas JSON homogéneas sin riesgo de ejecuciones residuales.
- **Composición lineal de middlewares.** La tubería HTTP (`requestLogger`, `recoverer`, cabeceras de seguridad, CORS, límite de tasa y `Guard`) se construye por composición funcional `func(next echo.HandlerFunc) echo.HandlerFunc`.
- **Árbol Radix *zero-allocation*.** La resolución de rutas y la extracción de parámetros (`c.Param`) operan sobre el árbol Radix de Echo sin asignaciones dinámicas en el *heap* (`0 allocs/op` verificado en pruebas), aplanando el impacto del recolector de basura bajo concurrencia continua.
- **Validación intercambiable (`echo.Validator`).** `httpserver.Bind` delega en `c.Validate(req)` a través de la interfaz `echo.Validator`, manteniendo las reglas de `platform/validation` desacopladas del transporte y sin dependencias externas de reflexión.

## Contrato REST

Base local `http://localhost:8091`, producción `https://api.smartpot.app`. Las rutas de negocio viven bajo `/api/v1` y
usan `Authorization: Bearer <token>`. La documentación interactiva está en `/docs` y el esquema en `/v3/api-docs`.

| Método          | Ruta                                                                                                                                                     | Acceso             |
|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------|
| GET             | `/health` → `{"status","database","broker","cache","ai","simulator"}` (503 si la base cae)                                                               | Público            |
| POST            | `/api/v1/auth/register` `{name,lastName,email,password}` → `{token,expiresAt,user}`                                                                      | Público            |
| POST            | `/api/v1/auth/login` `{email,password}`                                                                                                                  | Público            |
| POST            | `/api/v1/auth/password/forgot` `{email}` → 202 siempre                                                                                                   | Público            |
| POST            | `/api/v1/auth/password/reset` `{token,password}` → 204                                                                                                   | Público            |
| GET             | `/api/v1/crop-profiles` (rangos óptimos por especie)                                                                                                     | Público            |
| GET PUT DELETE  | `/api/v1/users/me` (PUT `{name,lastName}`)                                                                                                               | JWT                |
| PUT             | `/api/v1/users/me/password` `{currentPassword,newPassword}`                                                                                              | JWT                |
| GET POST        | `/api/v1/crops` (POST `{name,type,kind,form,placement?,virtual?}`; si es real devuelve la clave del dispositivo una sola vez)                            | JWT                |
| PUT             | `/api/v1/crops/automation` `{cropIds?,enabled}` (modo automático en varios cultivos; sin ids, en todos)                                                  | JWT                |
| GET PUT DELETE  | `/api/v1/crops/{id}` (PUT `{name,type,form?,placement?}`)                                                                                                | JWT, solo el dueño |
| PUT             | `/api/v1/crops/{id}/automation` `{enabled}`                                                                                                              | JWT, solo el dueño |
| GET             | `/api/v1/crops/{id}/weather` (clima del lugar; 204 sin ubicación o sin clima disponible)                                                                 | JWT, solo el dueño |
| GET             | `/api/v1/crops/{id}/device` (broker, usuario y tópicos; 204 si no es real)                                                                               | JWT, solo el dueño |
| POST            | `/api/v1/crops/{id}/device/key` (rota la clave y desconecta la sesión anterior; solo cultivos reales)                                                    | JWT, solo el dueño |
| GET POST        | `/api/v1/crops/{id}/readings` (`from`, `to`, `limit`)                                                                                                    | JWT, solo el dueño |
| GET             | `/api/v1/crops/{id}/readings/latest`, `/summary?hours=24`, `/export` (CSV)                                                                               | JWT, solo el dueño |
| GET POST DELETE | `/api/v1/crops/{id}/actuators`, `/actuators/{actuatorId}`                                                                                                | JWT, solo el dueño |
| GET POST        | `/api/v1/crops/{id}/commands` (POST `{actuatorId,action,durationSeconds}` → 202; 409 si no cambia nada o hay otra orden en curso)                        | JWT, solo el dueño |
| GET             | `/api/v1/crops/{id}/insights` (diagnóstico del asistente de IA con pronósticos)                                                                          | JWT, solo el dueño |
| GET             | `/api/v1/overview` (totales de la cuenta y cada cultivo con su última lectura)                                                                           | JWT                |
| GET             | `/api/v1/overview/series?metric=temperature&hours=24` (promedios por intervalo para comparar cultivos)                                                   | JWT                |
| GET             | `/api/v1/overview/fleet` (ranking, problemas compartidos, grupos y acciones en bloque de la IA)                                                          | JWT                |
| GET             | `/api/v1/commands?limit=50` (comandos de todos los cultivos)                                                                                             | JWT                |
| POST            | `/api/v1/commands/bulk` `{cropIds?,actuatorType,action,durationSeconds}` → 202 con el resultado por cultivo (`SENT`, `SKIPPED` con su motivo o `FAILED`) | JWT                |
| GET             | `/api/v1/notifications` (`unreadOnly`, `limit`), `/unread-count`                                                                                         | JWT                |
| PUT DELETE      | `/api/v1/notifications/{id}/read`, `/read-all`, `/{id}`                                                                                                  | JWT                |
| GET             | `/api/v1/channels` (canales del servidor y mis vínculos)                                                                                                 | JWT                |
| POST            | `/api/v1/channels/telegram/link` → `{code,url,expiresAt}` (código de un solo uso, 10 minutos)                                                            | JWT                |
| PUT POST DELETE | `/api/v1/channels/links/{id}` `{enabled?,events?}`, `/links/{id}/test`, `/links/{id}`                                                                    | JWT, solo el dueño |
| POST            | `/api/v1/channels/telegram/webhook` (modo webhook, firmado con `X-Telegram-Bot-Api-Secret-Token`)                                                        | Telegram           |
| GET PUT         | `/api/v1/crops/{id}/channels`, `/channels/{type}` `{enabled?,events?,delivery?,digestHours?,dailySummaryAt?}` (avisos del cultivo)                       | JWT, solo el dueño |
| POST DELETE     | `/api/v1/crops/{id}/channels/{type}/recipients` → `{code,url,expiresAt}` (compartir con otro chat), `/recipients/{recipientId}`                          | JWT, solo el dueño |
| GET PUT DELETE  | `/api/v1/crops/{id}/virtual-device` (solo cultivos virtuales; PUT `{mode,manual?,location?,intervalSeconds?}` cambia o reanuda, DELETE pausa)            | JWT, solo el dueño |
| GET             | `/api/v1/virtual-devices/places?q=` (lugares para el modo clima)                                                                                         | JWT                |
| GET             | `/api/v1/ai/learning` (qué ha aprendido la IA: solo datos agregados por especie)                                                                         | JWT                |

- Especies: `TOMATO`, `LETTUCE`, `STRAWBERRY`, `BASIL`, `SPINACH`, `PEPPER`.
- Tipo (`kind`): `REAL` o `VIRTUAL`, fijo al crear el cultivo (`REAL` si falta); cambiarlo responde 400.
- Forma (`form`): `POT`, `NFT`, `TOWER` o `RAFT` (`POT` si falta); se puede editar y solo cambia la ilustración de la
  PWA.
- Lugar (`placement`): `setting` `INDOOR` u `OUTDOOR`, `exposure` `FULL_SUN`, `PARTIAL_SUN` o `SHADE` y `location`;
  todo opcional y uno solo para el cultivo y su simulación.
- Actuadores: `WATER_PUMP`, `UV_LIGHT`, `FAN`, `HUMIDIFIER`, `NUTRIENT_DOSER`, `PH_DOSER`. Un cultivo real nace con la
  bomba, la luz ultravioleta y el ventilador; uno virtual, con los seis. Funcionan como switches: admiten una orden a la
  vez, apagar lo apagado o encender sin límite lo encendido responde 409, y cada uno informa `running` y `runningUntil`.
- Contraseñas de 8 caracteres a 72 bytes con mayúscula, minúscula y número (BCrypt de costo 12).
- Un cultivo de otra cuenta responde **404**, igual que uno inexistente, para no revelar qué ids existen.
- Límite por IP: 300 peticiones por minuto y 10 por minuto en `/api/v1/auth/*`. Responde 429 con `Retry-After`.

## MQTT

El broker es [SmartPot-Broker-EMQX](https://github.com/SmartPotTech/SmartPot-Broker-EMQX), que delega la autenticación
de cada dispositivo en la API (`POST /internal/mqtt/authn`, con el secreto compartido `MQTT_AUTHN_TOKEN`): la API
verifica la clave del cultivo (guardada cifrada con AES-256-GCM) y responde un ACL acotado a sus tópicos. La caché de
esa verificación solo guarda un HMAC de la clave. El dispositivo se conecta con **usuario = id del cultivo** y la
**clave del dispositivo**; al rotar o borrar la clave, la API corta sus sesiones en EMQX.

| Tópico                              | Sentido                                  | Carga                                                                                                          |
|-------------------------------------|------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `smartpot/v1/{cropId}/telemetry`    | Dispositivo → API                        | `{"temperature":24.5,"humidity":61,"brightness":710,"ph":6.1,"tds":820,"atmosphere":1012.8,"soilMoisture":55}` |
| `smartpot/v1/{cropId}/commands`     | API → dispositivo                        | `{"id":"…","actuator":"WATER_PUMP","action":"ACTIVATE","durationSeconds":30}`                                  |
| `smartpot/v1/{cropId}/commands/ack` | Dispositivo → API                        | `{"id":"…","status":"EXECUTED","message":"Bomba de agua encendida por 15 s"}`                                  |
| `smartpot/v1/{cropId}/status`       | Dispositivo (retenido y última voluntad) | `online` / `offline`                                                                                           |

La telemetría también puede llegar como **paquete compacto v2** (27 bytes, mismo tópico): máscara de presencia por
sensor, presión y luz de 32 bits; el formato está documentado en `internal/mqtt/parser.go`. Los mensajes de un mismo
cultivo se procesan en orden. La telemetría fuera de rango físico se descarta y cada cultivo guarda como máximo una lectura cada 5 segundos. Un
comando sin ACK en 2 minutos pasa a `EXPIRED` y avisa al dueño.

## Asistente de IA

Cada lectura nueva activa al agente de automatización, que evalúa el cultivo como máximo cada 30 segundos si tiene el
modo automático y cada 5 minutos si no: envía la lectura a [SmartPot-AI](https://github.com/SmartPotTech/SmartPot-AI)
junto con el historial reciente y los actuadores del cultivo. El servicio responde con un índice difuso de salud, el
diagnóstico del sistema experto, las predicciones de los modelos y las acciones sugeridas. La API guarda el índice en el
cultivo, avisa de los hallazgos críticos y, con el **modo automático** activo, ejecuta las acciones con un enfriamiento
de 10 minutos por actuador.

Cada lectura de un cultivo real también se encola para el **aprendizaje continuo** (las de los virtuales son sintéticas
y no se envían): cada minuto la API envía el lote a la IA (`/v1/learning/readings`) con la hora local; si la IA no
responde, el lote espera al siguiente ciclo (la cola guarda hasta 20 000 lecturas). El **panel general**
(`/api/v1/overview`) mira la cuenta completa: las series comparativas se agregan en la hipertabla con `time_bucket` y el
análisis de flota pide a la IA el ranking, los problemas compartidos y las acciones en bloque. La evaluación también
lleva el **lugar** del cultivo y el **clima de afuera**, que el simulador consulta y la API guarda 10 minutos por lugar
en la caché.

## Notificaciones por Telegram

Las notificaciones de la PWA (alertas, desconexiones, acciones del asistente y comandos) se reenvían a los **canales
externos** que la persona vincule y a los tipos que elija. Los canales implementan la interfaz `NotificationChannel`:
sumar WhatsApp, correo o Slack es una nueva implementación, sin tocar el resto. Hoy existe Telegram, centralizado en la
API (el dispositivo no habla con Telegram).

1. La PWA pide un código (`POST /api/v1/channels/telegram/link`) y abre `https://t.me/<bot>?start=<código>`.
2. El bot recibe `/start <código>`, lo valida (un solo uso, 10 minutos) y vincula ese chat a la cuenta.
3. Desde ahí llegan los avisos con un botón para abrir el cultivo; `/estado` resume los cultivos y `/desvincular` corta
   el envío.

Con `TELEGRAM_MODE=polling` la API consulta al bot con sondeo largo (sirve en local y en la demo, sin dirección
pública); en producción `webhook` registra `PUBLIC_API_URL/api/v1/channels/telegram/webhook` y valida el secreto de cada
llamada. Cada cultivo puede afinar sus avisos (`crop_channels`): al instante o en un resumen cada 1 a 24 horas, un
resumen diario a una hora fija y hasta 10 chats con los que se comparte. El preparador de resúmenes revisa cada minuto
los pendientes, sin cron en el servidor.

## Conexiones para asistentes de IA (MCP)

Cada persona crea desde la PWA (Perfil › Conexiones) tokens de conexión `spt_…` para clientes externos, como el
servidor [SmartPot-MCP](https://github.com/SmartPotTech/SmartPot-MCP): `GET/POST /api/v1/connections` y
`DELETE /api/v1/connections/{id}`. El token solo se devuelve al crearlo; la base guarda su SHA-256, un prefijo para
reconocerlo y el último uso. El guard lo acepta igual que la sesión, pero con límites:

| Alcance   | Puede                                                     |
|-----------|-----------------------------------------------------------|
| `READ`    | Solo consultas (`GET`)                                    |
| `CONTROL` | Además crear, editar, borrar y accionar actuadores        |

Ninguna conexión administra conexiones, cambia la cuenta, configura canales de aviso, ve credenciales de
dispositivos ni obtiene el rol de administrador. Revocar o vencer la conexión la corta al instante (401), y borrar la
cuenta borra sus conexiones. Máximo 10 por cuenta.

## Cultivos Reales y Virtuales

Al crear un cultivo se elige, una sola vez, de dónde vienen sus lecturas. Uno **real** recibe la clave del dispositivo
para un ESP32 con el firmware, físico o simulado en Wokwi. Uno **virtual** no entrega
credenciales: [SmartPot-DataGenerator](https://github.com/SmartPotTech/SmartPot-DataGenerator) lo simula siempre
encendido con la cuenta del cultivo, que la API descifra y le entrega por la red interna. La PWA solo habla con la API,
que comprueba el dueño y el tipo del cultivo en cada ruta. La configuración vive en la tabla `virtual_devices`;
pausar la simulación (`DELETE`) la marca `active: false` sin perderla y `PUT` la reanuda. Cada minuto la API reconcilia
con el simulador y vuelve a crear las simulaciones activas que falten. Máximo 5 por cuenta. Al arrancar, un relleno
completa el tipo y la forma de los cultivos anteriores.

## Guía de Instalación

### Despliegue en un comando

La imagen es pública: `ghcr.io/smartpottech/smartpot-api`. La plataforma completa, con este servicio incluido, se
levanta con un solo comando y sin descargar el código:

```bash
curl -fsSL https://raw.githubusercontent.com/SmartPotTech/.github/main/docker/demo-beta/compose.yaml | docker compose -f - up -d --wait
```

Abre http://localhost:5173 e ingresa con `demo@smartpot.app` / `SmartPot2026`. Se detiene con
`docker compose -p smartpot-demo-beta down` (con `-v` borra también los datos). Más detalle en la
[demo beta](https://github.com/SmartPotTech/.github/tree/main/docker/demo-beta).

### Requisitos Previos

- Go 1.26 (o solo Docker)
- PostgreSQL + TimescaleDB ([SmartPot-DB-Postgre](https://github.com/SmartPotTech/SmartPot-DB-Postgre)),
  Valkey ([SmartPot-Cache-Valkey](https://github.com/SmartPotTech/SmartPot-Cache-Valkey)),
  Mailpit, el broker y el servicio de IA: el entorno completo está
  en [SmartPotTech/.github](https://github.com/SmartPotTech/.github)

### Desarrollo

```bash
cp .env.example .env    # completa secretos y conexiones
go run ./cmd/api
```

La API lee `.env` desde la carpeta del proyecto. `SMARTPOT_JWT_SECRET` (al menos 32 caracteres) y `SMARTPOT_AES_KEY`
(32 bytes en Base64, `openssl rand -base64 32`) no tienen valor por defecto: sin ellos la API no arranca.

### Pruebas

```bash
go test ./...
go test -race ./...   # requiere CGO y un compilador de C
```

Cubren el cifrado (incluido descifrar un texto cifrado por la versión en Java), JWT y su revocación, la política de
contraseñas, los rangos de medida, los tópicos, la retención y la telemetría MQTT, el aprovisionamiento en el broker,
la máquina de estados condicional de comandos, las cuotas de cuenta, los switches (409), las reglas de cultivo y clima,
el agente de automatización, el envío de lecturas para el aprendizaje, los canales y los avisos por cultivo, el bot de
Telegram (códigos de un solo uso, webhook firmado, escape de HTML), el documento OpenAPI, la caché con respaldo local
y la seguridad de dueño en los controladores.

Los adaptadores de PostgreSQL tienen además pruebas de integración opcionales contra TimescaleDB real:

```bash
SMARTPOT_TEST_DATABASE_URL='postgres://usuario:clave@localhost:5432/smartpot?sslmode=disable' \
  go test -tags=integration ./internal/storage/postgres
```

### Imagen Docker

```bash
docker build -t smartpot-api .
docker pull ghcr.io/smartpottech/smartpot-api:latest
```

La imagen compila el binario estático en una etapa aparte y lo copia a una imagen final `scratch`: sin shell, sin gestor
de paquetes y sin herramientas de red, así un contenedor comprometido no encuentra nada que aprovechar. Corre como el
usuario `1000`, admite sistema de archivos de solo lectura (no escribe en disco) y el `HEALTHCHECK` lo resuelve el
propio
binario (`smartpot-api healthcheck`), sin `wget` ni `curl`. La zona horaria va incrustada en el binario y solo se suma
el
bundle de certificados CA para las conexiones TLS. Cada cambio en `main` pasa por el CI y publica la imagen en GHCR (y
en
Docker Hub como réplica cuando el repositorio tiene credenciales).

## Documentación

La documentación interactiva se sirve en `/docs` (interfaz de Swagger) y el esquema OpenAPI en `/v3/api-docs`. La
[documentación técnica de la plataforma](https://github.com/SmartPotTech/.github/blob/main/docs/SmartPot_Technical_Documentation.md)
detalla los contratos MQTT y REST, las reglas del agente, los canales y la seguridad. Los diagramas generales muestran
la plataforma completa en una sola imagen ampliable:

- [Arquitectura completa](https://github.com/SmartPotTech/.github/blob/main/docs/diagrams/SmartPot_Global_01_Architecture.svg)
- [Operación completa](https://github.com/SmartPotTech/.github/blob/main/docs/diagrams/SmartPot_Global_02_Operation_Sequence.svg)
- [Máquinas de estado](https://github.com/SmartPotTech/.github/blob/main/docs/diagrams/SmartPot_Global_05_State_Machines.svg)
- [Modelo de dominio](https://github.com/SmartPotTech/.github/blob/main/docs/diagrams/SmartPot_Global_06_Domain_Model.svg)

## Transporte y rendimiento

- **IA por gRPC con respaldo REST.** Con `AI_GRPC_SOCKET` (socket UNIX compartido) o `AI_GRPC_ADDR` (TCP interno),
  la API llama a la IA por HTTP/2 con el mismo JSON del contrato REST y el token en los metadatos; si gRPC falla,
  usa REST por HTTP/1.1, que pasa por cualquier proxy.
- **Lecturas en vivo por SSE.** `GET /api/v1/crops/{cropId}/stream` envía cada lectura validada; funciona con chunked
  y detrás de un nginx que hable HTTP/1.0 con la API (`X-Accel-Buffering: no`). El resto de los GET lleva `ETag` y
  responde `304` si nada cambió.
- **Ingesta por lotes.** La telemetría MQTT se guarda en lotes de hasta 100 lecturas o cada 10 ms con `COPY` binario;
  si un lote falla, se reintenta lectura por lectura. Los eventos (agente, aprendizaje, avisos) salen con la lectura ya
  guardada, y la última lectura de cada cultivo vive en la caché compartida.
- **Banda muerta.** El agente no vuelve a consultar a la IA si ninguna variable cambió más que su umbral desde la
  última evaluación (0,1 °C, 0,4 % HR, 0,03 pH, 15 ppm, 0,5 % de sustrato, 0,5 hPa, 5 % de luz); al menos cada
  15 minutos reevalúa.
- **Detrás de un proxy.** Con `TRUST_PROXY=true` (o el alias de producción `SERVER_TRUST_PROXY`) la IP del cliente
  sale de `X-Forwarded-For` solo si la conexión llega desde `TRUSTED_PROXY_CIDRS` (alias
  `SERVER_TRUSTED_PROXIES`); así el límite de tasa es por cliente.

## Fiabilidad y concurrencia

- **Máquina de estados de comandos atómica.** Las transiciones usan `UPDATE ... RETURNING` condicional; un ACK rápido
  nunca lo pisa el despacho ni la expiración. El índice único parcial `commands_one_inflight_per_actuator` es la
  autoridad y se traduce a `409 Conflict`.
- **Cuotas de cuenta atómicas.** La creación de un cultivo toma `pg_advisory_xact_lock` por dueño y cuenta los cultivos
  totales y virtuales dentro de la misma transacción (20 totales, 5 virtuales).
- **Última lectura en lote.** `/crops`, `/overview` y el análisis de flota resuelven la última lectura de todos los
  cultivos con una sola consulta `DISTINCT ON` más Valkey, sin N+1.
- **Apagado ordenado de MQTT.** Ante SIGTERM se cierran primero los flujos SSE, se deja de aceptar mensajes, se drenan
  las colas de los shards y la cola de lecturas por lotes, y solo entonces se desconecta el broker. La telemetría y
  los ACK retenidos se descartan; el estado retenido (LWT) sí se procesa.
- **Resiliencia de IA.** El circuit breaker abre tras 5 fallos consecutivos, gRPC y REST comparten un único
  presupuesto, gRPC tiene un deadline de 1,5 s con respaldo REST inmediato y el singleflight resiste pánicos y se
  versiona por lectura y por cultivo.
- **Seguridad de sesión.** Los JWT llevan `jti` y una versión por usuario; el logout revoca el token actual y el cambio
  o restablecimiento de contraseña invalida todos los anteriores. La autenticación MQTT exige el id del cultivo en
  minúsculas exacto y un clientid ligado al cultivo.

## Licencia

Este proyecto está bajo la licencia MIT. Consulta el archivo [LICENSE](LICENSE) para más detalles.
