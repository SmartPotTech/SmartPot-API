package app.smartpot.api.mail.service;

import app.smartpot.api.mail.mapper.EmailMapper;
import app.smartpot.api.mail.model.dto.EmailDTO;
import app.smartpot.api.mail.model.entity.EmailDetails;
import app.smartpot.api.mail.repository.EmailRepository;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {
    private final JavaMailSender javaMailSender;
    private final EmailRepository emailRepository;
    private final EmailMapper emailMapper;
    @Value("${spring.mail.username}")
    private String sender;

    @Autowired
    public EmailServiceImpl(JavaMailSender javaMailSender, EmailRepository emailRepository, EmailMapper emailMapper) {
        this.javaMailSender = javaMailSender;
        this.emailRepository = emailRepository;
        this.emailMapper = emailMapper;
    }

    @Override
    public EmailDTO sendSimpleMail(EmailDTO emailDTO) throws ValidationException {
        return Optional.of(emailDTO)
                .map(emailMapper::toEntity)
                .map(emailDetails -> {
                    try {
                        SimpleMailMessage mailMessage = new SimpleMailMessage();
                        mailMessage.setFrom(sender);
                        mailMessage.setTo(emailDetails.getRecipient());
                        mailMessage.setText(emailDetails.getMsgBody());
                        mailMessage.setSubject(emailDetails.getSubject());
                        javaMailSender.send(mailMessage);
                        emailRepository.save(emailDetails);
                    } catch (Exception e) {
                        throw new ValidationException("Error sending email " + e.getMessage());
                    }
                    return emailDetails;
                })
                .map(emailRepository::save)
                .map(emailMapper::toDTO)
                .orElseThrow(() -> new IllegalStateException("Error sending email"));
    }

    @Override
    public EmailDTO scheduleEmail(EmailDTO emailDTO) {
        return Optional.of(emailDTO)
                .map(emailMapper::toEntity)
                .map(emailDetails -> {
                    if (emailDetails.getSendDate() == null) {
                        throw new ValidationException("Send date must be provided.");
                    }
                    emailRepository.save(emailDetails);
                    return emailDetails;
                })
                .map(emailMapper::toDTO)
                .orElseThrow(() -> new IllegalStateException("Failed to schedule email"));
    }

    @Override
    public EmailDTO sendMailWithAttachment(EmailDTO emailDTO) {
        return Optional.of(emailDTO)
                .map(emailMapper::toEntity)
                .map(emailDetails -> {
                    MimeMessage mimeMessage = javaMailSender.createMimeMessage();
                    MimeMessageHelper mimeMessageHelper;
                    try {
                        mimeMessageHelper = new MimeMessageHelper(mimeMessage, true);
                        mimeMessageHelper.setFrom(sender);
                        mimeMessageHelper.setTo(emailDetails.getRecipient());
                        mimeMessageHelper.setText(emailDetails.getMsgBody());
                        mimeMessageHelper.setSubject(emailDetails.getSubject());
                        FileSystemResource file = new FileSystemResource(new File(emailDetails.getAttachment()));
                        mimeMessageHelper.addAttachment(Objects.requireNonNull(file.getFilename()), file);
                        javaMailSender.send(mimeMessage);
                        emailRepository.save(emailDetails);
                    } catch (Exception e) {
                        throw new ValidationException("Error sending email " + e.getMessage());
                    }
                    return emailDetails;
                })
                .map(emailRepository::save)
                .map(emailMapper::toDTO)
                .orElseThrow(() -> new IllegalStateException("Error sending email with attachment"));
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
    @Cacheable(value = "mails", key = "'all_mails'")
    public List<EmailDTO> getAllMails() throws Exception {
        return Optional.of(emailRepository.findAll())
                .filter(emails -> !emails.isEmpty())
                .map(emails -> emails.stream()
                        .map(emailMapper::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("There is no email"));
    }

    @Scheduled(fixedRate = 60000) // Runs every minute
    public void processScheduledEmails() {
        Date now = new Date();

        List<EmailDetails> emailsToSend = emailRepository.findAll().stream()
                .filter(email -> email.getSendDate() != null)
                .filter(email -> !email.getSent()) // Only unsent emails
                .filter(email -> {
                    long diff = email.getSendDate().getTime() - now.getTime();
                    return diff <= 10 * 60 * 1000 && diff > 0; // Within 10 minutes
                })
                .toList();

        emailsToSend.forEach(email -> {
            try {
                EmailDTO dto = emailMapper.toDTO(email);
                sendSimpleMail(dto);

                email.setSent(true);
                emailRepository.save(email);

                log.info("Scheduled email sent to {}", dto.getRecipient());
            } catch (Exception e) {
                log.error("Error sending scheduled email: {}", e.getMessage());
            }
        });
    }
}
