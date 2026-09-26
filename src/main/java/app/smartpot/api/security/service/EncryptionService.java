package app.smartpot.api.security.service;

import app.smartpot.api.config.SmartPotProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado AES-256-GCM con IV aleatorio por operación. El resultado es Base64 URL-safe de IV + texto cifrado.
 */
@Service
public class EncryptionService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public EncryptionService(SmartPotProperties properties) {
        byte[] decoded = Base64.getDecoder().decode(properties.security().aesKey());
        if (decoded.length != 32) {
            throw new IllegalStateException("SMARTPOT_AES_KEY debe ser Base64 de 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] output = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(output);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar el dato", ex);
        }
    }

    public String decrypt(String cipherText) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cipherText);
            if (decoded.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Texto cifrado incompleto");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, decoded, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(decoded, IV_LENGTH, decoded.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("No se pudo descifrar el dato", ex);
        }
    }
}
