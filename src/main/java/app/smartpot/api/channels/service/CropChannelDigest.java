package app.smartpot.api.channels.service;

import app.smartpot.api.ai.config.AiProperties;
import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.CropChannel;
import app.smartpot.api.channels.repository.CropChannelRepository;
import app.smartpot.api.commands.service.CommandService;
import app.smartpot.api.config.SmartPotProperties;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.notifications.model.entity.Notification;
import app.smartpot.api.notifications.model.entity.NotificationType;
import app.smartpot.api.notifications.repository.NotificationRepository;
import app.smartpot.api.readings.model.entity.Measures;
import app.smartpot.api.readings.model.entity.Reading;
import app.smartpot.api.readings.service.ReadingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resúmenes programados por cultivo, revisados cada minuto dentro de la API (no hay cron en el servidor): el resumen
 * cada tantas horas junta los avisos que se guardaron en ese lapso, y el resumen diario llega a la hora local elegida
 * con la salud, la última lectura y lo que pasó en las últimas 24 horas.
 */
@Slf4j
@Component
public class CropChannelDigest {

    static final int DIGEST_LINES = 8;
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<NotificationType, String> KINDS = Map.of(
            NotificationType.ALERT, "Alerta",
            NotificationType.DEVICE, "Conexión",
            NotificationType.AI, "Asistente",
            NotificationType.COMMAND, "Orden",
            NotificationType.INFO, "Aviso");

    private final CropChannelRepository repository;
    private final ChannelService channelService;
    private final ChannelSender sender;
    private final CropService cropService;
    private final NotificationRepository notifications;
    private final ReadingService readingService;
    private final CommandService commandService;
    private final Clock clock;
    private final ZoneId zone;
    private final String webBaseUrl;

    public CropChannelDigest(CropChannelRepository repository, ChannelService channelService, ChannelSender sender,
                             CropService cropService, NotificationRepository notifications,
                             ReadingService readingService, CommandService commandService, Clock clock,
                             AiProperties aiProperties, SmartPotProperties properties) {
        this.repository = repository;
        this.channelService = channelService;
        this.sender = sender;
        this.cropService = cropService;
        this.notifications = notifications;
        this.readingService = readingService;
        this.commandService = commandService;
        this.clock = clock;
        this.zone = aiProperties.timezone();
        this.webBaseUrl = properties.webBaseUrl().replaceAll("/+$", "");
    }

    static String describe(Reading reading) {
        Measures m = reading.getMeasures();
        if (m == null) {
            return "Sin lecturas recientes";
        }
        List<String> parts = new ArrayList<>();
        if (m.getTemperature() != null) {
            parts.add(String.format(Locale.ROOT, "%.1f °C", m.getTemperature()).replace('.', ','));
        }
        if (m.getHumidity() != null) {
            parts.add("humedad " + Math.round(m.getHumidity()) + " %");
        }
        if (m.getSoilMoisture() != null) {
            parts.add("sustrato " + Math.round(m.getSoilMoisture()) + " %");
        }
        if (m.getBrightness() != null) {
            parts.add("luz " + Math.round(m.getBrightness()) + " lux");
        }
        return "Última lectura: " + (parts.isEmpty() ? "sin datos" : String.join(" · ", parts));
    }

    @Scheduled(fixedDelayString = "${smartpot.channels.digest-interval:PT1M}", initialDelay = 30_000)
    public void run() {
        Instant now = clock.instant();
        for (CropChannel settings : repository.findByEnabledTrue()) {
            try {
                process(settings, now);
            } catch (RuntimeException ex) {
                log.warn("No se pudo preparar el resumen del cultivo {}: {}", settings.getCropId(), ex.getMessage());
            }
        }
    }

    void process(CropChannel settings, Instant now) {
        NotificationChannel channel = channelService.available(settings.getType(), false).orElse(null);
        Crop crop = channel == null ? null : cropService.find(settings.getCropId()).orElse(null);
        if (crop == null) {
            return;
        }
        boolean changed = false;
        if (settings.getDelivery() == CropChannel.Delivery.DIGEST) {
            Instant since = settings.getLastDigestAt();
            int hours = Math.max(1, settings.getDigestHours());
            if (since == null) {
                settings.setLastDigestAt(now);
                changed = true;
            } else if (!now.isBefore(since.plus(Duration.ofHours(hours)))) {
                List<Notification> items = notifications.findByCropIdAndCreatedAtAfterOrderByCreatedAtAsc(
                                crop.getId(), since).stream()
                        .filter(item -> settings.getEvents() != null && settings.getEvents().contains(item.getType()))
                        .toList();
                if (!items.isEmpty()) {
                    send(channel, settings, crop, digest(crop, items, hours));
                }
                settings.setLastDigestAt(now);
                changed = true;
            }
        }
        Instant dueAt = dueToday(settings.getDailySummaryAt(), now);
        if (dueAt != null && !now.isBefore(dueAt)
                && (settings.getLastSummaryAt() == null || settings.getLastSummaryAt().isBefore(dueAt))) {
            send(channel, settings, crop, summary(crop, now));
            settings.setLastSummaryAt(now);
            changed = true;
        }
        if (changed) {
            repository.save(settings);
        }
    }

    private void send(NotificationChannel channel, CropChannel settings, Crop crop, ChannelMessage message) {
        channelService.link(crop.getOwnerId(), settings.getType()).filter(ChannelLink::isEnabled)
                .ifPresent(link -> sender.toOwner(channel, link, message));
        sender.toRecipients(channel, settings, message);
    }

    Instant dueToday(String at, Instant now) {
        if (at == null || at.isBlank()) {
            return null;
        }
        return now.atZone(zone).toLocalDate().atTime(LocalTime.parse(at)).atZone(zone).toInstant();
    }

    ChannelMessage digest(Crop crop, List<Notification> items, int hours) {
        List<String> lines = new ArrayList<>();
        lines.add(items.size() + (items.size() == 1 ? " aviso" : " avisos") + " en "
                + (hours == 1 ? "la última hora" : "las últimas " + hours + " horas") + ":");
        for (Notification item : items.subList(0, Math.min(items.size(), DIGEST_LINES))) {
            lines.add("• " + HOUR.format(item.getCreatedAt().atZone(zone)) + " " + KINDS.get(item.getType()) + ": "
                    + item.getTitle());
        }
        if (items.size() > DIGEST_LINES) {
            lines.add("… y " + (items.size() - DIGEST_LINES) + " más en la app.");
        }
        return new ChannelMessage(NotificationType.INFO, "Resumen de «" + crop.getName() + "»",
                String.join("\n", lines), url(crop));
    }

    ChannelMessage summary(Crop crop, Instant now) {
        Instant dayAgo = now.minus(Duration.ofHours(24));
        List<String> lines = new ArrayList<>();
        lines.add(crop.getHealth() == null ? "Salud: aún sin evaluar"
                : "Salud: " + crop.getHealth().label() + " (" + Math.round(crop.getHealth().index()) + "/100)");
        boolean online = crop.getDevice() != null && crop.getDevice().isOnline();
        lines.add(online ? "Conexión: en línea" : "Conexión: sin conexión");
        readingService.latest(crop.getId()).map(CropChannelDigest::describe).ifPresent(lines::add);
        long alerts = notifications.countByCropIdAndTypeAndCreatedAtAfter(crop.getId(), NotificationType.ALERT,
                dayAgo);
        long orders = commandService.countSince(List.of(crop.getId()), dayAgo);
        lines.add("Últimas 24 h: " + alerts + (alerts == 1 ? " alerta" : " alertas") + " y " + orders
                + (orders == 1 ? " orden" : " órdenes"));
        return new ChannelMessage(NotificationType.INFO, "Resumen diario de «" + crop.getName() + "»",
                String.join("\n", lines), url(crop));
    }

    private String url(Crop crop) {
        return webBaseUrl + "/app/crops/" + crop.getId();
    }
}
