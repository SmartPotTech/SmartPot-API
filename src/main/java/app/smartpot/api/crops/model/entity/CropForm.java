package app.smartpot.api.crops.model.entity;

/** Forma del sistema hidropónico: define cómo se ilustra el cultivo en la PWA. */
public enum CropForm {
    /** Maceta individual con su depósito. */
    POT,
    /** Tubos horizontales con película de nutrientes (NFT). */
    NFT,
    /** Torre vertical con la solución que baja por gravedad. */
    TOWER,
    /** Balsa flotante sobre un estanque de solución (cultivo en agua profunda). */
    RAFT
}
