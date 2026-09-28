package app.smartpot.api.mqtt.service;

import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.repository.CropRepository;
import app.smartpot.api.mqtt.model.MqttConnectedEvent;
import app.smartpot.api.security.service.EncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Administra las cuentas de dispositivo en Mosquitto con el plugin de seguridad dinámica:
 * un cliente por cultivo (usuario = id del cultivo) con el rol «device».
 */
@Slf4j
@Component
public class DeviceProvisioner {

    static final String DEVICE_ROLE = "device";
    static final String ADMIN_ROLE = "admin";
    private static final int BATCH_SIZE = 40;
    private static final long RESPONSE_TIMEOUT_SECONDS = 5;

    private final MqttGateway gateway;
    private final MqttTopicResolver topics;
    private final CropRepository cropRepository;
    private final EncryptionService encryptionService;
    private final JsonMapper jsonMapper;
    private final Map<String, CompletableFuture<List<JsonNode>>> pending = new ConcurrentHashMap<>();

    public DeviceProvisioner(MqttGateway gateway, MqttTopicResolver topics, CropRepository cropRepository,
                             EncryptionService encryptionService, JsonMapper jsonMapper) {
        this.gateway = gateway;
        this.topics = topics;
        this.cropRepository = cropRepository;
        this.encryptionService = encryptionService;
        this.jsonMapper = jsonMapper;
    }

    static boolean isIgnorable(String error) {
        String normalized = error.toLowerCase();
        return normalized.contains("already") || normalized.contains("not found");
    }

    private static Map<String, Object> acl(String role, String type, String topic) {
        Map<String, Object> acl = command("addRoleACL", "rolename", role);
        acl.put("acltype", type);
        acl.put("topic", topic);
        acl.put("priority", 5);
        acl.put("allow", true);
        return acl;
    }

    private static Map<String, Object> command(String name, String keyField, String keyValue) {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("command", name);
        command.put(keyField, keyValue);
        return command;
    }

    @Async
    @EventListener
    public void onConnected(MqttConnectedEvent event) {
        ensureRoles();
        List<Crop> crops = cropRepository.findByDeviceKeyCiphertextNotNull();
        List<Map<String, Object>> commands = new ArrayList<>();
        for (Crop crop : crops) {
            try {
                String key = encryptionService.decrypt(crop.getDevice().getKeyCiphertext());
                commands.addAll(upsertClient(crop.getId(), key));
            } catch (IllegalStateException ex) {
                log.warn("No se pudo descifrar la clave del dispositivo {}; hay que rotarla", crop.getId());
            }
        }
        for (int from = 0; from < commands.size(); from += BATCH_SIZE) {
            send(commands.subList(from, Math.min(commands.size(), from + BATCH_SIZE)));
        }
        log.info("Broker sincronizado: {} dispositivos aprovisionados", crops.size());
    }

    public void ensureRoles() {
        List<Map<String, Object>> commands = new ArrayList<>();
        commands.add(command("createRole", "rolename", DEVICE_ROLE));
        for (String topic : topics.devicePublishPatterns()) {
            commands.add(acl(DEVICE_ROLE, "publishClientSend", topic));
        }
        commands.add(acl(DEVICE_ROLE, "subscribePattern", topics.deviceSubscribePattern()));
        commands.add(acl(ADMIN_ROLE, "publishClientSend", topics.serviceScope()));
        send(commands);
    }

    /**
     * Crea o actualiza la cuenta del dispositivo. Con kick=true desconecta la sesión anterior.
     */
    public boolean provision(String cropId, String key, boolean kick) {
        if (!gateway.isEnabled()) {
            return false;
        }
        List<Map<String, Object>> commands = new ArrayList<>(upsertClient(cropId, key));
        if (kick) {
            commands.add(command("disableClient", "username", cropId));
            commands.add(command("enableClient", "username", cropId));
        }
        return send(commands);
    }

    public boolean deprovision(String cropId) {
        if (!gateway.isEnabled()) {
            return false;
        }
        return send(List.of(command("deleteClient", "username", cropId)));
    }

    public void onControlResponse(String payload) {
        JsonNode responses = jsonMapper.readTree(payload).path("responses");
        Map<String, List<JsonNode>> byBatch = new LinkedHashMap<>();
        for (JsonNode response : responses) {
            String correlation = response.path("correlationData").asString("");
            String batch = correlation.contains(":") ? correlation.substring(0, correlation.indexOf(':')) : correlation;
            byBatch.computeIfAbsent(batch, key -> new ArrayList<>()).add(response);
            String error = response.path("error").asString("");
            if (!error.isEmpty() && !isIgnorable(error)) {
                log.warn("El broker rechazó «{}»: {}", response.path("command").asString(""), error);
            }
        }
        byBatch.forEach((batch, list) -> {
            CompletableFuture<List<JsonNode>> future = pending.remove(batch);
            if (future != null) {
                future.complete(list);
            }
        });
    }

    private List<Map<String, Object>> upsertClient(String cropId, String key) {
        Map<String, Object> create = command("createClient", "username", cropId);
        create.put("password", key);
        create.put("roles", List.of(Map.of("rolename", DEVICE_ROLE)));
        // Si el cliente ya existe, createClient falla sin efectos y setClientPassword deja la clave vigente.
        Map<String, Object> password = command("setClientPassword", "username", cropId);
        password.put("password", key);
        return List.of(create, password);
    }

    private boolean send(List<Map<String, Object>> commands) {
        if (commands.isEmpty() || !gateway.isConnected()) {
            return false;
        }
        String batch = UUID.randomUUID().toString();
        List<Map<String, Object>> tagged = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            Map<String, Object> copy = new LinkedHashMap<>(commands.get(i));
            copy.put("correlationData", batch + ":" + i);
            tagged.add(copy);
        }
        CompletableFuture<List<JsonNode>> future = new CompletableFuture<>();
        pending.put(batch, future);
        String payload = jsonMapper.writeValueAsString(Map.of("commands", tagged));
        if (!gateway.publish(MqttTopicResolver.CONTROL_TOPIC, payload, 1, false)) {
            pending.remove(batch);
            return false;
        }
        try {
            future.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception ex) {
            pending.remove(batch);
            log.warn("El broker no respondió a tiempo a {} comandos de seguridad", commands.size());
            return false;
        }
    }
}
