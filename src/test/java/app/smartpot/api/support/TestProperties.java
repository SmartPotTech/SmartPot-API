package app.smartpot.api.support;

import app.smartpot.api.config.SmartPotProperties;
import app.smartpot.api.mqtt.config.MqttProperties;

import java.time.Duration;
import java.util.List;

public final class TestProperties {

    public static final String AES_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    public static final String JWT_SECRET = "test-secret-for-smartpot-api-unit-tests-0123456789";

    private TestProperties() {
    }

    public static SmartPotProperties smartPot() {
        return new SmartPotProperties(
                "http://localhost:5173",
                new SmartPotProperties.Security(JWT_SECRET, Duration.ofHours(1), AES_KEY,
                        List.of("http://localhost:5173"), 5, 2, Duration.ofMinutes(30)),
                new SmartPotProperties.Readings(Duration.ofSeconds(5), 2000),
                new SmartPotProperties.Mail(false, "SmartPot <no-reply@smartpot.test>"));
    }

    public static MqttProperties mqtt() {
        return new MqttProperties(true, "tcp://localhost:1883", "smartpot-api-test", "smartpot-api", "secret",
                "smartpot/v1", "mqtt.smartpot.test", 8883, true, "wss://mqtt.smartpot.test/mqtt", Duration.ofMinutes(2));
    }
}
