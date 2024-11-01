package smartpot.com.api.utilitys;

import lombok.Getter;

@Getter
public class Exception extends RuntimeException {
    private final ErrorResponse errorResponse;

    public Exception(ErrorResponse errorResponse) {
        super(errorResponse.getMessage());
        this.errorResponse = errorResponse;
    }
}
