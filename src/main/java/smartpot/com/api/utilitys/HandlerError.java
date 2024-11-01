package smartpot.com.api.utilitys;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class HandlerError {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleCustomException(Exception ex) {
        return new ResponseEntity<>(ex.getErrorResponse(), HttpStatus.valueOf(ex.getErrorResponse().getStatus()));
    }
}
