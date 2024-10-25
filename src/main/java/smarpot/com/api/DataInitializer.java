package smarpot.com.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import smarpot.com.api.Models.DAO.RUser;
import smarpot.com.api.Models.Entity.User;

import java.io.InputStream;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private RUser userRepository;

    @Override
    public void run(String... args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = TypeReference.class.getResourceAsStream("/import.json");
        List<User> users = mapper.readValue(inputStream, new TypeReference<List<User>>() {});

        userRepository.saveAll(users);
    }
}
