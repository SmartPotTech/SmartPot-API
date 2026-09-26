package app.smartpot.api.mail.service;

import app.smartpot.api.config.SmartPotProperties;
import app.smartpot.api.users.model.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final SmartPotProperties properties;

    public MailService(JavaMailSender mailSender, SmartPotProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async
    public void sendWelcome(User user) {
        String loginUrl = properties.webBaseUrl() + "/login";
        send(user.getEmail(), "Bienvenido a SmartPot", MailTemplates.welcome(user.getName(), loginUrl));
    }

    @Async
    public void sendPasswordReset(User user, String token, Duration validity) {
        String resetUrl = properties.webBaseUrl() + "/reset-password?token=" + token;
        send(user.getEmail(), "Restablece tu contraseña de SmartPot",
                MailTemplates.passwordReset(user.getName(), resetUrl, validity.toMinutes()));
    }

    private void send(String to, String subject, MailTemplates.Content content) {
        if (!properties.mail().enabled()) {
            log.info("Correo deshabilitado; no se envía «{}»", subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.mail().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content.text(), content.html());
            mailSender.send(message);
            log.info("Correo «{}» enviado", subject);
        } catch (MessagingException | MailException ex) {
            log.warn("No se pudo enviar el correo «{}»: {}", subject, ex.getMessage());
        }
    }
}
