package smartpot.com.api.Validation.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ApiHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse> handleCustomException(ApiException ex) {
        return new ResponseEntity<>(ex.getApiResponse(), HttpStatus.valueOf(ex.getApiResponse().getStatus()));
    }
}
