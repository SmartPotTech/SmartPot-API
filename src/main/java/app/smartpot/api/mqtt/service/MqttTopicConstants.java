package app.smartpot.api.mqtt.service;

import java.util.Set;

public final class MqttTopicConstants {
    private MqttTopicConstants() {}

    public static final String WILDCARD_SINGLE = "+";
    public static final String WILDCARD_MULTI = "#";

    public static final String SEGMENT_SENSORS = "sensors";
    public static final String SEGMENT_ACTUATORS = "actuators";
    public static final String SEGMENT_COMMANDS = "commands";
    public static final String SEGMENT_ACK = "ack";

    public static final String SENSOR_BME280 = "bme280";
    public static final String SENSOR_LUX = "lux";
    public static final String SENSOR_PH = "ph";
    public static final String SENSOR_TDS = "tds";

    public static final Set<String> SUPPORTED_SENSORS = Set.of(
            SENSOR_BME280, SENSOR_LUX, SENSOR_PH, SENSOR_TDS
    );
}
