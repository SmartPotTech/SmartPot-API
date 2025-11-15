package app.smartpot.api.Mail.Validator;

import java.util.List;

/**
 * Interfaz para la validación de los datos de un email.
 * <p>
 * Esta interfaz define los métodos necesarios para validar los campos de un email, como el destinatario (recipient),
 * el cuerpo del mensaje (msgBody), el asunto (subject) y el archivo adjunto (attachment).
 * Implementada por clases que proporcionan la lógica de validación concreta, como {@link EmailValidator}.
 * </p>
 */
public interface EmailValidatorI {

    /**
     * Valida el ID del email.
     * <p>
     * El ID debe tener un formato hexadecimal de 24 caracteres (ObjectId).
     * </p>
     *
     * @param id El ID del email a validar.
     */
    void validateId(String id);

    /**
     * Válida el destinatario del email.
     * <p>
     * Debe ser una dirección de correo electrónico válida.
     * </p>
     *
     * @param recipient Dirección de correo electrónico del destinatario.
     */
    void validateRecipient(String recipient);

    /**
     * Válida el cuerpo del mensaje del email.
     * <p>
     * Puede ser opcional, pero si se proporciona, no debe superar una longitud máxima razonable.
     * </p>
     *
     * @param msgBody Cuerpo del mensaje.
     */
    void validateMsgBody(String msgBody);

    /**
     * Válida el asunto del email.
     * <p>
     * Debe ser un texto corto y no vacío. Se recomienda un límite de caracteres.
     * </p>
     *
     * @param subject Asunto del email.
     */
    void validateSubject(String subject);

    /**
     * Valida el nombre o ruta del archivo adjunto del email.
     * <p>
     * Puede ser opcional, pero si se incluye debe tener un formato válido (por ejemplo, nombre.extension).
     * </p>
     *
     * @param attachment Archivo adjunto.
     */
    void validateAttachment(String attachment);

    /**
     * Resetea el estado de la validación.
     * <p>
     * Limpia los errores y marca el validador como válido.
     * </p>
     */
    void reset();

    /**
     * Indica si todos los campos del email son válidos.
     *
     * @return <code>true</code> si todos los campos son válidos, <code>false</code> si hay errores.
     */
    boolean isValid();

    /**
     * Devuelve la lista de errores encontrados durante la validación.
     *
     * @return Lista de mensajes de error.
     */
    List<String> getErrors();
}

