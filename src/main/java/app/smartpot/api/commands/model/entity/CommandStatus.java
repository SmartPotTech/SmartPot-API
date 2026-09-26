package app.smartpot.api.commands.model.entity;

/**
 * PENDING al crearse, SENT al publicarse en el broker, EXECUTED o FAILED según el ACK de la maceta,
 * y EXPIRED si la maceta no responde dentro del tiempo límite.
 */
public enum CommandStatus {
    PENDING, SENT, EXECUTED, FAILED, EXPIRED
}
