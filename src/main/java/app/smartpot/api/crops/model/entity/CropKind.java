package app.smartpot.api.crops.model.entity;

/**
 * Origen de las lecturas de un cultivo, fijado al crearlo. REAL: un dispositivo con firmware (un ESP32 físico o
 * simulado en Wokwi) se conecta con las credenciales del cultivo. VIRTUAL: SmartPot lo simula; no expone
 * credenciales.
 */
public enum CropKind {
    REAL, VIRTUAL
}
