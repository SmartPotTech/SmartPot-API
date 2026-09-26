package app.smartpot.api.mqtt.model;

/**
 * Confirmación de la maceta en {prefijo}/{cropId}/commands/ack. status: EXECUTED o FAILED.
 */
public record CommandAckMessage(String id, String status, String message) {

    public boolean executed() {
        return "EXECUTED".equalsIgnoreCase(status);
    }
}
