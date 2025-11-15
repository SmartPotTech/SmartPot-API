package app.smartpot.api.exception;

public class EncryptionException extends RuntimeException {
    public EncryptionException(String message) {
        super(message);
    }
}