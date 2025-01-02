package smartpot.com.api.Mail.Model.DAO.Service;

import smartpot.com.api.Mail.Model.Entity.EmailDetails;

public interface EmailServiceI {

    String sendSimpleMail(EmailDetails details);

    String sendMailWithAttachment(EmailDetails details);
}
