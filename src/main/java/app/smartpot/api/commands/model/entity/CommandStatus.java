package app.smartpot.api.commands.model.entity;

/**
 * PENDING al crearse, SENT al publicarse en el broker, EXECUTED o FAILED según el ACK del dispositivo,
 * y EXPIRED si el dispositivo no responde dentro del tiempo límite.
 */
public enum CommandStatus {
    PENDING, SENT, EXECUTED, FAILED, EXPIRED
}
