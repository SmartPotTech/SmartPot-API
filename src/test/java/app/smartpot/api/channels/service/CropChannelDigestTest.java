package app.smartpot.api.channels.service;

import app.smartpot.api.ai.config.AiProperties;
import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.channels.model.entity.CropChannel;
import app.smartpot.api.channels.repository.CropChannelRepository;
import app.smartpot.api.commands.service.CommandService;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.CropHealth;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.notifications.model.entity.Notification;
import app.smartpot.api.notifications.model.entity.NotificationType;
import app.smartpot.api.notifications.repository.NotificationRepository;
import app.smartpot.api.readings.model.entity.Measures;
import app.smartpot.api.readings.model.entity.Reading;
import app.smartpot.api.readings.service.ReadingService;
import app.smartpot.api.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CropChannelDigestTest {

    private static final String OWNER = "6718f0a1b2c3d4e5f6a7b000";
    private static final String CROP = "6718f0a1b2c3d4e5f6a7b8c9";
    // 07:31 en Bogotá.
    private static final Instant NOW = Instant.parse("2026-09-28T12:31:00Z");

    private final CropChannelRepository repository = mock(CropChannelRepository.class);
    private final ChannelService channelService = mock(ChannelService.class);
    private final ChannelSender sender = mock(ChannelSender.class);
    private final CropService cropService = mock(CropService.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final ReadingService readingService = mock(ReadingService.class);
    private final CommandService commandService = mock(CommandService.class);
    private final NotificationChannel telegram = mock(NotificationChannel.class);
    private final ChannelLink link = ChannelLink.builder().userId(OWNER).address("555").enabled(true).build();
    private final Crop crop = Crop.builder().id(CROP).ownerId(OWNER).name("Tomates")
            .health(new CropHealth(81.6, "GOOD", "Saludable", NOW)).build();
    private CropChannelDigest digest;

    @BeforeEach
    void setUp() {
        digest = new CropChannelDigest(repository, channelService, sender, cropService, notifications, readingService,
                commandService, Clock.fixed(NOW, ZoneOffset.UTC),
                new AiProperties(true, "http://ai", "t", Duration.ofSeconds(2), Duration.ofMinutes(5),
                        Duration.ofMinutes(10), 48, ZoneId.of("America/Bogota")), TestProperties.smartPot());
        when(channelService.available(ChannelType.TELEGRAM, false)).thenReturn(Optional.of(telegram));
        when(channelService.link(OWNER, ChannelType.TELEGRAM)).thenReturn(Optional.of(link));
        when(cropService.find(CROP)).thenReturn(Optional.of(crop));
    }

    private static CropChannel settings() {
        return CropChannel.builder().cropId(CROP).ownerId(OWNER).type(ChannelType.TELEGRAM).enabled(true)
                .events(EnumSet.of(NotificationType.ALERT)).delivery(CropChannel.Delivery.DIGEST).digestHours(6)
                .recipients(new ArrayList<>()).build();
    }

    private static Notification notification(NotificationType type, String title, Instant at) {
        return Notification.builder().cropId(CROP).type(type).title(title).message("…").createdAt(at).build();
    }

    @Test
    void theDigestGathersTheChosenNoticesOfItsWindow() {
        CropChannel settings = settings();
        settings.setLastDigestAt(NOW.minus(Duration.ofHours(6)));
        when(notifications.findByCropIdAndCreatedAtAfterOrderByCreatedAtAsc(CROP, settings.getLastDigestAt()))
                .thenReturn(List.of(notification(NotificationType.ALERT, "Sustrato seco", NOW.minusSeconds(3600)),
                        notification(NotificationType.COMMAND, "Riego", NOW.minusSeconds(1800))));

        digest.process(settings, NOW);

        ArgumentCaptor<ChannelMessage> message = ArgumentCaptor.forClass(ChannelMessage.class);
        verify(sender).toOwner(eq(telegram), eq(link), message.capture());
        assertThat(message.getValue().title()).isEqualTo("Resumen de «Tomates»");
        assertThat(message.getValue().body()).contains("1 aviso en las últimas 6 horas", "06:31 Alerta: Sustrato seco")
                .doesNotContain("Riego");
        assertThat(settings.getLastDigestAt()).isEqualTo(NOW);
        verify(repository).save(settings);
    }

    @Test
    void theDigestWaitsForItsHour() {
        CropChannel settings = settings();
        settings.setLastDigestAt(NOW.minus(Duration.ofHours(2)));

        digest.process(settings, NOW);

        verify(sender, never()).toOwner(any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void theDailySummaryArrivesOnceAtTheLocalHour() {
        CropChannel settings = settings();
        settings.setDelivery(CropChannel.Delivery.INSTANT);
        settings.setDailySummaryAt("07:30");
        settings.setLastSummaryAt(NOW.minus(Duration.ofDays(1)));
        when(readingService.latest(CROP)).thenReturn(Optional.of(Reading.builder()
                .measures(Measures.builder().temperature(22.44).soilMoisture(64.6).build()).build()));
        when(notifications.countByCropIdAndTypeAndCreatedAtAfter(eq(CROP), eq(NotificationType.ALERT), any()))
                .thenReturn(1L);
        when(commandService.countSince(anyList(), any())).thenReturn(4L);

        digest.process(settings, NOW);
        digest.process(settings, NOW.plusSeconds(60));

        ArgumentCaptor<ChannelMessage> message = ArgumentCaptor.forClass(ChannelMessage.class);
        verify(sender).toOwner(eq(telegram), eq(link), message.capture());
        assertThat(message.getValue().title()).isEqualTo("Resumen diario de «Tomates»");
        assertThat(message.getValue().body()).contains("Saludable (82/100)", "22,4 °C", "sustrato 65 %",
                "1 alerta y 4 órdenes");
        verify(sender).toRecipients(telegram, settings, message.getValue());
    }

    @Test
    void theDailySummaryIsNotSentBeforeItsHour() {
        CropChannel settings = settings();
        settings.setDelivery(CropChannel.Delivery.INSTANT);
        settings.setDailySummaryAt("18:00");

        digest.process(settings, NOW);

        verify(sender, never()).toOwner(any(), any(), any());
    }
}
