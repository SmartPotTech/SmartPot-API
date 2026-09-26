package app.smartpot.api.users.validator;

public final class UserPatterns {

    /** Letras (con tildes y ñ), espacios, apóstrofo y guion. */
    public static final String PERSON_NAME = "^[\\p{L}][\\p{L} '\\-]*$";

    private UserPatterns() {
    }
}
