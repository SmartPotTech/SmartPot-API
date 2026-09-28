package app.smartpot.api.crops.model.entity;

/**
 * Dónde está el cultivo: bajo techo o al aire libre, cuánto sol recibe y en qué lugar. Sirve para ilustrarlo con el
 * clima de ese lugar, para simularlo (los virtuales) y para que el asistente recomiende otra posición cuando la
 * elegida afecta la salud de la especie. Todos los campos son opcionales.
 */
public record Placement(Setting setting, Exposure exposure, Location location) {

    public enum Setting {
        INDOOR, OUTDOOR
    }

    /** Al aire libre, horas de sol directo; bajo techo, qué tan soleada es su ventana. */
    public enum Exposure {
        FULL_SUN, PARTIAL_SUN, SHADE
    }

    public record Location(String name, double latitude, double longitude) {
    }

    public Placement withLocation(Location newLocation) {
        return new Placement(setting, exposure, newLocation);
    }

    public boolean isOutdoor() {
        return setting == Setting.OUTDOOR;
    }
}
