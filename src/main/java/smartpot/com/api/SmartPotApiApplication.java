package smartpot.com.api;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories(basePackages = "smartpot.com.api")
public class SmartPotApiApplication {

    public static void main(String[] args) {
        loadEnv();
        SpringApplication.run(SmartPotApiApplication.class, args);
    }

    private static void loadEnv(){
        // Carga de .env
        Dotenv dotenv = Dotenv.load();

        // Configuración APP
        System.setProperty("DATA_CONNECTION_METHOD", dotenv.get("DATA_CONNECTION_METHOD"));
        System.setProperty("APP_NAME", dotenv.get("APP_NAME"));
        System.setProperty("PORT", dotenv.get("PORT"));
        System.setProperty("TITLE", dotenv.get("TITLE"));
        System.setProperty("DESCRIPTION", dotenv.get("DESCRIPTION"));
        System.setProperty("VERSION", dotenv.get("VERSION"));
        System.setProperty("AUTHOR", dotenv.get("AUTHOR"));

        // Conexión de MongoDB
        System.setProperty("DATA_SOURCE_USERNAME", dotenv.get("DATA_SOURCE_USERNAME"));
        System.setProperty("DATA_SOURCE_PASSWORD", dotenv.get("DATA_SOURCE_PASSWORD"));
        System.setProperty("DATA_SOURCE_DOMAIN", dotenv.get("DATA_SOURCE_DOMAIN"));
        System.setProperty("DATA_SOURCE_DB", dotenv.get("DATA_SOURCE_DB"));
        System.setProperty("DATA_SOURCE_RETRY_WRITES", dotenv.get("DATA_SOURCE_RETRY_WRITES"));
        System.setProperty("DATA_SOURCE_W", dotenv.get("DATA_SOURCE_W"));
        System.setProperty("DATA_SOURCE_APP_NAME", dotenv.get("DATA_SOURCE_APP_NAME"));

        // Security Config
        System.setProperty("SECURITY_JWT_SECRET_KEY", dotenv.get("SECURITY_JWT_SECRET_KEY"));
        System.setProperty("SECURITY_JWT_EXPIRATION", dotenv.get("SECURITY_JWT_EXPIRATION"));
        System.setProperty("SECURITY_PUBLIC_ROUTES", dotenv.get("SECURITY_PUBLIC_ROUTES"));

        // Http Headers
        System.setProperty("HEADER_CORS_ALLOWED_ORIGINS", dotenv.get("HEADER_CORS_ALLOWED_ORIGINS"));

        // Debug
        System.setProperty("DEBUGGER_MODE", dotenv.get("DEBUGGER_MODE"));
    }
}
