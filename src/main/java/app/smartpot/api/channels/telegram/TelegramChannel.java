package app.smartpot.api.channels.telegram;

import app.smartpot.api.channels.config.TelegramProperties;
import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.channels.service.ChannelMessage;
import app.smartpot.api.channels.service.NotificationChannel;
import app.smartpot.api.notifications.model.entity.NotificationType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TelegramChannel implements NotificationChannel {

    private static final Map<NotificationType, String> ICONS = Map.of(
            NotificationType.ALERT, "⚠️",
            NotificationType.DEVICE, "📡",
            NotificationType.AI, "🤖",
            NotificationType.COMMAND, "💧",
            NotificationType.INFO, "🌱");

    private final TelegramProperties properties;
    private final TelegramClient client;

    public TelegramChannel(TelegramProperties properties, TelegramClient client) {
        this.properties = properties;
        this.client = client;
    }

    static String format(ChannelMessage message) {
        String icon = ICONS.getOrDefault(message.type(), "🌱");
        return icon + " <b>" + escape(message.title()) + "</b>\n" + escape(message.body());
    }

    /**
     * Telegram solo reconoce &amp;, &lt;, &gt; y &quot;: las tildes y la ñ van tal cual.
     */
    static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public ChannelType type() {
        return ChannelType.TELEGRAM;
    }

    @Override
    public boolean isAvailable() {
        return properties.enabled();
    }

    @Override
    public String displayName() {
        return "Telegram";
    }

    @Override
    public String handle() {
        return "@" + username();
    }

    @Override
    public String linkUrl(String code) {
        return "https://t.me/" + username() + "?start=" + code;
    }

    @Override
    public String description() {
        return "Recibe las alertas, los resúmenes y las acciones del asistente en un chat con el bot de SmartPot.";
    }

    @Override
    public List<String> requirements() {
        return List.of("TELEGRAM_BOT_TOKEN", "TELEGRAM_BOT_USERNAME");
    }

    @Override
    public void send(String address, ChannelMessage message) {
        client.sendMessage(address, format(message), "Abrir en SmartPot", message.url());
    }

    private String username() {
        return properties.botUsername().replaceFirst("^@", "");
    }
}
