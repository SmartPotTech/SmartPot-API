package app.smartpot.api.crops.model.event;

/**
 * La clave del dispositivo cambió: quien la use (por ejemplo, la simulación de un cultivo virtual) debe tomar la nueva.
 */
public record DeviceKeyRotatedEvent(String cropId) {
}
