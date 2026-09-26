package app.smartpot.api.users.validator;

import app.smartpot.api.exception.ApiException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Tomate2026", "LechugaVerde9", "Contraseña1"})
    void acceptsPasswordsWithUpperLowerAndDigit(String password) {
        assertThatCode(() -> PasswordPolicy.validate(password)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"corta1A", "sinmayuscula1", "SINMINUSCULA1", "SinNumeros", ""})
    void rejectsWeakPasswords(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password)).isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {73, 120})
    void rejectsPasswordsLongerThanTheBcryptLimit(int length) {
        String password = "Aa1" + "x".repeat(length - 3);

        assertThatThrownBy(() -> PasswordPolicy.validate(password))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("72 bytes");
    }
}
