package app.smartpot.api.mqtt.model;

public record SimpleAckMessage(boolean successful, String response) {
}
