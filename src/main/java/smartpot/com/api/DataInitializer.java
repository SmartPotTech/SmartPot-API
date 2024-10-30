package smartpot.com.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import smartpot.com.api.Models.DAO.*;
import smartpot.com.api.Models.Entity.*;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private RUser userRepository;

    @Autowired
    private RSession sessionRepository;

    @Autowired
    private RCrop cropRepository;

    @Autowired
    private RHistory historyRepository;

    @Autowired
    private RNotification notificationRepository;

    @Autowired
    private RCommand commandRepository;

    @Override
    public void run(String... args) throws Exception {
        /*
        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = TypeReference.class.getResourceAsStream("/import.json");

        Map<String, Object> data = mapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {});

        // Guardar usuarios
        if (data.containsKey("usuarios")) {
            List<User> usuarios = mapper.convertValue(data.get("usuarios"), new TypeReference<List<User>>() {});
            userRepository.saveAll(usuarios);
        }

        // Guardar sesiones
        if (data.containsKey("sesiones")) {
            List<Session> sessions = mapper.convertValue(data.get("sesiones"), new TypeReference<List<Session>>() {});
            sessionRepository.saveAll(sessions);
        }

        // Guardar cultivos
        if (data.containsKey("cultivos")) {
            List<Crop> cultivos = mapper.convertValue(data.get("cultivos"), new TypeReference<List<Crop>>() {});
            cropRepository.saveAll(cultivos);
        }

        // Guardar registros
        if (data.containsKey("registros")) {
            List<History> historiales = mapper.convertValue(data.get("registros"), new TypeReference<List<History>>() {});
            historyRepository.saveAll(historiales);
        }

        if (data.containsKey("notificaciones")) {
            List<Notification> notifications = mapper.convertValue(data.get("notificaciones"), new TypeReference<List<Notification>>() {});
            notificationRepository.saveAll(notifications);
        }

        if (data.containsKey("comandos")) {
            List<Command> commands = mapper.convertValue(data.get("comandos"), new TypeReference<List<Command>>() {});
            commandRepository.saveAll(commands);
        }*/

    }
}
