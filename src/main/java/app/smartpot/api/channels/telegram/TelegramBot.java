package app.smartpot.api.channels.telegram;

import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.channels.service.ChannelDeliveryException;
import app.smartpot.api.channels.service.ChannelService;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.service.CropService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Conversación con el bot: /start con el código vincula el chat, /estado resume los cultivos,
 * /desvincular deja de enviar alertas y /ayuda explica lo demás. Solo atiende chats privados.
 */
@Slf4j
@Component
public class TelegramBot {

    static final String HELP = """
            Soy el asistente de <b>SmartPot</b> 🌱
            Te aviso aquí cuando un cultivo necesita atención, cuando una maceta se desconecta o cuando el \
            asistente actúa por su cuenta.

            /estado — cómo están tus cultivos
            /desvincular — dejar de recibir alertas en este chat
            /ayuda — este mensaje

            Para vincular este chat entra a SmartPot › Perfil › Notificaciones y toca «Vincular Telegram».""";
    static final String NOT_LINKED = "Este chat aún no está vinculado. Entra a SmartPot › Perfil › Notificaciones "
            + "y toca «Vincular Telegram».";

    private final ChannelService channelService;
    private final CropService cropService;
    private final TelegramClient client;

    public TelegramBot(ChannelService channelService, CropService cropService, TelegramClient client) {
        this.channelService = channelService;
        this.cropService = cropService;
        this.client = client;
    }

    public void handle(TelegramUpdate update) {
        TelegramUpdate.Message message = update.message();
        if (message == null || message.chat() == null || message.text() == null) {
            return;
        }
        String chatId = String.valueOf(message.chat().id());
        if (!"private".equals(message.chat().type())) {
            reply(chatId, "Escríbeme en un chat privado para vincular tus cultivos.");
            return;
        }
        String[] parts = message.text().trim().split("\\s+", 2);
        String command = parts[0].toLowerCase().replaceFirst("@.*$", "");
        String argument = parts.length > 1 ? parts[1].trim() : "";
        switch (command) {
            case "/start" -> start(chatId, argument, message);
            case "/estado" -> reply(chatId, status(chatId));
            case "/desvincular" -> reply(chatId, channelService.unlinkAddress(ChannelType.TELEGRAM, chatId)
                    ? "Listo: ya no recibirás alertas en este chat. Puedes volver a vincularlo desde tu perfil."
                    : "Este chat no estaba vinculado a ninguna cuenta.");
            default -> reply(chatId, HELP);
        }
    }

    private void start(String chatId, String code, TelegramUpdate.Message message) {
        if (code.isEmpty()) {
            reply(chatId, HELP);
            return;
        }
        String name = message.from() != null && message.from().firstName() != null ? message.from().firstName()
                : message.chat().firstName();
        String display = message.chat().username() != null ? "@" + message.chat().username() : name;
        Optional<ChannelLink> link = channelService.completeLink(ChannelType.TELEGRAM, code, chatId, display);
        reply(chatId, link.isPresent()
                ? "¡Listo" + (name == null ? "" : ", " + TelegramChannel.escape(name)) + "! Este chat quedó vinculado "
                + "a tu cuenta de SmartPot. Te avisaré de alertas, desconexiones y acciones del asistente; puedes "
                + "elegir cuáles en tu perfil.\n\nEscribe /estado para ver tus cultivos."
                : "El enlace expiró o ya se usó. Genera uno nuevo desde SmartPot › Perfil › Notificaciones.");
    }

    String status(String chatId) {
        Optional<ChannelLink> link = channelService.findByAddress(ChannelType.TELEGRAM, chatId);
        if (link.isEmpty()) {
            return NOT_LINKED;
        }
        List<Crop> crops = cropService.list(link.get().getUserId());
        if (crops.isEmpty()) {
            return "Todavía no tienes cultivos. Crea el primero desde la app.";
        }
        StringBuilder text = new StringBuilder("🌱 <b>Tus cultivos</b>\n");
        for (Crop crop : crops) {
            boolean online = crop.getDevice() != null && crop.getDevice().isOnline();
            text.append("\n• <b>").append(TelegramChannel.escape(crop.getName())).append("</b> — ")
                    .append(online ? "🟢 en línea" : "⚪ sin conexión");
            if (crop.getHealth() != null) {
                text.append(" · ").append(TelegramChannel.escape(crop.getHealth().label()))
                        .append(" (").append(Math.round(crop.getHealth().index())).append("/100)");
            }
            if (crop.isAutomationEnabled()) {
                text.append(" · 🤖 automático");
            }
        }
        return text.toString();
    }

    private void reply(String chatId, String html) {
        try {
            client.sendMessage(chatId, html, null, null);
        } catch (ChannelDeliveryException ex) {
            log.debug("No se pudo responder en Telegram: {}", ex.getMessage());
        }
    }
}
