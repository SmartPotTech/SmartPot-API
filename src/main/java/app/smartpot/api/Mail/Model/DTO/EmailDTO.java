package app.smartpot.api.Mail.Model.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class EmailDTO implements Serializable {
    private String id;
    private String recipient;
    private String msgBody;
    private String subject;
    private String attachment;
    private String sendDate;
    private String sent;
}
