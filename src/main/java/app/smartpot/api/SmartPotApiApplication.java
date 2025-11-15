package app.smartpot.api;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories(basePackages = "smartpot.com.api")
@EnableCaching
@Slf4j
public class SmartPotApiApplication {

    public static void main(String[] args) {
        loadEnv();
        SpringApplication.run(SmartPotApiApplication.class, args);
    }

    private static void loadEnv() {
        try {
            Dotenv dotenv = Dotenv.load();

            dotenv.entries().forEach(entry -> {
                String key = entry.getKey();
                String value = entry.getValue();

                if (value != null) {
                    System.setProperty(key, value);
                }
            });
        } catch (Exception e) {
            log.warn("No se pudo cargar el archivo .env. Se continuará con las variables de entorno ya presentes.");
            log.debug("Detalles del error: ", e);
        }
    }
}
