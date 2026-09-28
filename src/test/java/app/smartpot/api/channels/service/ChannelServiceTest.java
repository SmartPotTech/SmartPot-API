package app.smartpot.api.channels.service;

import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.channels.model.dto.ChannelLinkUpdateRequest;
import app.smartpot.api.channels.model.dto.ChannelOptionResponse;
import app.smartpot.api.channels.model.dto.LinkCodeResponse;
import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.channels.repository.ChannelLinkRepository;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.notifications.model.entity.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

class ChannelServiceTest {

    private static final String USER = "6718f0a1b2c3d4e5f6a7b000";
    private static final String OTHER = "6718f0a1b2c3d4e5f6a7b001";
    private static final String LINK = "6718f0a1b2c3d4e5f6a7b8c9";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC);

    private final ChannelLinkRepository repository = mock(ChannelLinkRepository.class);
    private final NotificationChannel telegram = mock(NotificationChannel.class);
    private CacheStore cacheStore;
    private ChannelService service;

    @BeforeEach
    void setUp() {
        // Redis caído a propósito: la caché trabaja en memoria, como en la API sin Redis.
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("sin Redis"));
        cacheStore = new CacheStore(redis, CLOCK);
        when(telegram.type()).thenReturn(ChannelType.TELEGRAM);
        when(telegram.isAvailable()).thenReturn(true);
        when(telegram.handle()).thenReturn("@SmartPotBot");
        when(telegram.linkUrl(any())).thenAnswer(call -> "https://t.me/SmartPotBot?start=" + call.getArgument(0));
        when(repository.save(any(ChannelLink.class))).thenAnswer(call -> call.getArgument(0));
        service = new ChannelService(repository, List.of(telegram), cacheStore, CLOCK);
    }

    @Test
    void linkCodesOpenTheBotAndWorkOnlyOnce() {
        LinkCodeResponse code = service.createLinkCode(USER, ChannelType.TELEGRAM);
        assertThat(code.url()).isEqualTo("https://t.me/SmartPotBot?start=" + code.code());
        assertThat(code.expiresAt()).isEqualTo(CLOCK.instant().plus(ChannelService.CODE_TTL));
        when(repository.findByTypeAndAddress(ChannelType.TELEGRAM, "555")).thenReturn(Optional.empty());
        when(repository.findByUserIdAndType(USER, ChannelType.TELEGRAM)).thenReturn(Optional.empty());

        Optional<ChannelLink> link = service.completeLink(ChannelType.TELEGRAM, code.code(), "555", "@sebas");

        assertThat(link).isPresent();
        assertThat(link.get().getUserId()).isEqualTo(USER);
        assertThat(link.get().getEvents()).isEqualTo(ChannelService.DEFAULT_EVENTS);
        assertThat(link.get().isEnabled()).isTrue();
        assertThat(service.completeLink(ChannelType.TELEGRAM, code.code(), "555", "@sebas")).isEmpty();
    }

    @Test
    void unknownCodesDoNotLinkAnything() {
        assertThat(service.completeLink(ChannelType.TELEGRAM, "inventado", "555", "x")).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void aChatMovesToTheAccountThatLinkedItLast() {
        ChannelLink previous = ChannelLink.builder().id(LINK).userId(OTHER).type(ChannelType.TELEGRAM).address("555")
                .build();
        when(repository.findByTypeAndAddress(ChannelType.TELEGRAM, "555")).thenReturn(Optional.of(previous));
        when(repository.findByUserIdAndType(USER, ChannelType.TELEGRAM)).thenReturn(Optional.empty());
        String code = service.createLinkCode(USER, ChannelType.TELEGRAM).code();

        service.completeLink(ChannelType.TELEGRAM, code, "555", "@sebas");

        verify(repository).delete(previous);
    }

    @Test
    void peopleChooseWhichNotificationsTheyReceive() {
        ChannelLink link = ChannelLink.builder().id(LINK).userId(USER).type(ChannelType.TELEGRAM)
                .events(EnumSet.of(NotificationType.ALERT)).enabled(true).build();
        when(repository.findByIdAndUserId(LINK, USER)).thenReturn(Optional.of(link));

        var updated = service.update(USER, LINK, new ChannelLinkUpdateRequest(false,
                Set.of(NotificationType.DEVICE, NotificationType.AI)));

        assertThat(updated.enabled()).isFalse();
        assertThat(updated.events()).containsExactly(NotificationType.DEVICE, NotificationType.AI);
    }

    @Test
    void foreignLinksLookMissing() {
        when(repository.findByIdAndUserId(LINK, USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.unlink(USER, LINK)).isInstanceOf(ApiException.class)
                .hasMessage("El canal no está vinculado");
    }

    @Test
    void aBlockedChatPausesTheLink() {
        ChannelLink link = ChannelLink.builder().id(LINK).userId(USER).type(ChannelType.TELEGRAM).enabled(true)
                .build();
        service.failed(link, true);
        assertThat(link.isEnabled()).isFalse();

        ChannelLink flaky = ChannelLink.builder().id(LINK).userId(USER).type(ChannelType.TELEGRAM).enabled(true)
                .build();
        for (int i = 0; i < 4; i++) {
            service.failed(flaky, false);
        }
        assertThat(flaky.isEnabled()).isTrue();
        service.failed(flaky, false);
        assertThat(flaky.isEnabled()).isFalse();
    }

    @Test
    void unconfiguredChannelsCannotBeLinked() {
        when(telegram.isAvailable()).thenReturn(false);
        when(telegram.requirements()).thenReturn(List.of("TELEGRAM_BOT_TOKEN", "TELEGRAM_BOT_USERNAME"));
        assertThatThrownBy(() -> service.createLinkCode(USER, ChannelType.TELEGRAM))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no está configurado");
        ChannelOptionResponse option = service.overview(USER).getFirst();
        assertThat(option.available()).isFalse();
        assertThat(option.requirements()).containsExactly("TELEGRAM_BOT_TOKEN", "TELEGRAM_BOT_USERNAME");
    }
}
