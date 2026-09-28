package app.smartpot.api.channels.telegram;

import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.channels.service.ChannelDeliveryException;
import app.smartpot.api.channels.service.ChannelService;
import app.smartpot.api.channels.service.CropChannelService;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.service.CropService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Conversación con el bot: /start con el código vincula el chat a la cuenta (o, con un código para compartir, a los
 * avisos de un cultivo ajeno), /estado resume los cultivos, /desvincular deja de enviar avisos y /ayuda explica lo
 * demás. Solo atiende chats privados.
 */
@Slf4j
@Component
public class TelegramBot {

    static final String HELP = """
            Soy el asistente de <b>SmartPot</b> 🌱
            Te aviso aquí cuando un cultivo necesita atención, cuando un cultivo se desconecta o cuando el \
            asistente actúa por su cuenta.

            /estado — cómo están tus cultivos y los que te compartieron
            /desvincular — dejar de recibir avisos en este chat
            /ayuda — este mensaje

            Para vincular este chat entra a SmartPot › Perfil › Aplicaciones y toca «Vincular Telegram». Si alguien \
            te comparte un cultivo, abre el enlace que te envíe.""";
    static final String NOT_LINKED = "Este chat aún no está vinculado. Entra a SmartPot › Perfil › Aplicaciones "
            + "y toca «Vincular Telegram».";

    private final ChannelService channelService;
    private final CropChannelService cropChannelService;
    private final CropService cropService;
    private final TelegramClient client;

    public TelegramBot(ChannelService channelService, CropChannelService cropChannelService, CropService cropService,
                       TelegramClient client) {
        this.channelService = channelService;
        this.cropChannelService = cropChannelService;
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
            case "/desvincular" -> reply(chatId, unlink(chatId));
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
        if (CropChannelService.isShareCode(code)) {
            Optional<Crop> crop = cropChannelService.acceptShare(ChannelType.TELEGRAM, code, chatId, display);
            reply(chatId, crop.isPresent()
                    ? "¡Listo" + (name == null ? "" : ", " + TelegramChannel.escape(name)) + "! Desde ahora recibirás "
                    + "aquí los avisos de «" + TelegramChannel.escape(crop.get().getName()) + "». Escribe /estado para "
                    + "verlo y /desvincular para dejar de recibirlos."
                    : "El enlace expiró, ya se usó o el cultivo ya se comparte con demasiados chats. Pide uno nuevo.");
            return;
        }
        Optional<ChannelLink> link = channelService.completeLink(ChannelType.TELEGRAM, code, chatId, display);
        reply(chatId, link.isPresent()
                ? "¡Listo" + (name == null ? "" : ", " + TelegramChannel.escape(name)) + "! Este chat quedó vinculado "
                + "a tu cuenta de SmartPot. Te avisaré de alertas, desconexiones y acciones del asistente; puedes "
                + "elegir cuáles en tu perfil.\n\nEscribe /estado para ver tus cultivos."
                : "El enlace expiró o ya se usó. Genera uno nuevo desde SmartPot › Perfil › Notificaciones.");
    }

    private String unlink(String chatId) {
        boolean linked = channelService.unlinkAddress(ChannelType.TELEGRAM, chatId);
        int shared = cropChannelService.leave(ChannelType.TELEGRAM, chatId);
        if (!linked && shared == 0) {
            return "Este chat no estaba vinculado a ninguna cuenta ni recibía avisos compartidos.";
        }
        return "Listo: ya no recibirás avisos en este chat. Puedes volver a vincularlo desde tu perfil.";
    }

    String status(String chatId) {
        Optional<ChannelLink> link = channelService.findByAddress(ChannelType.TELEGRAM, chatId);
        List<Crop> shared = cropChannelService.sharedWith(ChannelType.TELEGRAM, chatId);
        if (link.isEmpty() && shared.isEmpty()) {
            return NOT_LINKED;
        }
        List<Crop> crops = link.map(value -> cropService.list(value.getUserId())).orElse(List.of());
        if (crops.isEmpty() && shared.isEmpty()) {
            return "Todavía no tienes cultivos. Crea el primero desde la app.";
        }
        StringBuilder text = new StringBuilder();
        if (!crops.isEmpty()) {
            text.append("🌱 <b>Tus cultivos</b>\n");
            describe(text, crops);
        }
        if (!shared.isEmpty()) {
            text.append(text.isEmpty() ? "" : "\n\n").append("🤝 <b>Te compartieron</b>\n");
            describe(text, shared);
        }
        return text.toString();
    }

    private static void describe(StringBuilder text, List<Crop> crops) {
        for (Crop crop : crops) {
            boolean online = crop.getDevice() != null && crop.getDevice().isOnline();
            text.append("\n• <b>").append(TelegramChannel.escape(crop.getName())).append("</b>")
                    .append(crop.isVirtual() ? " (virtual)" : "").append(" — ")
                    .append(online ? "🟢 en línea" : "⚪ sin conexión");
            if (crop.getHealth() != null) {
                text.append(" · ").append(TelegramChannel.escape(crop.getHealth().label()))
                        .append(" (").append(Math.round(crop.getHealth().index())).append("/100)");
            }
            if (crop.isAutomationEnabled()) {
                text.append(" · 🤖 automático");
            }
        }
    }

    private void reply(String chatId, String html) {
        try {
            client.sendMessage(chatId, html, null, null);
        } catch (ChannelDeliveryException ex) {
            log.debug("No se pudo responder en Telegram: {}", ex.getMessage());
        }
    }
}
