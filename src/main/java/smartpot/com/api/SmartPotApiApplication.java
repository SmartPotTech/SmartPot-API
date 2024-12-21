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
        Dotenv dotenv = Dotenv.load();

        dotenv.entries().forEach(entry -> {
            String key = entry.getKey();
            String value = entry.getValue();

            if (value != null) {
                System.setProperty(key, value);
            }
        });
    }
}
