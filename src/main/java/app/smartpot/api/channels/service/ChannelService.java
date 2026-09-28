package app.smartpot.api.channels.service;

import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.channels.model.dto.ChannelLinkResponse;
import app.smartpot.api.channels.model.dto.ChannelLinkUpdateRequest;
import app.smartpot.api.channels.model.dto.ChannelOptionResponse;
import app.smartpot.api.channels.model.dto.LinkCodeResponse;
import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.channels.repository.ChannelLinkRepository;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.exception.ObjectIds;
import app.smartpot.api.notifications.model.entity.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vínculos de cada cuenta con los canales externos. El vínculo se completa desde el propio canal con un
 * código de un solo uso que vence en 10 minutos: así se demuestra que la conversación es de quien lo pidió.
 */
@Slf4j
@Service
public class ChannelService {

    public static final Duration CODE_TTL = Duration.ofMinutes(10);
    public static final Set<NotificationType> DEFAULT_EVENTS =
            EnumSet.of(NotificationType.ALERT, NotificationType.DEVICE, NotificationType.AI);
    private static final String NOT_FOUND = "El canal no está vinculado";
    private static final Map<ChannelType, String> NAMES = Map.of(ChannelType.TELEGRAM, "Telegram");

    private final ChannelLinkRepository repository;
    private final Map<ChannelType, NotificationChannel> channels;
    private final CacheStore cacheStore;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ChannelService(ChannelLinkRepository repository, List<NotificationChannel> channels, CacheStore cacheStore,
                          Clock clock) {
        this.repository = repository;
        this.channels = channels.stream().collect(Collectors.toMap(NotificationChannel::type, Function.identity()));
        this.cacheStore = cacheStore;
        this.clock = clock;
    }

    public static String nameOf(ChannelType type) {
        return NAMES.getOrDefault(type, type.name());
    }

    private static String codeKey(ChannelType type, String code) {
        return "channel-link:" + type.name().toLowerCase() + ":" + code;
    }

    public List<ChannelOptionResponse> overview(String userId) {
        Map<ChannelType, ChannelLink> links = repository.findByUserId(userId).stream()
                .collect(Collectors.toMap(ChannelLink::getType, Function.identity()));
        return Arrays.stream(ChannelType.values()).map(type -> {
            NotificationChannel channel = channels.get(type);
            boolean available = channel != null && channel.isAvailable();
            ChannelLink link = links.get(type);
            return new ChannelOptionResponse(type, NAMES.getOrDefault(type, type.name()),
                    channel == null ? null : channel.description(), available, available ? channel.handle() : null,
                    available || channel == null ? List.of() : channel.requirements(),
                    link == null ? null : ChannelLinkResponse.of(link));
        }).toList();
    }

    public LinkCodeResponse createLinkCode(String userId, ChannelType type) {
        NotificationChannel channel = available(type);
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        cacheStore.put(codeKey(type, code), userId, CODE_TTL);
        return new LinkCodeResponse(type, code, channel.linkUrl(code), clock.instant().plus(CODE_TTL));
    }

    /**
     * Completa el vínculo desde el canal. Si la dirección estaba vinculada a otra cuenta, pasa a la nueva;
     * si la cuenta ya tenía otra dirección en ese canal, se reemplaza.
     */
    public Optional<ChannelLink> completeLink(ChannelType type, String code, String address, String displayName) {
        if (code == null || code.isBlank() || code.length() > 64) {
            return Optional.empty();
        }
        Optional<String> userId = cacheStore.take(codeKey(type, code));
        if (userId.isEmpty()) {
            return Optional.empty();
        }
        repository.findByTypeAndAddress(type, address)
                .filter(existing -> !existing.getUserId().equals(userId.get()))
                .ifPresent(repository::delete);
        ChannelLink link = repository.findByUserIdAndType(userId.get(), type).orElseGet(() -> ChannelLink.builder()
                .userId(userId.get())
                .type(type)
                .events(EnumSet.copyOf(DEFAULT_EVENTS))
                .build());
        link.setAddress(address);
        link.setDisplayName(displayName);
        link.setEnabled(true);
        link.setFailures(0);
        link.setLinkedAt(clock.instant());
        log.info("Canal {} vinculado a la cuenta {}", type, userId.get());
        return Optional.of(repository.save(link));
    }

    public Optional<ChannelLink> findByAddress(ChannelType type, String address) {
        return repository.findByTypeAndAddress(type, address);
    }

    public ChannelLinkResponse update(String userId, String linkId, ChannelLinkUpdateRequest request) {
        ChannelLink link = find(userId, linkId);
        if (request.enabled() != null) {
            link.setEnabled(request.enabled());
            if (request.enabled()) {
                link.setFailures(0);
            }
        }
        if (request.events() != null) {
            link.setEvents(request.events().isEmpty() ? EnumSet.noneOf(NotificationType.class)
                    : EnumSet.copyOf(request.events()));
        }
        return ChannelLinkResponse.of(repository.save(link));
    }

    public void sendTest(String userId, String linkId) {
        ChannelLink link = find(userId, linkId);
        NotificationChannel channel = available(link.getType());
        try {
            channel.send(link.getAddress(), new ChannelMessage(NotificationType.INFO, "Prueba de SmartPot",
                    "Así te llegarán las alertas de tus cultivos. Puedes elegir cuáles recibir en tu perfil.", null));
            delivered(link);
        } catch (ChannelDeliveryException ex) {
            throw ApiException.badRequest("No se pudo enviar el mensaje de prueba: " + ex.getMessage());
        }
    }

    public void unlink(String userId, String linkId) {
        repository.delete(find(userId, linkId));
    }

    public boolean unlinkAddress(ChannelType type, String address) {
        Optional<ChannelLink> link = repository.findByTypeAndAddress(type, address);
        link.ifPresent(repository::delete);
        return link.isPresent();
    }

    public void deleteAllForUser(String userId) {
        repository.deleteByUserId(userId);
    }

    List<ChannelLink> activeLinks(String userId) {
        return repository.findByUserIdAndEnabledTrue(userId);
    }

    public Optional<ChannelLink> link(String userId, ChannelType type) {
        return repository.findByUserIdAndType(userId, type);
    }

    public NotificationChannel channel(ChannelType type) {
        return channels.get(type);
    }

    /**
     * El canal existe y el servidor lo tiene configurado.
     */
    public Optional<NotificationChannel> available(ChannelType type, boolean required) {
        NotificationChannel channel = channels.get(type);
        if (channel != null && channel.isAvailable()) {
            return Optional.of(channel);
        }
        if (required) {
            throw ApiException.unavailable("El canal " + nameOf(type) + " no está configurado en este servidor");
        }
        return Optional.empty();
    }

    public void delivered(ChannelLink link) {
        link.setLastDeliveredAt(clock.instant());
        link.setFailures(0);
        repository.save(link);
    }

    /**
     * Tras varios fallos seguidos, o si el canal dice que el destino ya no existe, el vínculo se pausa.
     */
    public void failed(ChannelLink link, boolean permanent) {
        link.setFailures(link.getFailures() + 1);
        if (permanent || link.getFailures() >= 5) {
            link.setEnabled(false);
            log.info("Canal {} pausado para la cuenta {} tras {} fallos", link.getType(), link.getUserId(),
                    link.getFailures());
        }
        repository.save(link);
    }

    private NotificationChannel available(ChannelType type) {
        return available(type, true).orElseThrow();
    }

    private ChannelLink find(String userId, String linkId) {
        ObjectIds.require(linkId, NOT_FOUND);
        return repository.findByIdAndUserId(linkId, userId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }
}
