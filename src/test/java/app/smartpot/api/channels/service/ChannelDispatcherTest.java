package app.smartpot.api.channels.service;

import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.notifications.model.entity.Notification;
import app.smartpot.api.notifications.model.entity.NotificationType;
import app.smartpot.api.notifications.model.event.NotificationCreatedEvent;
import app.smartpot.api.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelDispatcherTest {

    private static final String USER = "6718f0a1b2c3d4e5f6a7b000";
    private static final String CROP = "6718f0a1b2c3d4e5f6a7b8c9";

    private final ChannelService channelService = mock(ChannelService.class);
    private final NotificationChannel telegram = mock(NotificationChannel.class);
    private final ChannelLink link = ChannelLink.builder().userId(USER).type(ChannelType.TELEGRAM).address("555")
            .enabled(true).events(EnumSet.of(NotificationType.ALERT)).build();
    private ChannelDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        when(channelService.activeLinks(USER)).thenReturn(List.of(link));
        when(channelService.channel(ChannelType.TELEGRAM)).thenReturn(telegram);
        when(telegram.isAvailable()).thenReturn(true);
        dispatcher = new ChannelDispatcher(channelService, TestProperties.smartPot());
    }

    private NotificationCreatedEvent event(NotificationType type) {
        return new NotificationCreatedEvent(Notification.builder().userId(USER).cropId(CROP).type(type)
                .title("Atención en Lechugas").message("El sustrato está seco.").build());
    }

    @Test
    void forwardsSubscribedNotificationsWithALinkToTheCrop() {
        dispatcher.onNotification(event(NotificationType.ALERT));

        ArgumentCaptor<ChannelMessage> message = ArgumentCaptor.forClass(ChannelMessage.class);
        verify(telegram).send(any(), message.capture());
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
}
