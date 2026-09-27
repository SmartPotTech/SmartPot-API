package app.smartpot.api.health;

import app.smartpot.api.ai.service.AiClient;
import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.mqtt.service.MqttGateway;
import app.smartpot.api.virtualdevices.service.SimulatorClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Salud pública para balanceadores y despliegues. Solo la base de datos hace fallar el chequeo:
 * sin broker, caché o IA la API sigue atendiendo con funciones reducidas.
 */
@RestController
@Tag(name = "Salud", description = "Estado de la API y sus dependencias")
public class HealthController {

    private final MongoTemplate mongoTemplate;
    private final MqttGateway mqttGateway;
    private final CacheStore cacheStore;
    private final AiClient aiClient;
    private final SimulatorClient simulatorClient;

    public HealthController(MongoTemplate mongoTemplate, MqttGateway mqttGateway, CacheStore cacheStore, AiClient aiClient,
                            SimulatorClient simulatorClient) {
        this.mongoTemplate = mongoTemplate;
        this.mqttGateway = mqttGateway;
        this.cacheStore = cacheStore;
        this.aiClient = aiClient;
        this.simulatorClient = simulatorClient;
    }

    @GetMapping("/health")
    @Operation(summary = "Estado del servicio")
    public ResponseEntity<Map<String, String>> health() {
        boolean database = pingDatabase();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", database ? "UP" : "DOWN");
        body.put("database", database ? "UP" : "DOWN");
        body.put("broker", !mqttGateway.isEnabled() ? "DISABLED" : mqttGateway.isConnected() ? "UP" : "DOWN");
        body.put("cache", cacheStore.isRedisAvailable() ? "UP" : "DOWN");
        body.put("ai", !aiClient.isEnabled() ? "DISABLED" : aiClient.isHealthy() ? "UP" : "DOWN");
        body.put("simulator", !simulatorClient.isAvailable() ? "DISABLED" : simulatorClient.isHealthy() ? "UP" : "DOWN");
        return ResponseEntity.status(database ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean pingDatabase() {
        try {
            Document result = mongoTemplate.executeCommand(new Document("ping", 1));
            return result.get("ok") instanceof Number ok && ok.doubleValue() >= 1.0;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
