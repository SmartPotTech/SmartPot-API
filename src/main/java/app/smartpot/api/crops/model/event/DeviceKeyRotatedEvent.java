package app.smartpot.api.crops.model.event;

/** La clave de la maceta cambió: quien la use (por ejemplo, la maceta virtual) debe tomar la nueva. */
public record DeviceKeyRotatedEvent(String cropId) {
}
