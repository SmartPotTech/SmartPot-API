package app.smartpot.api.channels.service;

import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.channels.model.dto.CropChannelResponse;
import app.smartpot.api.channels.model.dto.CropChannelUpdateRequest;
import app.smartpot.api.channels.model.dto.LinkCodeResponse;
import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.channels.model.entity.CropChannel;
import app.smartpot.api.channels.repository.CropChannelRepository;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.notifications.model.entity.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CropChannelServiceTest {

    private static final String OWNER = "6718f0a1b2c3d4e5f6a7b000";
    private static final String CROP = "6718f0a1b2c3d4e5f6a7b8c9";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-28T12:00:00Z"), ZoneOffset.UTC);

    private final CropChannelRepository repository = mock(CropChannelRepository.class);
    private final ChannelService channelService = mock(ChannelService.class);
    private final CropService cropService = mock(CropService.class);
    private final NotificationChannel telegram = mock(NotificationChannel.class);
    private final CacheStore cacheStore = new CacheStore(null, CLOCK);
    private final Crop crop = Crop.builder().id(CROP).ownerId(OWNER).name("Tomates").build();
    private CropChannelService service;

    @BeforeEach
    void setUp() {
        service = new CropChannelService(repository, channelService, cropService, cacheStore, CLOCK);
        when(cropService.getOwned(OWNER, CROP)).thenReturn(crop);
        when(cropService.find(CROP)).thenReturn(Optional.of(crop));
        when(channelService.available(ChannelType.TELEGRAM, false)).thenReturn(Optional.of(telegram));
        when(channelService.available(ChannelType.TELEGRAM, true)).thenReturn(Optional.of(telegram));
        when(channelService.link(OWNER, ChannelType.TELEGRAM)).thenReturn(Optional.of(ChannelLink.builder()
                .events(EnumSet.of(NotificationType.ALERT, NotificationType.DEVICE)).build()));
        when(repository.findByCropIdAndType(CROP, ChannelType.TELEGRAM)).thenReturn(Optional.empty());
        when(repository.save(any(CropChannel.class))).thenAnswer(call -> call.getArgument(0));
        when(telegram.linkUrl(any())).thenAnswer(call -> "https://t.me/SmartPotBot?start=" + call.getArgument(0));
    }

    @Test
    void withoutChoicesTheCropFollowsTheProfileInstantly() {
        CropChannelResponse response = service.list(OWNER, CROP).getFirst();

        assertThat(response.available()).isTrue();
        assertThat(response.linked()).isTrue();
        assertThat(response.enabled()).isTrue();
        assertThat(response.delivery()).isEqualTo(CropChannel.Delivery.INSTANT);
        assertThat(response.events()).containsExactlyInAnyOrder(NotificationType.ALERT, NotificationType.DEVICE);
        verify(repository, never()).save(any());
    }

    @Test
    void savesWhatToNotifyTheDigestAndTheDailySummary() {
        CropChannelResponse response = service.update(OWNER, CROP, ChannelType.TELEGRAM,
                new CropChannelUpdateRequest(true, Set.of(NotificationType.AI), CropChannel.Delivery.DIGEST, 3,
                        "07:30"));

        assertThat(response.events()).containsExactly(NotificationType.AI);
        assertThat(response.delivery()).isEqualTo(CropChannel.Delivery.DIGEST);
        assertThat(response.digestHours()).isEqualTo(3);
        assertThat(response.dailySummaryAt()).isEqualTo("07:30");
        ArgumentCaptor<CropChannel> saved = ArgumentCaptor.forClass(CropChannel.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getLastDigestAt()).isEqualTo(CLOCK.instant());
        assertThat(saved.getValue().getOwnerId()).isEqualTo(OWNER);
    }

    @Test
    void anEmptyHourTurnsTheDailySummaryOff() {
        when(repository.findByCropIdAndType(CROP, ChannelType.TELEGRAM)).thenReturn(Optional.of(CropChannel.builder()
                .cropId(CROP).ownerId(OWNER).type(ChannelType.TELEGRAM).enabled(true).dailySummaryAt("07:30")
                .delivery(CropChannel.Delivery.INSTANT).recipients(new ArrayList<>()).build()));

        assertThat(service.update(OWNER, CROP, ChannelType.TELEGRAM,
                new CropChannelUpdateRequest(null, null, null, null, "")).dailySummaryAt()).isNull();
    }

    @Test
    void aShareLinkAddsTheChatOnceAndCanBeRemoved() {
        LinkCodeResponse code = service.shareCode(OWNER, CROP, ChannelType.TELEGRAM);
        assertThat(code.code()).startsWith("c_");
        assertThat(code.url()).endsWith(code.code());

        Optional<Crop> accepted = service.acceptShare(ChannelType.TELEGRAM, code.code(), "777", "@ana");
        assertThat(accepted).contains(crop);
        assertThat(service.acceptShare(ChannelType.TELEGRAM, code.code(), "777", "@ana")).isEmpty();

        ArgumentCaptor<CropChannel> saved = ArgumentCaptor.forClass(CropChannel.class);
        verify(repository).save(saved.capture());
        CropChannel settings = saved.getValue();
        assertThat(settings.getRecipients()).extracting(CropChannel.Recipient::displayName).containsExactly("@ana");

        when(repository.findByCropIdAndType(CROP, ChannelType.TELEGRAM)).thenReturn(Optional.of(settings));
        service.removeRecipient(OWNER, CROP, ChannelType.TELEGRAM, settings.getRecipients().getFirst().id());
        assertThat(settings.getRecipients()).isEmpty();
        assertThatThrownBy(() -> service.removeRecipient(OWNER, CROP, ChannelType.TELEGRAM, "otro"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void aCropIsSharedWithTenChatsAtMost() {
        List<CropChannel.Recipient> full = new ArrayList<>();
        for (int i = 0; i < CropChannel.MAX_RECIPIENTS; i++) {
            full.add(new CropChannel.Recipient("r" + i, "70" + i, "Chat " + i, CLOCK.instant()));
        }
        when(repository.findByCropIdAndType(CROP, ChannelType.TELEGRAM)).thenReturn(Optional.of(CropChannel.builder()
                .cropId(CROP).ownerId(OWNER).type(ChannelType.TELEGRAM).enabled(true).recipients(full).build()));

        assertThatThrownBy(() -> service.shareCode(OWNER, CROP, ChannelType.TELEGRAM))
                .hasMessageContaining("hasta 10 chats");
    }

    @Test
    void linkCodesAreNotShareCodes() {
        assertThat(CropChannelService.isShareCode("c_abc")).isTrue();
        assertThat(CropChannelService.isShareCode("abc")).isFalse();
        assertThat(service.acceptShare(ChannelType.TELEGRAM, "abc", "777", "@ana")).isEmpty();
    }
}
