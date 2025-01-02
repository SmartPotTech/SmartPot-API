package smartpot.com.api.Users.Validator;

/**
 * Contiene las expresiones regulares para la validación de los campos de usuario.
 * <p>
 * Esta clase define una serie de constantes que representan las expresiones regulares (regex) utilizadas
 * para validar diversos campos del usuario, como el nombre, apellido, correo electrónico y contraseña.
 * Estas expresiones regulares se usan en los métodos de validación de la clase {@link VUser} y otras clases relacionadas.
 * </p>
 */
public class UserRegex {
    /**
     * Expresión regular para validar el nombre de un usuario.
     * <p>
     * El nombre debe estar compuesto únicamente por letras (mayúsculas y minúsculas),
     * incluyendo caracteres acentuados y especiales. La longitud debe ser de 4 a 15 caracteres.
     * </p>
     */
    public static final String NAME_PATTERN = "^[a-zA-ZÁ-ÿá-ÿ]{4,15}$";

    /**
     * Expresión regular para validar el apellido de un usuario.
     * <p>
     * El apellido debe estar compuesto únicamente por letras (mayúsculas y minúsculas),
     * incluyendo caracteres acentuados y especiales. La longitud debe ser de 4 a 30 caracteres.
     * </p>
     */
    public static final String LASTNAME_PATTERN = "^[a-zA-ZÁ-ÿá-ÿ]{4,30}$";

    /**
     * Expresión regular para validar la dirección de correo electrónico de un usuario.
     * <p>
     * El correo electrónico debe seguir el formato estándar: algo@dominio.com.
     * No se permiten espacios y debe haber un solo carácter '@' separando el nombre del dominio.
     * </p>
     */
    public static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    /**
     * Expresión regular para validar la contraseña de un usuario.
     * <p>
     * La contraseña debe cumplir con los siguientes requisitos:
     * - Al menos 8 caracteres de longitud.
     * - Debe contener al menos una letra mayúscula.
     * - Debe contener al menos una letra minúscula.
     * - Debe contener al menos un número.
     * - Debe contener al menos un carácter especial (como @$!%*?&).
     * </p>
     */
    public static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";
}
