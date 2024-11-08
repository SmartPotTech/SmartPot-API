package smartpot.com.api.Exception;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final ApiResponse apiResponse;

    public ApiException(ApiResponse apiResponse) {
        super(apiResponse.getMessage());
        this.apiResponse = apiResponse;
    }
}
