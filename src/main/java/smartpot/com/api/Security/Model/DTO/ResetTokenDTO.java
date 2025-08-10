package smartpot.com.api.Security.Model.DTO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
public class ResetTokenDTO {
    private String token;
    private String operation;
    private Date expiration;

    public ResetTokenDTO(String token, String operation, Date expiration) {
        this.token = token;
        this.operation = operation;
        this.expiration = expiration;
    }

    static public String convertToJson(ResetTokenDTO resetToken) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(resetToken);
    }

    public static ResetTokenDTO convertToDTO(String json) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(json, ResetTokenDTO.class);
    }
}
