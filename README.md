# SmartPot-API

## Deployment

[![CI/CD Pipeline for Deployment](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/deployment.yml/badge.svg)](https://github.com/SmartPotTech/SmartPot-API/actions/workflows/deployment.yml)

### 1. Compilación de la Aplicación

- **Abre la terminal** y navega hasta la raíz de tu proyecto.
- Ejecuta el comando:

  ```bash
  mvn clean package -DskipTests -P docker -f pom.xml
  ```

    - Esto limpiará el proyecto, compilará el código y empaquetará la aplicación en un archivo JAR. El uso de `-DskipTests` omite las pruebas durante la construcción, esto con la finalidad de que no genere error, dado que se cambia las properties por variables de entorno. El uso de `-P docker` es porque se definió un perfil de build especifico donde compila ignorando él .env

### 2. Creación de la Imagen Docker

- **Asegúrate de tener un `Dockerfile`** en la raíz de tu proyecto. Este archivo define cómo se debe construir la imagen Docker.
- **Ejecuta el siguiente comando** para construir la imagen:

  ```bash
  docker build --platform linux/amd64 -t sebastian190030/api-smartpot:latest .
  ```

    - Esto crea una imagen basada en tu aplicación.

### 3. Publicación de la Imagen en Docker Hub

- **Inicia sesión en Docker Hub** desde la terminal:

  ```bash
  docker login
  ```

- **Sube la imagen** a tu repositorio (cambiando sebastian190030 por tu usuario) en Docker Hub:

  ```bash
  docker push sebastian190030/api-smartpot:latest
  ```

    - Esto hace que tu imagen esté disponible en línea públicamente

### 4. Despliegue en Render.com

#### 4.1 Creación de Web Service

- **Inicia sesión en tu cuenta de Render.com**.
- **Crea un nuevo servicio** seleccionando "New" y luego "Web Service".
- **Elige Docker** como tipo de despliegue.
- **Proporciona el nombre de tu repositorio** de Docker Hub: `sebastian190030/api-smarpot`.
- **Configura las variables de entorno** necesarias para tu aplicación.
- **Haz clic en "Crear"** para iniciar el despliegue.

#### 4.2 Despliegue
- **Usa el Deploy hook** para hacer deploy automático

  ```bash
  cmd /c deploy.render.cmd
  ```
  - Esto ejecuta un CURL -X POST https://api.render.com/deploy/srv-csgeg0lumphs73b48veg?key={key}

## 📦 Estructura del Proyecto – SmartPot API
Este proyecto es una API RESTful desarrollada en Java con Spring Boot, diseñada para gestionar la información de cultivos y dispositivos SmartPot. Utiliza MongoDB como base de datos y Redis para almacenamiento en caché. La API está documentada con Swagger/OpenAPI y cuenta con autenticación JWT.

### 📁 Estructura de Directorios

```
src/
 └── main/
      ├── java/
      │    └── smartpot/
      │         └── com/
      │              └── api/
      │                   ├── Cache/
      │                   ├── Commands/
      │                   ├── Crops/
      │                   ├── Documentation/
      │                   ├── Exception/
      │                   ├── Mail/
      │                   ├── Notifications/
      │                   ├── Records/
      │                   ├── Responses/
      │                   ├── Security/
      │                   ├── Sessions/
      │                   ├── Users/
      │                   └── SmartPotApiApplication.java
      └── resources/
           ├── application.yml
           └── banner.txt
```

---

## 📂 Descripción de Directorios

### `Cache/`

Configuraciones relacionadas con mecanismos de caché como Redis.

* `RedisConfig.java`: Configura el uso de Redis como sistema de almacenamiento temporal.

---

### `Commands/`

Manejo de comandos automatizados en la aplicación.

* **Controller**: `CommandController.java`
* **DTO**: `CommandDTO.java`
* **Entity**: `Command.java`
* **Repository**: `RCommand.java`
* **Service**: `SCommand.java`, `SCommandI.java`
* **Mapper**: `MCommand.java`
* **Validator**: *(vacío o por implementar)*

---

### `Crops/`

Administración de cultivos y su estado.

* **Controller**: `CropController.java`
* **DTO**: `CropDTO.java`
* **Entity**: `Crop.java`, `CropStatus.java`, `CropType.java`
* **Repository**: `RCrop.java`
* **Service**: `SCrop.java`, `SCropI.java`
* **Mapper**: `MCrop.java`
* **Validator**: `VCrop.java`, `VCropI.java`

---

### `Documentation/`

Configuración de Swagger/OpenAPI.

* `SwaggerConfig.java`: Habilita y configura la documentación automática de la API.

---

### `Exception/`

Manejo global de errores.

* `ApiException.java`: Excepción personalizada.
* `ApiHandler.java`: Manejador global de excepciones.
* `ApiResponse.java`: Modelo de respuesta para errores.
* `InvalidTokenException.java`: Excepción específica para tokens inválidos.

---

### `Mail/`

Envío de correos electrónicos y configuración relacionada.

* **Config**: `AsyncConfig.java`
* **Controller**: `EmailController.java`
* **DTO**: `EmailDTO.java`
* **Entity**: `EmailDetails.java`
* **Repository**: `EmailRepository.java`
* **Service**: `EmailService.java`, `EmailServiceI.java`
* **Mapper**: `EmailMapper.java`
* **Validator**: `EmailValidator.java`, `EmailValidatorI.java`

---

### `Notifications/`

Notificaciones automáticas hacia el usuario.

* **Controller**: `NotificationController.java`
* **DTO**: `NotificationDTO.java`
* **Entity**: `Notification.java`
* **Repository**: `RNotification.java`
* **Service**: `SNotification.java`, `SNotificationI.java`
* **Mapper**: `MNotification.java`

---

### `Records/`

Historial y registros de medidas.

* **Controller**: `HistoryController.java`
* **DTO**: `CropRecordDTO.java`, `MeasuresDTO.java`, `RecordDTO.java`
* **Entity**: `DateRange.java`, `History.java`, `Measures.java`
* **Repository**: `RHistory.java`
* **Service**: `SHistory.java`, `SHistoryI.java`
* **Mapper**: `MRecords.java`

---

### `Responses/`

Modelos de respuestas comunes de la API.

* `DeleteResponse.java`: Respuesta para operaciones DELETE exitosas.
* `ErrorResponse.java`: Estructura para respuestas de error.
* `TokenResponse.java`: Estructura para respuestas con tokens JWT.

---

### `Security/`

Gestión de autenticación, autorización y configuración de seguridad.

* **Config**: `SecurityConfiguration.java`
* **Filters**: `JwtAuthFilter.java`, `RateLimitingFilter.java`
* **CORS**: `CorsConfig.java`
* **Controller**: `AuthController.java`
* **Service**: `JwtService.java`, `JwtServiceI.java`

---

### `Sessions/`

Gestión de sesiones de usuario.

* **Controller**: `SessionController.java`
* **DTO**: `SessionDTO.java`
* **Entity**: `Session.java`
* **Repository**: `RSession.java`
* **Service**: `SSession.java`, `SSessionI.java`
* **Mapper**: `MSession.java`

---

### `Users/`

Gestión de usuarios del sistema.

* **Controller**: `UserController.java`
* **DTO**: `UserDTO.java`
* **Entity**: `User.java`, `UserRole.java`
* **Repository**: `RUser.java`
* **Service**: `SUser.java`, `SUserI.java`
* **Mapper**: `MUser.java`
* **Validator**: `UserRegex.java`, `VUser.java`, `VUserI.java`

---

### `SmartPotApiApplication.java`

Clase principal que inicia la aplicación Spring Boot. Marca el punto de entrada (`main`) del backend SmartPot API.

---

## 🧪 Estructura de Pruebas

```
src/
 └── test/
      └── java/
           └── smartpot/
                └── com/
                     └── api/
                          ├── SmartPotApiApplicationTest.java
                          └── Users/
                               └── Controller/
                                    └── UserControllerTest.java
```

Pruebas unitarias utilizando JUnit para validar funcionalidad básica, incluyendo controladores y la clase principal.

---

## 🧱 Archivos adicionales relevantes

* `Dockerfile`, `.dockerignore`: Contenedorización con Docker.
* `.env`, `.env.example`: Configuración de entorno.
* `.gitignore`, `.gitattributes`: Configuración de exclusión de archivos.
* `pom.xml`: Gestión de dependencias con Maven.
* `.github/`: Flujos de trabajo CI/CD con GitHub Actions.
* `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `LICENSE`: Documentación del proyecto y políticas de contribución.


## Ejecución en Entorno Docker

Para desplegar la aplicación en un entorno Docker, puedes utilizar el siguiente comando:

```bash
docker run -d --name <CONTAINER_NAME> -p <EXTERNAL_PORT>:<INTERNAL_PORT> \
-e APP_NAME=<Nombre de la aplicación> \
-e PORT=<Puerto de escucha interno> \
-e TITLE=<Título de la API> \
-e DESCRIPTION=<Descripción de la API> \
-e VERSION=<Versión de la API> \
-e AUTHOR=<Nombre del autor o equipo> \
-e DATA_SOURCE_USERNAME=<Usuario de la base de datos> \
-e DATA_SOURCE_PASSWORD=<Contraseña de la base de datos> \
-e DATA_SOURCE_DOMAIN=<Dominio de la base de datos> \
-e DATA_SOURCE_DB=<Nombre de la base de datos> \
-e DATA_SOURCE_RETRY_WRITES=<Habilitar o deshabilitar reintentos de escritura (true/false)> \
-e DATA_SOURCE_W=<Nivel de confirmación de escritura> \
-e DATA_SOURCE_APP_NAME=<Nombre de la aplicación en MongoDB> \
-e SECURITY_JWT_SECRET_KEY=<Clave secreta para JWT> \
-e SECURITY_JWT_EXPIRATION=<Tiempo de expiración del JWT en milisegundos> \
-e SECURITY_PUBLIC_ROUTES=<Rutas públicas que no requieren autenticación> \
-e HEADER_CORS_ALLOWED_ORIGINS=<Origen permitido para CORS> \
-e DEBUGGER_MODE=<Modo de depuración (INFO, DEBUG, OFF)> \
<DOCKER_IMAGE>
```

### Variables de Entorno
A continuación, se describen las variables de entorno necesarias para la ejecución:

#### Configuración de la Aplicación
- **`APP_NAME`**: Nombre de la aplicación para identificación en el entorno.
- **`PORT`**: Puerto interno en el que la aplicación escucha.
- **`TITLE`**: Título descriptivo de la API.
- **`DESCRIPTION`**: Información breve sobre la API.
- **`VERSION`**: Versión de la API (en formato semántico, como `1.0.0`).
- **`AUTHOR`**: Nombre del autor o equipo desarrollador.

#### Configuración de la Base de Datos
- **`DATA_SOURCE_USERNAME`**: Usuario de acceso a la base de datos.
- **`DATA_SOURCE_PASSWORD`**: Contraseña de acceso.
- **`DATA_SOURCE_DOMAIN`**: URL o dominio del clúster de MongoDB.
- **`DATA_SOURCE_DB`**: Nombre de la base de datos.
- **`DATA_SOURCE_RETRY_WRITES`**: Define si los reintentos de escritura están habilitados (`true` o `false`).
- **`DATA_SOURCE_W`**: Nivel de confirmación de escritura (por ejemplo, `majority`).
- **`DATA_SOURCE_APP_NAME`**: Nombre usado para identificar la aplicación en las métricas de MongoDB.

#### Configuración de Seguridad
- **`SECURITY_JWT_SECRET_KEY`**: Clave secreta utilizada para firmar y verificar los tokens JWT.
- **`SECURITY_JWT_EXPIRATION`**: Duración del token JWT en milisegundos (por ejemplo, `300000` para 5 minutos).
- **`SECURITY_PUBLIC_ROUTES`**: Endpoints públicos que no requieren autenticación (separados por comas).

#### Configuración de CORS
- **`HEADER_CORS_ALLOWED_ORIGINS`**: Origen permitido para solicitudes CORS (por ejemplo, `http://localhost:3000`).

#### Configuración de Depuración
- **`DEBUGGER_MODE`**: Nivel de detalle para los logs (`INFO`, `DEBUG`, `OFF`).

### Ejemplo de Ejecución
Aquí tienes un ejemplo práctico con valores de ejemplo que puedes ajustar según tu entorno:

```bash
docker run -d --name smartpot-api-dev -p 8091:8091 \
  -e APP_NAME=SmartPot-API \
  -e PORT=8091 \
  -e TITLE="SmartPot API" \
  -e DESCRIPTION="Documentación de la API REST de SmartPot" \
  -e VERSION=1.0.0 \
  -e AUTHOR="SmartPot Developers" \
  \
  # MongoDB
  -e DATA_CONNECTION_METHOD=mongodb \
  -e DATA_SOURCE_USERNAME=admin \
  -e DATA_SOURCE_PASSWORD=admin \
  -e DATA_SOURCE_DOMAIN=db-smartpot:27017 \
  -e DATA_SOURCE_DB=smartpot \
  -e DATA_PARAMS="authSource=admin&directConnection=true&serverSelectionTimeoutMS=100000&socketTimeoutMS=10000&appName=mongo" \
  \
  # Redis
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
  # Config cache
  -e CACHE_TIME_TO_LIVE=300000 \
  -e CACHE_NULL_VALUES=false \
  \
  # Email (Mailpit local)
  -e MAIL_HOST=mail-smartpot \
  -e MAIL_PORT=1025 \
  -e MAIL_USERNAME=smartpotadmin@example.com \
  -e MAIL_PASSWORD=password123 \
  -e MAIL_PROPERTIES_SMTP_AUTH=true \
  -e MAIL_PROPERTIES_SMTP_STARTTLS_ENABLE=false \
  \
  # Seguridad
  -e SECURITY_JWT_SECRET_KEY=c8e9b6803afbcfa6edd9569c94c75ff4b144622b0a0570a636dffd62c24a3476 \
  -e SECURITY_JWT_EXPIRATION=86400000 \
  -e SECURITY_PUBLIC_ROUTES="/auth/login,/auth/verify" \
  \
  # Rate limiting
  -e RATE_LIMITING_MAX_REQUESTS=5 \
  -e RATE_LIMITING_TIME_WINDOW=60000 \
  -e RATE_LIMITING_PUBLIC_ROUTES="/swagger-ui/,/v3/api-docs,/swagger-resources/,/webjars/" \
  \
  # Headers
  -e HEADER_CORS_ALLOWED_ORIGINS=http://localhost:5173 \
  \
  # Tomcat
  -e SERVER_TOMCAT_TIMEOUT=600000 \
  \
  # Debugger
  -e DEBUGGER_MODE=INFO \
  sebastian190030/api-smartpot:latest

```
