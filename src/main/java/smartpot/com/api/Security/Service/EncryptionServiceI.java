package smartpot.com.api.Security.Service;

public interface EncryptionServiceI {
    public String encrypt(String plainText) throws Exception;
    public String decrypt(String cipherText) throws Exception;
}
