package app.smartpot.api.channels.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bot de Telegram de SmartPot. Sin token el canal queda deshabilitado.
 * mode «polling» consulta las novedades (sirve en local y en la demo); «webhook» recibe las novedades en
 * {@code webhookUrl}, validadas con {@code webhookSecret}.
 */
@ConfigurationProperties(prefix = "smartpot.telegram")
public record TelegramProperties(
        String botToken,
        String botUsername,
        String mode,
        String webhookUrl,
        String webhookSecret,
        String apiBaseUrl
) {

    public TelegramProperties {
        mode = mode == null || mode.isBlank() ? "polling" : mode.trim().toLowerCase();
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://api.telegram.org" : apiBaseUrl;
    }

    public boolean enabled() {
        return botToken != null && !botToken.isBlank() && botUsername != null && !botUsername.isBlank();
    }

    public boolean webhook() {
        return "webhook".equals(mode);
    }
}
