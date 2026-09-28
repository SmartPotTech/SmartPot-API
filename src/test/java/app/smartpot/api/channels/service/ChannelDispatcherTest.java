package app.smartpot.api.channels.service;

import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.channels.model.entity.CropChannel;
import app.smartpot.api.notifications.model.entity.Notification;
import app.smartpot.api.notifications.model.entity.NotificationType;
import app.smartpot.api.notifications.model.event.NotificationCreatedEvent;
import app.smartpot.api.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelDispatcherTest {

    private static final String USER = "6718f0a1b2c3d4e5f6a7b000";
    private static final String CROP = "6718f0a1b2c3d4e5f6a7b8c9";

    private final ChannelService channelService = mock(ChannelService.class);
    private final CropChannelService cropChannelService = mock(CropChannelService.class);
    private final NotificationChannel telegram = mock(NotificationChannel.class);
    private final ChannelLink link = ChannelLink.builder().userId(USER).type(ChannelType.TELEGRAM).address("555")
            .enabled(true).events(EnumSet.of(NotificationType.ALERT)).build();
    private ChannelDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        when(channelService.available(ChannelType.TELEGRAM, false)).thenReturn(Optional.of(telegram));
        when(channelService.link(USER, ChannelType.TELEGRAM)).thenReturn(Optional.of(link));
        when(cropChannelService.find(CROP, ChannelType.TELEGRAM)).thenReturn(Optional.empty());
        when(telegram.type()).thenReturn(ChannelType.TELEGRAM);
        dispatcher = new ChannelDispatcher(channelService, cropChannelService,
                new ChannelSender(channelService, cropChannelService), TestProperties.smartPot());
    }

    private NotificationCreatedEvent event(NotificationType type) {
        return new NotificationCreatedEvent(Notification.builder().userId(USER).cropId(CROP).type(type)
                .title("Atención en Lechugas").message("El sustrato está seco.").build());
    }

    private static CropChannel settings(CropChannel.Delivery delivery, NotificationType... events) {
        return CropChannel.builder().cropId(CROP).ownerId(USER).type(ChannelType.TELEGRAM).enabled(true)
                .events(EnumSet.of(events[0], events)).delivery(delivery).digestHours(6)
                .recipients(new ArrayList<>(List.of(new CropChannel.Recipient("r1", "777", "Ana", null)))).build();
    }

    @Test
    void forwardsSubscribedNotificationsWithALinkToTheCrop() {
        dispatcher.onNotification(event(NotificationType.ALERT));

        ArgumentCaptor<ChannelMessage> message = ArgumentCaptor.forClass(ChannelMessage.class);
        verify(telegram).send(eq("555"), message.capture());
        assertThat(message.getValue().title()).isEqualTo("Atención en Lechugas");
        assertThat(message.getValue().url()).isEqualTo("http://localhost:5173/app/crops/" + CROP);
        verify(channelService).delivered(link);
    }

    @Test
    void skipsTypesThePersonDidNotChoose() {
        dispatcher.onNotification(event(NotificationType.COMMAND));
        verify(telegram, never()).send(any(), any());
    }

    @Test
    void recordsFailuresWithoutBreakingTheFlow() {
        doThrow(new ChannelDeliveryException("bloqueado", true)).when(telegram).send(any(), any());
        dispatcher.onNotification(event(NotificationType.ALERT));
        verify(channelService).failed(link, true);
    }

    @Test
    void theCropChoicesWinAndReachTheSharedChats() {
        when(cropChannelService.find(CROP, ChannelType.TELEGRAM))
                .thenReturn(Optional.of(settings(CropChannel.Delivery.INSTANT, NotificationType.COMMAND)));

        dispatcher.onNotification(event(NotificationType.COMMAND));

        verify(telegram).send(eq("555"), any());
        verify(telegram).send(eq("777"), any());
    }

    @Test
    void digestsWaitForTheScheduledSummary() {
        when(cropChannelService.find(CROP, ChannelType.TELEGRAM))
                .thenReturn(Optional.of(settings(CropChannel.Delivery.DIGEST, NotificationType.ALERT)));

        dispatcher.onNotification(event(NotificationType.ALERT));

        verify(telegram, never()).send(any(), any());
    }

    @Test
    void aSharedChatThatBlockedTheBotIsRemoved() {
        CropChannel shared = settings(CropChannel.Delivery.INSTANT, NotificationType.ALERT);
        when(cropChannelService.find(CROP, ChannelType.TELEGRAM)).thenReturn(Optional.of(shared));
        doThrow(new ChannelDeliveryException("bloqueado", true)).when(telegram).send(eq("777"), any());

        dispatcher.onNotification(event(NotificationType.ALERT));

        verify(cropChannelService).removeAddress(shared, "777");
    }
}
