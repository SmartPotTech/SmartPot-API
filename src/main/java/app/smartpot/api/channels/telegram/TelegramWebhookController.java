package app.smartpot.api.channels.telegram;

import app.smartpot.api.channels.config.TelegramProperties;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Entrada pública de los mensajes del bot en modo webhook. Telegram firma cada llamada con el encabezado
 * X-Telegram-Bot-Api-Secret-Token; sin el secreto correcto la llamada no existe (404).
 */
@Hidden
@RestController
public class TelegramWebhookController {

    public static final String PATH = "/channels/telegram/webhook";

    private final TelegramProperties properties;
    private final TelegramUpdatesListener listener;

    public TelegramWebhookController(TelegramProperties properties, TelegramUpdatesListener listener) {
        this.properties = properties;
        this.listener = listener;
    }

    @PostMapping(PATH)
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody TelegramUpdate update) {
        if (!properties.enabled() || !properties.webhook() || !matches(secret)) {
            return ResponseEntity.notFound().build();
        }
        listener.handleSafely(update);
        return ResponseEntity.ok().build();
    }

    private boolean matches(String secret) {
        String expected = properties.webhookSecret();
        return secret != null && expected != null && !expected.isBlank() && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8));
    }
}
