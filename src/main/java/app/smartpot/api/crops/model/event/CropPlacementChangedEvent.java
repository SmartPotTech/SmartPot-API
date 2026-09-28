package app.smartpot.api.crops.model.event;

/**
 * El cultivo cambió de lugar o de exposición: su simulación, si la tiene, debe tomar el nuevo entorno.
 */
public record CropPlacementChangedEvent(String cropId) {
}
