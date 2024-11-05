package smartpot.com.api.Validation;

public class RegexPatterns {
    /* * Patrón para nombres y apellidos (mínimo 4, máximo 15 caracteres) */
    public static final String NAME_PATTERN = "^[a-zA-Z]{4,15}$";

    /* * Patrón para apellidos (mínimo 4, máximo 30 caracteres) */
    public static final String LASTNAME_PATTERN = "^[a-zA-Z]{4,30}$";

    /* * Patrón para correos electrónicos */
    public static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    private RegexPatterns() {}
}
