# SmartPot-API

[![wakatime](https://wakatime.com/badge/user/45f93d1b-ca86-4c83-a72b-206ab33d0abb/project/950184a8-5fa5-45eb-bbfe-126402c81529.svg)](https://wakatime.com/badge/user/45f93d1b-ca86-4c83-a72b-206ab33d0abb/project/950184a8-5fa5-45eb-bbfe-126402c81529)

## Estado del Proyecto

[![CI/CD Pipeline for Deployment](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/deployment.yml/badge.svg)](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/deployment.yml)
[![CodeQL Advanced](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/codeql.yml/badge.svg)](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/codeql.yml)
[![Dependabot Updates](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/dependabot/dependabot-updates)
[![Microsoft Defender For Devops](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/defender-for-devops.yml/badge.svg)](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/defender-for-devops.yml)
[![Publish Package](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/packaging.yml/badge.svg)](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/packaging.yml)

## Descripción

SmartPot-API es una API RESTful desarrollada en **Java 17** con **Spring Boot 3.5.7**, diseñada para gestionar el
sistema de cultivos inteligentes SmartPot. La aplicación utiliza **MongoDB** como base de datos principal, **Redis**
para almacenamiento en caché, y cuenta con autenticación mediante **JWT** (JSON Web Tokens). La API está completamente
documentada con **Swagger/OpenAPI** y ofrece múltiples interfaces de documentación (Swagger UI, ReDoc, Scalar).

## Tecnologías Principales

- **Java 17** - Lenguaje de programación
- **Spring Boot 3.5.7** - Framework principal
- **Spring Data MongoDB** - Persistencia de datos
- **Spring Data Redis** - Sistema de caché
- **Spring Security** - Autenticación y autorización
- **JWT (jjwt 0.13.0)** - Tokens de autenticación
- **Lombok** - Reducción de código boilerplate
- **MapStruct 1.6.3** - Mapeo de objetos DTO/Entity
- **SpringDoc OpenAPI 2.8.14** - Documentación automática de API
- **Spring Mail** - Envío de correos electrónicos
- **Maven** - Gestión de dependencias

## Estructura del Proyecto

```

SmartPot-API/
├── .github/                          # Configuración de GitHub
│   ├── ISSUE_TEMPLATE/               # Templates para issues
│   │   ├── bug_report.md
│   │   └── feature_request.md
│   └── workflows/                    # GitHub Actions CI/CD
│       ├── codeql.yml                # Análisis de seguridad CodeQL
│       ├── defender-for-devops.yml   # Microsoft Defender
│       ├── deployment.yml            # Pipeline de despliegue
│       ├── noir-security.yml         # OWASP Noir Security
│       └── packaging.yml             # Publicación de packages
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── app.smartpot.api/
│   │   │       ├── cache/                    # Configuración de caché (Redis)
│   │   │       │   └── RedisConfig.java
│   │   │       │
│   │   │       ├── commands/                 # Gestión de comandos IoT
│   │   │       │   ├── controller/           # CommandController
│   │   │       │   ├── mapper/               # CommandMapper
│   │   │       │   ├── model/
│   │   │       │   │   ├── dto/              # CommandDTO
│   │   │       │   │   └── entity/           # Command
│   │   │       │   ├── repository/           # CommandRepository
│   │   │       │   └── service/              # CommandService, CommandServiceImpl
│   │   │       │
│   │   │       ├── crops/                    # Gestión de cultivos
│   │   │       │   ├── controller/           # CropController
│   │   │       │   ├── mapper/               # CropMapper
│   │   │       │   ├── model/
│   │   │       │   │   ├── dto/              # CropDTO
│   │   │       │   │   └── entity/           # Crop, CropStatus, CropType
│   │   │       │   ├── repository/           # CropRepository
│   │   │       │   ├── service/              # CropService, CropServiceImpl
│   │   │       │   └── validator/            # CropValidator, CropValidatorImpl
│   │   │       │
│   │   │       ├── documentation/            # Configuración de documentación API
│   │   │       │   ├── DocumentController.java
│   │   │       │   └── SwaggerConfig.java
│   │   │       │
│   │   │       ├── exception/                # Manejo global de excepciones
│   │   │       │   ├── ApiException.java
│   │   │       │   ├── ApiHandler.java
│   │   │       │   ├── ApiResponse.java
│   │   │       │   ├── EncryptionException.java
│   │   │       │   └── InvalidTokenException.java
│   │   │       │
│   │   │       ├── mail/                     # Sistema de correo electrónico
│   │   │       │   ├── config/               # AsyncConfig
│   │   │       │   ├── controller/           # EmailController
│   │   │       │   ├── mapper/               # EmailMapper
│   │   │       │   ├── model/
│   │   │       │   │   ├── dto/              # EmailDTO
│   │   │       │   │   └── entity/           # EmailDetails
│   │   │       │   ├── repository/           # EmailRepository
│   │   │       │   ├── service/              # EmailService, EmailServiceImpl
│   │   │       │   └── validator/            # EmailValidator, EmailValidatorImpl
│   │   │       │
│   │   │       ├── notifications/            # Sistema de notificaciones
│   │   │       │   ├── controller/           # NotificationController
│   │   │       │   ├── mapper/               # NotificationMapper
│   │   │       │   ├── model/
│   │   │       │   │   ├── dto/              # NotificationDTO
│   │   │       │   │   └── entity/           # Notification
│   │   │       │   ├── repository/           # NotificationRepository
│   │   │       │   └── service/              # NotificationService, NotificationServiceImpl
│   │   │       │
│   │   │       ├── records/                  # Historial y registros de mediciones
│   │   │       │   ├── controller/           # RecordController
│   │   │       │   ├── mapper/               # RecordMapper
│   │   │       │   ├── model/
│   │   │       │   │   ├── dto/              # CropRecordDTO, MeasuresDTO, RecordDTO
│   │   │       │   │   └── entity/           # DateRange, History, Measures
│   │   │       │   ├── repository/           # RecordRepository
│   │   │       │   └── service/              # RecordService, RecordServiceImpl
│   │   │       │
│   │   │       ├── responses/                # DTOs de respuestas comunes
│   │   │       │   ├── DeleteResponse.java
│   │   │       │   ├── ErrorResponse.java
│   │   │       │   └── TokenResponse.java
│   │   │       │
│   │   │       ├── security/                 # Seguridad y autenticación
│   │   │       │   ├── config/
│   │   │       │   │   ├── filters/          # JwtAuthFilter, RateLimitingFilter
│   │   │       │   │   ├── headers/          # CorsConfig
│   │   │       │   │   └── SecurityConfiguration.java
│   │   │       │   ├── controller/           # AuthController
│   │   │       │   ├── model/dto/            # ResetTokenDTO
│   │   │       │   └── service/              # JwtService, JwtServiceImpl
│   │   │       │                             # EncryptionService, EncryptionServiceImpl
│   │   │       │
│   │   │       ├── sessions/                 # Gestión de sesiones de usuario
│   │   │       │   ├── Service/              # SessionService, SessionServiceImpl
│   │   │       │   ├── controller/           # SessionController
│   │   │       │   ├── mapper/               # SessionMapper
│   │   │       │   ├── model/
│   │   │       │   │   ├── dto/              # SessionDTO
│   │   │       │   │   └── entity/           # Session
│   │   │       │   └── repository/           # SessionRepository
│   │   │       │
│   │   │       ├── users/                    # Gestión de usuarios
│   │   │       │   ├── controller/           # UserController
│   │   │       │   ├── mapper/               # UserMapper
│   │   │       │   ├── model/
│   │   │       │   │   ├── dto/              # UserDTO
│   │   │       │   │   └── entity/           # User, UserRole
│   │   │       │   ├── repository/           # UserRepository
│   │   │       │   ├── service/              # UserService, UserServiceImpl
│   │   │       │   └── validator/            # UserRegex, UserValidator, UserValidatorImpl
│   │   │       │
│   │   │       └── SmartPotApiApplication.java   # Clase principal de la aplicación
│   │   │
│   │   └── resources/
│   │       ├── templates/                    # Templates HTML para documentación
│   │       │   ├── redoc.html                # ReDoc UI
│   │       │   └── scalar.html               # Scalar UI
│   │       ├── application.yml               # Configuración de Spring Boot
│   │       └── banner.txt                    # Banner personalizado
│   │
│   └── test/
│       └── java/
│           └── app.smartpot.api/
│               ├── users/controller/         # UserControllerTest
│               └── SmartPotApiApplicationTest.java
│
├── .dockerignore                     # Archivos excluidos de Docker
├── .env.example                      # Ejemplo de variables de entorno
├── .gitignore                        # Archivos excluidos de Git
├── .gitattributes                    # Configuración de Git
├── CONTRIBUTING.md                   # Guía de contribución
├── Dockerfile                        # Configuración de contenedor Docker
├── LICENSE                           # Licencia MIT
├── README.md                         # Este archivo
├── SECURITY.md                       # Política de seguridad
├── mvnw / mvnw.cmd                   # Maven Wrapper
└── pom.xml                           # Configuración de Maven
```

## Descripción de Módulos

### Cache

**Propósito**: Configuración de Redis para almacenamiento en caché.

- `RedisConfig.java`: Configuración de conexión y serialización de Redis.

### Commands

**Propósito**: Gestión de comandos enviados a dispositivos IoT SmartPot.

- **Controlador**: Endpoints REST para crear, leer, actualizar y eliminar comandos.
- **Servicio**: Lógica de negocio para procesamiento de comandos.
- **Repositorio**: Persistencia de comandos en MongoDB.

### Crops

**Propósito**: Administración completa del ciclo de vida de cultivos.

- **Entidades**: `Crop`, `CropStatus`, `CropType`.
- **Validación**: Validadores personalizados para integridad de datos de cultivos.
- **Mapper**: Conversión entre DTOs y entidades usando MapStruct.

### Documentation

**Propósito**: Configuración de documentación automática de la API.

- `SwaggerConfig.java`: Configuración de OpenAPI/Swagger.
- `DocumentController.java`: Endpoints para servir múltiples UIs de documentación.
- **Interfaces disponibles**:
    - Swagger UI
    - ReDoc
    - Scalar

### Exception

**Propósito**: Manejo centralizado de errores y excepciones.

- `ApiHandler.java`: Manejador global de excepciones con `@ControllerAdvice`.
- `ApiException.java`: Excepción personalizada base.
- `InvalidTokenException.java`: Excepción específica para tokens JWT inválidos.
- `EncryptionException.java`: Excepción para errores de cifrado.

### Mail

**Propósito**: Sistema de envío de correos electrónicos.

- **Configuración asíncrona**: `AsyncConfig.java` para envío no bloqueante.
- **Validación**: Validadores para verificar formato de correos.
- **Repositorio**: Historial de correos enviados.

### Notifications

**Propósito**: Sistema de notificaciones a usuarios.

- Gestión de notificaciones push y en tiempo real.
- Registro de historial de notificaciones.

### Records

**Propósito**: Gestión de historial de mediciones de sensores.

- **Entidades**: `History`, `Measures`, `DateRange`.
- **DTOs**: `RecordDTO`, `MeasuresDTO`, `CropRecordDTO`.
- Almacena datos de temperatura, humedad, pH, etc.

### Security

**Propósito**: Autenticación, autorización y seguridad.

- **JWT**: Generación y validación de tokens.
- **Filtros**:
    - `JwtAuthFilter`: Validación de tokens en cada request.
    - `RateLimitingFilter`: Limitación de tasa de peticiones.
- **CORS**: Configuración de orígenes permitidos.
- **Encriptación**: Servicios de cifrado AES para datos sensibles.

### Users

**Propósito**: Gestión completa de usuarios del sistema.

- **Entidades**: `User`, `UserRole`.
- **Validación**: Expresiones regulares personalizadas (`UserRegex`).
- Autenticación y gestión de perfiles.

### Sessions

**Propósito**: Gestión de sesiones activas de usuarios.

- Registro de dispositivos y tokens activos.
- Control de sesiones concurrentes.

## Guía de Despliegue

### Requisitos Previos

- Java 17 o superior
- Maven 3.9+
- Docker (opcional, para contenedorización)
- MongoDB 4.4+
- Redis 6.0+

### 1. Compilación de la Aplicación

```
bash
# Compilar sin ejecutar tests
mvn clean package -DskipTests -P docker -f pom.xml
```

**Explicación**:

- `clean`: Limpia compilaciones previas.
- `package`: Empaqueta la aplicación en un JAR.
- `-DskipTests`: Omite la ejecución de tests.
- `-P docker`: Activa el perfil de compilación para Docker.

### 2. Construcción de Imagen Docker

```
bash
# Construir imagen para arquitectura AMD64
docker build --platform linux/amd64 -t sebastian190030/api-smartpot:latest .
```

### 3. Publicación en Docker Hub

```
bash
# Login en Docker Hub
docker login

# Subir imagen
docker push sebastian190030/api-smartpot:latest
```

### 4. Despliegue en Render.com

#### 4.1 Configuración Inicial

1. Crear cuenta en [Render.com](https://render.com)
2. Crear nuevo **Web Service**
3. Seleccionar **Docker** como tipo de despliegue
4. Configurar repositorio: `sebastian190030/api-smartpot`
5. Configurar variables de entorno (ver sección Variables de Entorno)

#### 4.2 Despliegue Automático

```
bash
# Usando deploy hook (Windows)
cmd /c deploy.render.cmd

# Usando curl directamente
curl -X POST https://api.render.com/deploy/srv-YOUR_SERVICE_ID?key=YOUR_DEPLOY_KEY
```

## 🔧 Configuración

### Variables de Entorno

Copia el archivo `.env.example` a `.env` y configura las siguientes variables:

#### Configuración de la Aplicación

```
bash
APP_NAME=SmartPot-API
PORT=8091
TITLE=SmartPot API
DESCRIPTION=Documentación de la API REST de SmartPot
VERSION=1.0.0
AUTHOR=SmartPot Developers
```

#### MongoDB

```
bash
DATA_CONNECTION_METHOD=mongodb
DATA_SOURCE_USERNAME=admin
DATA_SOURCE_PASSWORD=admin
DATA_SOURCE_DOMAIN=db-smartpot:27017
DATA_SOURCE_DB=smartpot
DATA_PARAMS=authSource=admin&directConnection=true&serverSelectionTimeoutMS=100000&socketTimeoutMS=10000&appName=mongo
```

#### Redis (Cache)

```
bash
CACHE_TYPE=redis
CACHE_HOST=cache-smartpot
CACHE_PORT=6379
CACHE_DB=0
CACHE_USERNAME=default
CACHE_PASSWORD=root
CACHE_TIMEOUT=2000
CACHE_LETTUCE_POOL_MAX_ACTIVE=8
CACHE_LETTUCE_POOL_MAX_WAIT=-1
CACHE_LETTUCE_POOL_MAX_IDLE=8
CACHE_LETTUCE_POOL_MIN_IDLE=8

# Configuración de caché
CACHE_TIME_TO_LIVE=300000
CACHE_NULL_VALUES=false
```

#### Email (SMTP)

```
bash
MAIL_HOST=mail-smartpot
MAIL_PORT=1025
MAIL_USERNAME=smartpotadmin@example.com
MAIL_PASSWORD=password123
MAIL_PROPERTIES_SMTP_AUTH=true
MAIL_PROPERTIES_SMTP_STARTTLS_ENABLE=false
```

#### Seguridad (JWT)

```
bash
SECURITY_JWT_SECRET_KEY=your-secret-key-here
SECURITY_JWT_EXPIRATION=86400000
SECURITY_PUBLIC_ROUTES=/api/v1/auth/login,/api/v1/auth/verify
SECURITY_AES_KEY=your-aes-key-here
SECURITY_SCHEME_NAME=bearerAuth
```

#### Rate Limiting

```
bash
RATE_LIMITING_MAX_REQUESTS=5
RATE_LIMITING_TIME_WINDOW=60000
RATE_LIMITING_PUBLIC_ROUTES=/api/v1/auth/login,/api/v1/auth/verify
```

#### CORS

```
bash
HEADER_CORS_ALLOWED_ORIGINS=http://localhost:5173
```

#### Servidor

```
bash
SERVER_TOMCAT_TIMEOUT=600000
SERVER_FORWARD_HEADERS_STRATEGY=framework
```

#### Swagger/OpenAPI

```
bash
SWAGGER_API_DOCS_ENABLED=true
SWAGGER_API_DOCS_PATH=/v3/api-docs
SWAGGER_REDOC_ENABLED=true
SWAGGER_REDOC_PATH=/redoc
SCALAR_ENABLED=true
SCALAR_PATH=/scalar
SWAGGER_UI_ENABLED=true
SWAGGER_UI_PATH=/
SWAGGER_UI_URL=/v3/api-docs
SWAGGER_UI_DEFAULT_MODEL_EXPAND_DEPTH=1
SWAGGER_UI_DEFAULT_MODEL_RENDERING=example
SWAGGER_UI_DISPLAY_OPERATION_ID=true
SWAGGER_UI_DISPLAY_REQUEST_DURATION=true
SWAGGER_UI_DOC_EXPANSION=list
OPENAPI_SERVER_URL=http://localhost:8091
```

#### Logging

```
bash
DEBUGGER_MODE=INFO
```

### Ejemplo de Ejecución con Docker

```
bash
docker run -d --name smartpot-api-dev -p 8091:8091 \
-e APP_NAME=SmartPot-API \
-e PORT=8091 \
-e TITLE="SmartPot API" \
-e DESCRIPTION="Documentación de la API REST de SmartPot" \
-e VERSION=1.0.0 \
-e AUTHOR="SmartPot Developers" \
\
-e DATA_CONNECTION_METHOD=mongodb \
-e DATA_SOURCE_USERNAME=admin \
-e DATA_SOURCE_PASSWORD=admin \
-e DATA_SOURCE_DOMAIN=db-smartpot:27017 \
-e DATA_SOURCE_DB=smartpot \
-e DATA_PARAMS="authSource=admin&directConnection=true&serverSelectionTimeoutMS=100000&socketTimeoutMS=10000&appName=mongo" \
\
-e CACHE_TYPE=redis \
-e CACHE_HOST=cache-smartpot \
-e CACHE_PORT=6379 \
-e CACHE_DB=0 \
-e CACHE_USERNAME=default \
-e CACHE_PASSWORD=root \
-e CACHE_TIMEOUT=2000 \
-e CACHE_LETTUCE_POOL_MAX_ACTIVE=8 \
-e CACHE_LETTUCE_POOL_MAX_WAIT=-1 \
-e CACHE_LETTUCE_POOL_MAX_IDLE=8 \
-e CACHE_LETTUCE_POOL_MIN_IDLE=8 \
\
-e CACHE_TIME_TO_LIVE=300000 \
-e CACHE_NULL_VALUES=false \
\
-e MAIL_HOST=mail-smartpot \
-e MAIL_PORT=1025 \
-e MAIL_USERNAME=smartpotadmin@example.com \
-e MAIL_PASSWORD=password123 \
-e MAIL_PROPERTIES_SMTP_AUTH=true \
-e MAIL_PROPERTIES_SMTP_STARTTLS_ENABLE=false \
\
-e SECURITY_JWT_SECRET_KEY=c8e9b6803afbcfa6edd9569c94c75ff4b144622b0a0570a636dffd62c24a3476 \
-e SECURITY_JWT_EXPIRATION=86400000 \
-e SECURITY_PUBLIC_ROUTES="/api/v1/auth/login,/api/v1/auth/verify" \
\
-e RATE_LIMITING_MAX_REQUESTS=5 \
-e RATE_LIMITING_TIME_WINDOW=60000 \
-e RATE_LIMITING_PUBLIC_ROUTES="/api/v1/auth/login,/api/v1/auth/verify" \
\
-e HEADER_CORS_ALLOWED_ORIGINS=http://localhost:5173 \
\
-e SERVER_TOMCAT_TIMEOUT=600000 \
\
-e DEBUGGER_MODE=INFO \
sebastian190030/api-smartpot:latest
```

## Testing

```
bash
# Ejecutar todos los tests
mvn test

# Ejecutar tests específicos
mvn test -Dtest=UserControllerTest
```

## Documentación de la API

Una vez que la aplicación esté en ejecución, accede a las interfaces de documentación:

- **Swagger UI**: `http://localhost:8091/` (o tu URL configurada)
- **ReDoc**: `http://localhost:8091/redoc`
- **Scalar**: `http://localhost:8091/scalar`
- **OpenAPI JSON**: `http://localhost:8091/v3/api-docs`
- **Versionado**: los endpoints funcionales de la API usan prefijo `http://localhost:8091/api/v1/...`

## Seguridad

- **Autenticación JWT**: Todas las rutas (excepto las públicas) requieren un token JWT válido.
- **Documentación protegida**: Swagger UI, ReDoc y Scalar requieren JWT (no son rutas públicas por defecto).
- **Rate Limiting**: Límite de 5 peticiones por minuto por IP en endpoints protegidos.
- **CORS**: Configurado para aceptar peticiones desde orígenes específicos.
- **Encriptación AES**: Datos sensibles cifrados antes de almacenarse.

## CI/CD

El proyecto utiliza **GitHub Actions** para automatización:

- **CodeQL**: Análisis de seguridad de código.
- **Deployment**: Compilación, construcción de imagen Docker y despliegue automático.
- **Microsoft Defender**: Escaneo de vulnerabilidades.
- **OWASP Noir**: Análisis de seguridad adicional.
- **Packaging**: Publicación automática en GitHub Container Registry.

## Contribución

Lee [CONTRIBUTING.md](CONTRIBUTING.md) para más detalles sobre cómo contribuir al proyecto.

## Licencia

Este proyecto está bajo la licencia MIT. Consulta el archivo [LICENSE](LICENSE) para más detalles.

## Política de Seguridad

Consulta [SECURITY.md](SECURITY.md) para información sobre la política de seguridad y cómo reportar vulnerabilidades.

## Autores

**SmartPot Tech Team**
