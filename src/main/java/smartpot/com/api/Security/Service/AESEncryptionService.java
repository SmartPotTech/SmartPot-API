package smartpot.com.api.Security.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import smartpot.com.api.Exception.EncryptionException;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AESEncryptionService implements EncryptionServiceI {

    SecureRandom random = new SecureRandom();
    @Value("${application.security.aes.key}")
    private String aesKey;

    public AESEncryptionService() {}

    private SecretKey getSecretKey() {
        byte[] decoded = Base64.getDecoder().decode(aesKey);
        if (decoded.length != 32) {
            throw new IllegalArgumentException("La clave debe tener 256 bits (32 bytes)");
        }
        return new SecretKeySpec(decoded, "AES");
    }

    @Override
    public String encrypt(String data) throws EncryptionException {
        try {
            // get salt
            byte[] salt = new byte[8];
            random.nextBytes(salt);
            String saltedData = Base64.getEncoder().encodeToString(salt) + ":" + data;

            byte[] iv = new byte[12];
            random.nextBytes(iv);
            SecretKey key = getSecretKey(); // get encryption key
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));

            byte[] encrypted = cipher.doFinal(saltedData.getBytes());

            byte[] output = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);
            return Base64.getUrlEncoder().encodeToString(output);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | // if you need more specific errors, catch each one separately
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new EncryptionException("Error while encrypting data");
        }

    }

    @Override
    public String decrypt(String encryptedData) throws EncryptionException {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encryptedData);

            byte[] iv = new byte[12];
            byte[] cipherText = new byte[decoded.length - 12];
            System.arraycopy(decoded, 0, iv, 0, 12);
            System.arraycopy(decoded, 12, cipherText, 0, cipherText.length);

            SecretKey key = getSecretKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            String result = new String(cipher.doFinal(cipherText));

            // remove salt
            return result.split(":", 2)[1];
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | // if you need more specific errors, catch each one separately
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new EncryptionException("Error while decrypting data");
        }

    }
}
