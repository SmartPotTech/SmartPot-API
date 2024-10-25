package smarpot.com.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories(basePackages = "smarpot.com.api")
public class SmartPotApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartPotApiApplication.class, args);
    }

}
