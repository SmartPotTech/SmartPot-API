package smartpot.com.api.Security.Model.DTO;

import lombok.Data;
import java.util.Date;

@Data
public class ResetTokenDTO {
    private String token;
    private String operation;
    private Date expiration;

    public ResetTokenDTO(String token, String operation, Date expiration) {
        this.token = token;
        this.operation = operation;
        this.expiration = expiration;
    }
}
