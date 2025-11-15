package app.smartpot.api.mail.service;

import app.smartpot.api.mail.model.dto.EmailDTO;

import java.util.List;

public interface EmailService {

    EmailDTO sendSimpleMail(EmailDTO emailDTO);

    EmailDTO sendMailWithAttachment(EmailDTO emailDTO);

    List<EmailDTO> getAllMails() throws Exception;

    EmailDTO scheduleEmail(EmailDTO emailDTO);
}
