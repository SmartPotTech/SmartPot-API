package smartpot.com.api.Mail.Service;

import smartpot.com.api.Mail.Model.DTO.EmailDTO;

import java.util.List;

public interface EmailServiceI {

    EmailDTO sendSimpleMail(EmailDTO emailDTO);

    EmailDTO sendMailWithAttachment(EmailDTO emailDTO);

    List<EmailDTO> getAllMails() throws Exception;

    EmailDTO scheduleEmail(EmailDTO emailDTO);
}
