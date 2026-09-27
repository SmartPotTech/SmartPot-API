package app.smartpot.api.crops.model.event;

/** Un cultivo se eliminó con sus lecturas: los demás módulos limpian lo que guardan de él. */
public record CropDeletedEvent(String cropId, String ownerId) {
}
