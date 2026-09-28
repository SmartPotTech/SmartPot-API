package app.smartpot.api.actuators.model.entity;

/** Cada actuador con su nombre en español para los mensajes («La bomba de agua ya está apagada»). */
public enum ActuatorType {
    WATER_PUMP("La bomba de agua", true),
    UV_LIGHT("La luz ultravioleta", true),
    FAN("El ventilador", false),
    HUMIDIFIER("El humidificador", false),
    NUTRIENT_DOSER("El dosificador de nutrientes", false),
    PH_DOSER("El dosificador de pH", false);

    private final String subject;
    private final boolean feminine;

    ActuatorType(String subject, boolean feminine) {
        this.subject = subject;
        this.feminine = feminine;
    }

    /** «La bomba de agua ya está encendida», «El ventilador ya está apagado». */
    public String alreadyIn(boolean on) {
        return subject + " ya está " + (on ? "encendid" : "apagad") + (feminine ? "a" : "o");
    }
}
