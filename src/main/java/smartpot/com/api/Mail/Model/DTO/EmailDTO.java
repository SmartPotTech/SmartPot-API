package smartpot.com.api.Mail.Model.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class EmailDTO {
    private String id;
    private String recipient;
    private String msgBody;
    private String subject;
    private String attachment;
}
