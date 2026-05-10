# AGENTS.md — SmartPot-API

Compact instruction file for AI agents working in this repository.

## Build & Run Commands

- **Run locally (auto-loads `.env` via dotenv-java):** `mvn spring-boot:run`
- **Package with Docker profile:** `mvn clean package -DskipTests -P docker`
- **Run all tests:** `mvn test`
- **Run a single test class:** `mvn test -Dtest=UserControllerTest`
- **Package only:** `mvn package`

## Tech Stack (Verified from pom.xml)

- **Java 17**, Spring Boot **4.0.6** (parent)
- **MongoDB** — primary database (Spring Data MongoDB)
- **Redis / Jedis 7.4.1** — caching layer (note: config keys say `lettuce`, but dependency is `jedis`)
- **jjwt 0.13.0** — JWT tokens
- **MapStruct 1.6.3** + processor — DTO↔Entity mapping
- **SpringDoc OpenAPI 3.0.3** — Swagger UI, ReDoc, Scalar
- **dotenv-java 3.2.0** — `.env` file loading at startup
- **Maven Wrapper** (`mvnw`) available

## Architecture

- **Domain-based modules** in `app.smartpot.api`: `users`, `crops`, `commands`, `records`, `notifications`, `sessions`, `mail`, `actuators`
- **Layered structure per domain**: `controller/`, `service/` (+ `impl/`), `repository/`, `mapper/`, `model/dto/`, `model/entity/`, optional `validator/`
- **Mappers**: MapStruct interfaces named `{Module}Mapper` (e.g., `UserMapper`). Update them when adding DTOs/entities.
- **Exception handling**: Throw `ApiException` (custom) in controllers; `ApiHandler` (`@ControllerAdvice`) catches it and returns `ApiResponse`.
- **Security**: JWT stateless (`JwtAuthFilter`), in-memory rate limiting (`RateLimitingFilter`), BCrypt passwords. Public routes configured as comma-separated in `application.security.public.routes`.
- **Session policy**: `STATELESS` — no HTTP sessions.

## Environment & Configuration

- All config is externalized via `.env` and referenced in `application.yml` with `${VAR:default}` syntax.
- `mvn spring-boot:run` automatically loads `.env` — no manual `export` needed.
- Key sections in `application.yml`:
  - `application.*` — metadata, JWT secret/expiration, AES key, public routes
  - `spring.data.mongodb.*` — connection URI
  - `spring.data.redis.*` — Redis pool (uses Jedis, not Lettuce, despite naming)
  - `spring.mail.*` — SMTP settings
  - `rate.limiting.*` — max requests, time window, public route exclusions
  - `http.header.cors.allowedOrigins` — CORS origins
  - `springdoc.*` — OpenAPI/Swagger UI paths

## Testing

- Test-specific `application.yml` is in `src/test/resources/`.
- Test config sets `application.security.public.routes: /**` (all routes public) and uses `mongo://` protocol in the MongoDB URI — do not copy these to production config.

## CI / Deployment

- GitHub Actions workflows in `.github/workflows/`:
  - `deployment.yml` — builds (`-P docker`), pushes Docker image to Docker Hub, triggers Render deploy
  - `packaging.yml` — builds and pushes Docker image to GitHub Container Registry (`ghcr.io`)
  - `codeql.yml`, `noir-security.yml`, `defender-for-devops.yml` — security scanning
- Docker build command from CI: `docker build --platform linux/amd64 -t <image> .`

## Quirks & Gotchas

- **sessions module folder case**: `sessions/Service/` (capital S) exists alongside lowercase `service/` folders in other modules. Watch path casing.
- **Redis client mismatch**: `application.yml` uses `lettuce` pool config keys, but the actual dependency is `jedis`. Changing pool settings may require switching to `lettuce` dependency if needed.
- **Public routes format**: comma-separated string, not a YAML list.
- **Docker profile**: only adds `maven-resources-plugin`; the JAR is otherwise the same.

## API Documentation Endpoints (when running)

- Swagger UI: `/`
- ReDoc: `/redoc`
- Scalar: `/scalar`
- OpenAPI JSON: `/v3/api-docs`
- Business API routes remain versioned under `/api/v1/**`
- Docs routes are intentionally protected by JWT unless explicitly added to `application.security.public.routes`
