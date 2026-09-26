package app.smartpot.api.users.validator;

import app.smartpot.api.exception.ApiException;

import java.nio.charset.StandardCharsets;

/**
 * Mínimo 8 caracteres con mayúscula, minúscula y número. BCrypt solo usa los primeros 72 bytes.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw ApiException.badRequest("La contraseña debe tener al menos 8 caracteres");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw ApiException.badRequest("La contraseña es demasiado larga (máximo 72 bytes)");
        }
        boolean upper = password.chars().anyMatch(Character::isUpperCase);
        boolean lower = password.chars().anyMatch(Character::isLowerCase);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!upper || !lower || !digit) {
            throw ApiException.badRequest("La contraseña debe incluir una mayúscula, una minúscula y un número");
        }
    }
}
