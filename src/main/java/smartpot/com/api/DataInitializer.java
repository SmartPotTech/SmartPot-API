package smartpot.com.api;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {
    @Override
    public void run(String... args) throws Exception {

    }

  /*   @Autowired
    private RUser userRepository;

   @Autowired
    private RSession sessionRepository;

    @Autowired
    private RCrop cropRepository;

    @Autowired
    private RHistory historyRepository;

    @Override
    public void run(String... args) throws Exception {
        /*
        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = TypeReference.class.getResourceAsStream("/import.json");

        Map<String, Object> data = mapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {
        });

        if (data.containsKey("usuarios")) {
            List<User> usuarios = mapper.convertValue(data.get("usuarios"), new TypeReference<List<User>>() {
            });
            userRepository.saveAll(usuarios);
        }

      *//*  if (data.containsKey("sesiones")) {
            List<Session> sessions = mapper.convertValue(data.get("sesiones"), new TypeReference<List<Session>>() {
            });
            sessionRepository.saveAll(sessions);
        }*//*

        if (data.containsKey("cultivos")) {
            List<Crop> cultivos = mapper.convertValue(data.get("cultivos"), new TypeReference<List<Crop>>() {
            });
            cropRepository.saveAll(cultivos);
        }

        if (data.containsKey("registros")) {
            List<History> historiales = mapper.convertValue(data.get("registros"), new TypeReference<List<History>>() {
            });
            historyRepository.saveAll(historiales);
        }

         */
}