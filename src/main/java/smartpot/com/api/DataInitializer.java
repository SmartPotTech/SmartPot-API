package smartpot.com.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import smartpot.com.api.Models.DAO.Repository.RCrop;
import smartpot.com.api.Models.DAO.Repository.RHistory;
import smartpot.com.api.Models.DAO.Repository.RSession;
import smartpot.com.api.Models.DAO.Repository.RUser;
import smartpot.com.api.Models.Entity.Crop;
import smartpot.com.api.Models.Entity.History;
import smartpot.com.api.Models.Entity.Session;
import smartpot.com.api.Models.Entity.User;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

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