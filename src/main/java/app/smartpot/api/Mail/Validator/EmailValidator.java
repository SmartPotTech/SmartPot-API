package app.smartpot.api.Mail.Validator;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class EmailValidator implements EmailValidatorI {
    private static final String EMAIL_PATTERN =
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    private static final int SUBJECT_MAX_LENGTH = 100;
    private static final int BODY_MAX_LENGTH = 1000;
    private boolean valid;
    private List<String> errors;

    public EmailValidator() {
        this.valid = true;
        this.errors = new ArrayList<>();
    }

    @Override
    public void reset() {
        this.valid = true;
        this.errors = new ArrayList<>();
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public List<String> getErrors() {
        List<String> currentErrors = errors;
        reset();
        return currentErrors;
    }

    @Override
    public void validateId(String id) {
        if (id == null || id.isEmpty()) {
            errors.add("El Id no puede estar vacío.");
            valid = false;
        } else if (!ObjectId.isValid(id)) {
            errors.add("El Id debe ser un hexadecimal de 24 caracteres.");
            valid = false;
        }
    }

    @Override
    public void validateRecipient(String recipient) {
        if (recipient == null || recipient.isEmpty()) {
            errors.add("El destinatario no puede estar vacío.");
            valid = false;
        } else if (!Pattern.matches(EMAIL_PATTERN, recipient)) {
            errors.add("El correo del destinatario no es válido.");
            valid = false;
        }
    }

    @Override
    public void validateSubject(String subject) {
        if (subject != null && subject.length() > SUBJECT_MAX_LENGTH) {
            errors.add("El asunto no puede tener más de " + SUBJECT_MAX_LENGTH + " caracteres.");
            valid = false;
        }
    }

    @Override
    public void validateMsgBody(String msgBody) {
        if (msgBody != null && msgBody.length() > BODY_MAX_LENGTH) {
            errors.add("El mensaje no puede tener más de " + BODY_MAX_LENGTH + " caracteres.");
            valid = false;
        }
    }

    @Override
    public void validateAttachment(String attachment) {
        if (attachment != null && !attachment.isEmpty()) {
            if (!attachment.startsWith("http://") && !attachment.startsWith("https://")) {
                errors.add("El enlace del adjunto debe ser una URL válida (http o https).");
                valid = false;
            }
        }
    }
}
