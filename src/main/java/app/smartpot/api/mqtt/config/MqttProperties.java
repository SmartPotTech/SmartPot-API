package app.smartpot.api.mqtt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "application.mqtt")
public class MqttProperties {
    private boolean enabled = true;
    private String brokerUri = "ssl://localhost:8883";
    private String clientId = "smartpot-api";
    private String username = "";
    private String password = "";
    private boolean cleanSession = false;
    private int keepAliveSeconds = 60;
    private int connectionTimeoutSeconds = 10;
    private int commandQos = 1;
    private int telemetryQos = 0;
    private String topicPrefix = "smartpot";
    private String topicVersion = "v1";
}
