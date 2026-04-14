# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Compile and package (skip tests, Docker profile)
mvn clean package -DskipTests -P docker

# Run tests
mvn test

# Run a specific test class
mvn test -Dtest=UserControllerTest

# Run the application (loads .env automatically via dotenv-java)
mvn spring-boot:run

# Package only
mvn package
```

## Architecture Overview

### Module Structure (Layered per Domain)

Each domain module follows an identical layered structure:

```
module/
├── controller/      # REST endpoints
├── service/        # Service interface
├── service/impl/   # Service implementation
├── repository/     # MongoDB repository (extends MongoRepository)
├── mapper/         # MapStruct mapper (DTO <-> Entity)
├── model/
│   ├── dto/        # Data Transfer Objects
│   └── entity/     # MongoDB documents
└── validator/      # Custom validators (optional)
```

Key modules: `users`, `crops`, `commands`, `records`, `notifications`, `sessions`, `mail`, `actuators`.

### DTO/Entity Mapping

MapStruct is used for all DTO↔Entity conversions. Mappers follow the naming pattern `{Module}Mapper` (e.g., `UserMapper`, `CropMapper`). When adding new DTOs or entities, update the corresponding mapper interface.

### Exception Handling Pattern

- `ApiException` — base custom exception, wraps an `ApiResponse`
- `ApiHandler` — `@ControllerAdvice` that catches `ApiException` and returns `ApiResponse` with the exception's HTTP status
- `ApiResponse` — standard response wrapper with `status`, `message`, `data`, `time`
- Other exceptions: `InvalidTokenException`, `EncryptionException`

All controllers should throw `ApiException` rather than native Spring exceptions.

### Security Architecture

- **JWT stateless authentication** via `JwtAuthFilter` (adds `UsernamePasswordAuthenticationToken` to SecurityContext)
- **Rate limiting** via `RateLimitingFilter` (in-memory, per IP, configurable via `rate.limiting.*`)
- **Password encoding**: BCrypt via `PasswordEncoder` bean
- **Public routes** configured in `application.security.public.routes` (comma-separated)
- **Session policy**: `STATELESS` — no HTTP sessions
- **CORS**: delegated to `CorsConfig` (configured via `http.header.cors.allowedOrigins`)

### Configuration

All configuration is externalized via environment variables (`.env` file, loaded by `dotenv-java`). The `application.yml` references these with `${VAR:default}` syntax. Key config sections:

- `application.*` — app metadata, JWT secret/expiration, AES key
- `spring.data.mongodb.*` — MongoDB connection URI
- `spring.data.redis.*` — Redis connection pool
- `spring.mail.*` — SMTP settings
- `rate.limiting.*` — request limits and time windows
- `springdoc.*` — OpenAPI/Swagger UI configuration

### Documentation

Three API doc UIs are available (configured via `springdoc.*`):
- Swagger UI: `/` (or configured path)
- ReDoc: `/redoc`
- Scalar: `/scalar`
- OpenAPI JSON: `/v3/api-docs`

### CI/CD

GitHub Actions workflows in `.github/workflows/` handle:
- CodeQL security analysis
- Maven build + Docker image
- Deployment to Render.com
- OWASP Noir and Microsoft Defender scanning

### Key Dependencies

- **Spring Boot 4.0.5** with web, security, data-mongodb, data-redis, validation, actuator, mail, cache
- **MongoDB** — primary database
- **Redis/Jedis** — caching layer
- **jjwt 0.13.0** — JWT generation/validation
- **MapStruct 1.6.3** — object mapping
- **Lombok** — boilerplate reduction
- **SpringDoc OpenAPI 3.0.2** — API documentation
- **dotenv-java 3.2.0** — `.env` file loading
