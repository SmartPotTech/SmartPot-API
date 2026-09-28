package app.smartpot.api.channels.telegram;

import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.channels.service.ChannelMessage;
import app.smartpot.api.channels.service.ChannelService;
import app.smartpot.api.channels.service.CropChannelService;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.CropHealth;
import app.smartpot.api.crops.model.entity.Device;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.notifications.model.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramBotTest {

    private static final String USER = "6718f0a1b2c3d4e5f6a7b000";

    private final ChannelService channelService = mock(ChannelService.class);
    private final CropChannelService cropChannelService = mock(CropChannelService.class);
    private final CropService cropService = mock(CropService.class);
    private final TelegramClient client = mock(TelegramClient.class);
    private final TelegramBot bot = new TelegramBot(channelService, cropChannelService, cropService, client);

    private static TelegramUpdate message(String text, String chatType) {
        return new TelegramUpdate(1, new TelegramUpdate.Message(10, new TelegramUpdate.Chat(555, chatType, "sebas",
                "Sebastián"), new TelegramUpdate.User(555, "Sebastián", "sebas"), text));
    }

    private String reply() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(client).sendMessage(eq("555"), text.capture(), isNull(), isNull());
        return text.getValue();
    }

    @Test
    void startWithAValidCodeLinksTheChat() {
        when(channelService.completeLink(ChannelType.TELEGRAM, "codigo123", "555", "@sebas"))
                .thenReturn(Optional.of(ChannelLink.builder().userId(USER).build()));

        bot.handle(message("/start codigo123", "private"));

        assertThat(reply()).startsWith("¡Listo, Sebastián!");
    }

    @Test
    void expiredCodesAskForANewLink() {
        when(channelService.completeLink(ChannelType.TELEGRAM, "viejo", "555", "@sebas")).thenReturn(Optional.empty());
        bot.handle(message("/start viejo", "private"));
        assertThat(reply()).contains("expiró");
    }

    @Test
    void statusSummarizesTheCropsOfTheLinkedAccount() {
        when(channelService.findByAddress(ChannelType.TELEGRAM, "555"))
                .thenReturn(Optional.of(ChannelLink.builder().userId(USER).build()));
        Crop crop = Crop.builder().name("Lechugas <balcón>").automationEnabled(true)
                .device(Device.builder().online(true).build())
                .health(new CropHealth(82.4, "GOOD", "Saludable", Instant.now())).build();
        when(cropService.list(USER)).thenReturn(List.of(crop));

        bot.handle(message("/estado", "private"));

        assertThat(reply()).contains("Lechugas &lt;balcón&gt;", "en línea", "Saludable (82/100)", "automático");
    }

    @Test
    void unlinkedChatsDoNotSeeAnyData() {
        when(channelService.findByAddress(ChannelType.TELEGRAM, "555")).thenReturn(Optional.empty());
        bot.handle(message("/estado", "private"));
        assertThat(reply()).isEqualTo(TelegramBot.NOT_LINKED);
        verify(cropService, never()).list(USER);
    }

    @Test
    void aShareCodeSubscribesTheChatToThatCrop() {
        when(cropChannelService.acceptShare(ChannelType.TELEGRAM, "c_compartido", "555", "@sebas"))
                .thenReturn(Optional.of(Crop.builder().name("Tomates de Ana").build()));

        bot.handle(message("/start c_compartido", "private"));

        assertThat(reply()).contains("avisos de «Tomates de Ana»");
        verify(channelService, never()).completeLink(any(), any(), any(), any());
    }

    @Test
    void statusListsTheCropsSharedWithTheChat() {
        when(channelService.findByAddress(ChannelType.TELEGRAM, "555")).thenReturn(Optional.empty());
        when(cropChannelService.sharedWith(ChannelType.TELEGRAM, "555"))
                .thenReturn(List.of(Crop.builder().name("Tomates de Ana").build()));

        bot.handle(message("/estado", "private"));

        assertThat(reply()).contains("Te compartieron", "Tomates de Ana");
    }

    @Test
    void unlinkingAlsoLeavesTheSharedCrops() {
        when(channelService.unlinkAddress(ChannelType.TELEGRAM, "555")).thenReturn(false);
        when(cropChannelService.leave(ChannelType.TELEGRAM, "555")).thenReturn(2);

        bot.handle(message("/desvincular", "private"));

        assertThat(reply()).startsWith("Listo");
    }

    @Test
    void groupsAreNotServed() {
        bot.handle(message("/start codigo123", "group"));
        verify(channelService, never()).completeLink(eq(ChannelType.TELEGRAM), eq("codigo123"), eq("555"),
                eq("@sebas"));
    }

    @Test
    void notificationsAreEscapedForTelegramHtml() {
        String text = TelegramChannel.format(new ChannelMessage(NotificationType.ALERT, "Atención en <Tomates>",
                "pH & nutrientes", null));
        assertThat(text).isEqualTo("⚠️ <b>Atención en &lt;Tomates&gt;</b>\npH &amp; nutrientes");
    }
}
