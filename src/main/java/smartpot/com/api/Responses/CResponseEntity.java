package smartpot.com.api.Responses;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import java.io.Serial;
import java.io.Serializable;

public class CResponseEntity<T> extends ResponseEntity<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public CResponseEntity() {
        super((T) null, HttpStatus.OK);
    }

    public CResponseEntity(T body, HttpStatus status) {
        super(body, status);
    }

    public CResponseEntity(MultiValueMap<String, String> headers, HttpStatus status) {
        super(headers, status);
    }

    public CResponseEntity(T body, MultiValueMap<String, String> headers, HttpStatus status) {
        super(body, headers, status);
    }
}
