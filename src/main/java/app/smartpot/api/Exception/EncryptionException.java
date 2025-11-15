package app.smartpot.api.Exception;

public class EncryptionException extends RuntimeException {
    public EncryptionException(String message) {
        super(message);
    }
}