package app.smartpot.api.Mail.Service;

import app.smartpot.api.Mail.Model.DTO.EmailDTO;

import java.util.List;

public interface EmailServiceI {

    EmailDTO sendSimpleMail(EmailDTO emailDTO);

    EmailDTO sendMailWithAttachment(EmailDTO emailDTO);

    List<EmailDTO> getAllMails() throws Exception;

    EmailDTO scheduleEmail(EmailDTO emailDTO);
}
