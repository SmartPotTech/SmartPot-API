package app.smartpot.api.mqtt.service;

import app.smartpot.api.crops.repository.CropRepository;
import app.smartpot.api.security.service.EncryptionService;
import app.smartpot.api.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceProvisionerTest {

    private static final String CROP = "6718f0a1b2c3d4e5f6a7b8c9";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final MqttGateway gateway = mock(MqttGateway.class);
    private final List<String> published = new ArrayList<>();
    private DeviceProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new DeviceProvisioner(gateway, new MqttTopicResolver(TestProperties.mqtt()),
                mock(CropRepository.class), new EncryptionService(TestProperties.smartPot()), jsonMapper);
        when(gateway.isEnabled()).thenReturn(true);
        when(gateway.isConnected()).thenReturn(true);
        when(gateway.publish(eq(MqttTopicResolver.CONTROL_TOPIC), anyString(), anyInt(), anyBoolean()))
                .thenAnswer(invocation -> {
                    String payload = invocation.getArgument(1);
                    published.add(payload);
                    answer(payload);
                    return true;
                });
    }

    @Test
    void provisionsTheDeviceWithItsCropIdAsUsername() {
        assertThat(provisioner.provision(CROP, "clave-secreta", false)).isTrue();

        JsonNode commands = jsonMapper.readTree(published.getFirst()).path("commands");
        assertThat(commands.get(0).path("command").asString()).isEqualTo("createClient");
        assertThat(commands.get(0).path("username").asString()).isEqualTo(CROP);
        assertThat(commands.get(0).path("password").asString()).isEqualTo("clave-secreta");
        assertThat(commands.get(0).path("roles").get(0).path("rolename").asString()).isEqualTo("device");
        assertThat(commands).hasSize(2);
    }

    @Test
    void rotationDisconnectsTheOldSession() {
        provisioner.provision(CROP, "nueva", true);

        List<String> names = new ArrayList<>();
        jsonMapper.readTree(published.getFirst()).path("commands").forEach(c -> names.add(c.path("command").asString()));
        assertThat(names).containsSubsequence("setClientPassword", "disableClient", "enableClient");
    }

    @Test
    void rolesRestrictDevicesToTheirOwnTopics() {
        provisioner.ensureRoles();

        List<String> topics = new ArrayList<>();
        jsonMapper.readTree(published.getFirst()).path("commands").forEach(c -> topics.add(c.path("topic").asString("")));
        assertThat(topics).contains("smartpot/v1/%u/telemetry", "smartpot/v1/%u/commands", "smartpot/v1/#");
    }

    @Test
    void doesNothingWhenTheBrokerIsDisconnected() {
        when(gateway.isConnected()).thenReturn(false);

        assertThat(provisioner.deprovision(CROP)).isFalse();
        assertThat(published).isEmpty();
    }

    @Test
    void toleratesIdempotentErrors() {
        assertThat(DeviceProvisioner.isIgnorable("Client already exists")).isTrue();
        assertThat(DeviceProvisioner.isIgnorable("Client not found")).isTrue();
        assertThat(DeviceProvisioner.isIgnorable("Invalid password")).isFalse();
    }

    private void answer(String payload) {
        StringBuilder responses = new StringBuilder("{\"responses\":[");
        JsonNode commands = jsonMapper.readTree(payload).path("commands");
        for (int i = 0; i < commands.size(); i++) {
            JsonNode command = commands.get(i);
            responses.append(i == 0 ? "" : ",").append("{\"command\":\"").append(command.path("command").asString())
                    .append("\",\"correlationData\":\"").append(command.path("correlationData").asString()).append("\"}");
        }
        String response = responses.append("]}").toString();
        new Thread(() -> provisioner.onControlResponse(response)).start();
    }
}
