# SmartPot-API

## Deployment

### 1. Compilación de la Aplicación

- **Abre la terminal** y navega hasta la raíz de tu proyecto.
- Ejecuta el comando:

  ```bash
  mvn clean package -DskipTests -P docker -f pom.xml
  ```

    - Esto limpiará el proyecto, compilará el código y empaquetará la aplicación en un archivo JAR. El uso de `-DskipTests` omite las pruebas durante la construcción, esto con la finalidad de que no genere error, dado que se cambia las properties por variables de entorno. El uso de `-P docker` es porque se definió un perfil de build especifico donde no se compila en credentials.propierties

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

    - Esto hace que tu imagen esté disponible en linea públicamente

### 4. Despliegue en Render.com

#### 4.1 Creación de Web Service

- **Inicia sesión en tu cuenta de Render.com**.
- **Crea un nuevo servicio** seleccionando "New" y luego "Web Service".
- **Elige Docker** como tipo de despliegue.
- **Proporciona el nombre de tu repositorio** de Docker Hub: `sebastian190030/api-smarpot`.
- **Configura las variables de entorno** necesarias para tu aplicación.
- **Haz clic en "Crear"** para iniciar el despliegue.

#### 4.2 Despliegue
- **Usa el Deploy hook** para hacer deploy automatico

  ```bash
  cmd /c deploy.render.cmd
  ```
  - Esto ejecuta un CURL -X POST https://api.render.com/deploy/srv-csgeg0lumphs73b48veg?key={key}


## Estructura del Proyecto

Este documento describe la estructura del proyecto **SmartPot API**, desarrollado con **Spring Boot**, **Java 17** y gestionado con **Maven**. A continuación se detallan los directorios y la organización general del código fuente, con el objetivo de proporcionar una visión clara de cómo está estructurada la aplicación.

## Estructura de Directorios

```
src/
 └── main/
      └── java/
           └── smartpot/
                └── com/
                     └── api/
                          ├── Controllers/
                          ├── Exception/
                          ├── Models/
                          ├── Security/
                          └── SmartPotApiApplication.java
```

### Descripción de Directorios y Archivos

#### `Controllers/`
Contiene los controladores principales que manejan las solicitudes HTTP. Cada controlador está asociado a un recurso específico de la aplicación.

- **`AuthController.java`**: Controlador encargado de la autenticación y la gestión de sesiones de usuario.
- **`CommandController.java`**: Gestiona las solicitudes relacionadas con los comandos del sistema.
- **`CropController.java`**: Controlador que maneja las operaciones relacionadas con los cultivos.
- **`HistoryController.java`**: Controla las operaciones sobre el historial de acciones o registros.
- **`IndexController.java`**: Controlador base para el punto de entrada o la raíz de la aplicación.
- **`NotificationController.java`**: Gestiona las notificaciones enviadas al usuario.
- **`SessionController.java`**: Maneja la creación y validación de las sesiones de usuario.
- **`UserController.java`**: Controlador relacionado con las operaciones CRUD sobre los usuarios.

#### `Exception/`
Contiene clases relacionadas con el manejo de excepciones y la estructuración de las respuestas de error.

- **`ApiException.java`**: Excepción base personalizada para manejar errores comunes de la API.
- **`ApiHandler.java`**: Lógica para capturar y procesar excepciones globales.
- **`ApiResponse.java`**: Contiene la estructura estándar para las respuestas de error (mensaje y estado).

#### `Models/`
Este paquete contiene las clases que representan el modelo de la aplicación. Se organiza en subdirectorios para mantener separadas las distintas capas del modelo.

##### `DAO/`
Contiene la lógica de acceso a datos. Aquí se encuentran los repositorios que interactúan directamente con la base de datos.

- **`Repository/`**: Contiene las interfaces de repositorios de Spring Data JPA.
  - **`RCommand.java`**: Repositorio para la entidad `Command`.
  - **`RCrop.java`**: Repositorio para la entidad `Crop`.
  - **`RHistory.java`**: Repositorio para la entidad `History`.
  - **`RNotification.java`**: Repositorio para la entidad `Notification`.
  - **`RSession.java`**: Repositorio para la entidad `Session`.
  - **`RUser.java`**: Repositorio para la entidad `User`.

##### `DTO/`
Contiene las clases de objetos de transferencia de datos (DTOs). Estas clases son utilizadas para enviar y recibir datos entre el cliente y el servidor.

- **`CommandDTO.java`**: DTO para el recurso `Command`.
- **`CropDTO.java`**: DTO para el recurso `Crop`.
- **`HistoryDTO.java`**: DTO para el recurso `History`.
- **`NotificationDTO.java`**: DTO para el recurso `Notification`.
- **`SessionDTO.java`**: DTO para el recurso `Session`.
- **`UserDTO.java`**: DTO para el recurso `User`.
- **`ObjectIdSerializer.java`**: Serializador personalizado para el tipo de ID de objetos.

##### `Entity/`
Contiene las clases que representan las entidades JPA, mapeadas a las tablas de la base de datos.

- **`Command.java`**: Entidad que representa el comando.
- **`Crop.java`**: Entidad que representa el cultivo.
- **`History.java`**: Entidad que representa el historial de acciones.
- **`Notification.java`**: Entidad que representa las notificaciones.
- **`Role.java`**: Entidad que representa los roles de los usuarios.
- **`Session.java`**: Entidad que representa la sesión de usuario.
- **`Status.java`**: Enum que define los posibles estados de un recurso.
- **`Type.java`**: Enum que define los tipos de recursos o acciones.
- **`User.java`**: Entidad que representa el usuario.

#### `Security/`
Contiene las configuraciones y clases relacionadas con la seguridad, como la autenticación y autorización de usuarios.

- **`SecurityConfiguration.java`**: Configuración global de seguridad de la aplicación, que incluye reglas de acceso y protección.
- **`jwt/`**: Paquete que contiene la lógica relacionada con la autenticación basada en JWT (JSON Web Token).
  - **`JwtAuthFilter.java`**: Filtro de autenticación JWT.
  - **`JwtService.java`**: Servicio encargado de generar y validar los tokens JWT.

#### `SmartPotApiApplication.java`
Archivo principal que contiene el punto de entrada de la aplicación Spring Boot. Aquí se inicia la aplicación y se configura el contexto de Spring.