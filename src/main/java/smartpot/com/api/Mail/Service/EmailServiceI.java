package smartpot.com.api.Mail.Service;

import smartpot.com.api.Mail.Model.DTO.EmailDTO;
import smartpot.com.api.Mail.Model.Entity.EmailDetails;

import java.util.List;

public interface EmailServiceI {

    void sendSimpleMail(EmailDetails details);

    void sendMailWithAttachment(EmailDetails details);

    List<EmailDTO> getAllMails() throws Exception;
}
