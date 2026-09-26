package app.smartpot.api.security.service;

import app.smartpot.api.config.SmartPotProperties;
import app.smartpot.api.support.TestProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    private final EncryptionService service = new EncryptionService(TestProperties.smartPot());

    @Test
    void encryptsAndDecryptsTheSameValue() {
        String cipher = service.encrypt("clave-del-dispositivo");

        assertThat(cipher).isNotEqualTo("clave-del-dispositivo");
        assertThat(service.decrypt(cipher)).isEqualTo("clave-del-dispositivo");
    }

    @Test
    void usesARandomIvSoEqualValuesProduceDifferentCiphertexts() {
        assertThat(service.encrypt("igual")).isNotEqualTo(service.encrypt("igual"));
    }

    @Test
    void rejectsTamperedCiphertext() {
        String cipher = service.encrypt("dato");
        String tampered = cipher.substring(0, cipher.length() - 2) + (cipher.endsWith("A") ? "BB" : "AA");

        assertThatThrownBy(() -> service.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesKeysThatAreNot256Bits() {
        SmartPotProperties base = TestProperties.smartPot();
        SmartPotProperties shortKey = new SmartPotProperties(base.webBaseUrl(),
                new SmartPotProperties.Security(TestProperties.JWT_SECRET, Duration.ofHours(1), "c2hvcnQ=",
                        List.of(), 1, 1, Duration.ofMinutes(1)), base.readings(), base.mail());

        assertThatThrownBy(() -> new EncryptionService(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
