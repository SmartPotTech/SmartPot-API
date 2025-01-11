package smartpot.com.api.Mail.Model.DAO.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import smartpot.com.api.Mail.Mapper.EmailMapper;
import smartpot.com.api.Mail.Model.DAO.Repository.EmailRepository;
import smartpot.com.api.Mail.Model.DTO.EmailDTO;
import smartpot.com.api.Mail.Model.Entity.EmailDetails;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmailService implements EmailServiceI {
    private final JavaMailSender javaMailSender;
    private final EmailRepository emailRepository;
    private final EmailMapper emailMapper;
    @Value("${spring.mail.username}")
    private String sender;

    @Autowired
    public EmailService(JavaMailSender javaMailSender, EmailRepository emailRepository, EmailMapper emailMapper) {
        this.javaMailSender = javaMailSender;
        this.emailRepository = emailRepository;
        this.emailMapper = emailMapper;
    }

    @Override
    @Async
    public void sendSimpleMail(EmailDetails details) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(sender);
            mailMessage.setTo(details.getRecipient());
            mailMessage.setText(details.getMsgBody());
            mailMessage.setSubject(details.getSubject());
            javaMailSender.send(mailMessage);
            emailRepository.save(details);
            log.warn("Correo Enviado Exitosamente");
        } catch (Exception e) {
            log.error("Error al Enviar Correo " + e.getMessage());
        }
    }

    @Override
    @Async
    public void sendMailWithAttachment(EmailDetails details) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper mimeMessageHelper;
        try {
            mimeMessageHelper = new MimeMessageHelper(mimeMessage, true);
            mimeMessageHelper.setFrom(sender);
            mimeMessageHelper.setTo(details.getRecipient());
            mimeMessageHelper.setText(details.getMsgBody());
            mimeMessageHelper.setSubject(details.getSubject());
            FileSystemResource file = new FileSystemResource(new File(details.getAttachment()));
            mimeMessageHelper.addAttachment(Objects.requireNonNull(file.getFilename()), file);
            javaMailSender.send(mimeMessage);
            emailRepository.save(details);
            log.warn("Correo Enviado Exitosamente");
        } catch (Exception e) {
            log.error("Error al Enviar Correo " + e.getMessage());
        }
    }

    /**
     * Obtiene todos los correos registrados en la base de datos y los convierte en objetos DTO.
     *
     * <p>Este método consulta todos los correos almacenados en la base de datos mediante el repositorio {@link EmailRepository}.
     * Si la lista de correos está vacía, se lanza una excepción. Los correos obtenidos se mapean a objetos
     * {@link EmailDTO} utilizando el convertidor {@link EmailMapper}.</p>
     *
     * @return Una lista de objetos {@link EmailDTO} que representan todos los correos en la base de datos.
     * @throws Exception Si no existen correos registrados en la base de datos.
     * @see EmailDTO
     * @see EmailRepository
     * @see EmailMapper
     */
    @Override
    public List<EmailDTO> getAllMails() throws Exception {
        return Optional.of(emailRepository.findAll())
                .filter(emails -> !emails.isEmpty())
                .map(emails -> emails.stream()
                        .map(emailMapper::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No existe ningún correo"));
    }
}
