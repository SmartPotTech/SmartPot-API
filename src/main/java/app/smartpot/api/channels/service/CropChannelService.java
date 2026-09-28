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
import app.smartpot.api.crops.model.event.CropDeletedEvent;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.notifications.model.entity.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Avisos de cada cultivo por los canales externos: qué avisar, si al instante o en un resumen cada tantas horas, un
 * resumen diario a una hora fija y los chats con los que se comparte. Para compartir, el dueño genera un enlace de un
 * solo uso (10 minutos) y quien lo abre en el canal empieza a recibir los avisos de ese cultivo, sin entrar a la
 * cuenta.
 */
@Slf4j
@Service
public class CropChannelService {

    public static final int DEFAULT_DIGEST_HOURS = 6;
    /**
     * Los códigos para compartir se distinguen de los de vinculación por este prefijo.
     */
    static final String SHARE_PREFIX = "c_";
    private static final String RECIPIENT_NOT_FOUND = "Ese chat no recibe los avisos de este cultivo";

    private final CropChannelRepository repository;
    private final ChannelService channelService;
    private final CropService cropService;
    private final CacheStore cacheStore;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CropChannelService(CropChannelRepository repository, ChannelService channelService, CropService cropService,
                              CacheStore cacheStore, Clock clock) {
        this.repository = repository;
        this.channelService = channelService;
        this.cropService = cropService;
        this.cacheStore = cacheStore;
        this.clock = clock;
    }

    public static boolean isShareCode(String code) {
        return code != null && code.startsWith(SHARE_PREFIX);
    }

    private static String codeKey(ChannelType type, String code) {
        return "crop-share:" + type.name().toLowerCase() + ":" + code;
    }

    public List<CropChannelResponse> list(String ownerId, String cropId) {
        Crop crop = cropService.getOwned(ownerId, cropId);
        return Arrays.stream(ChannelType.values()).map(type -> response(crop, settings(crop, type))).toList();
    }

    public CropChannelResponse update(String ownerId, String cropId, ChannelType type,
                                      CropChannelUpdateRequest request) {
        Crop crop = cropService.getOwned(ownerId, cropId);
        channelService.available(type, true);
        CropChannel settings = settings(crop, type);
        Instant now = clock.instant();
        if (request.enabled() != null) {
            settings.setEnabled(request.enabled());
        }
        if (request.events() != null) {
            settings.setEvents(request.events().isEmpty() ? EnumSet.noneOf(NotificationType.class)
                    : EnumSet.copyOf(request.events()));
        }
        if (request.delivery() != null && request.delivery() != settings.getDelivery()) {
            settings.setDelivery(request.delivery());
            // El primer resumen cuenta desde ahora: no se reenvía lo que ya llegó al instante.
            settings.setLastDigestAt(now);
        }
        if (request.digestHours() != null) {
            settings.setDigestHours(request.digestHours());
        }
        if (request.dailySummaryAt() != null) {
            String at = request.dailySummaryAt().isBlank() ? null : request.dailySummaryAt();
            if (!Objects.equals(at, settings.getDailySummaryAt())) {
                settings.setDailySummaryAt(at);
                // Si la hora ya pasó hoy, el primero llega mañana.
                settings.setLastSummaryAt(now);
            }
        }
        settings.setUpdatedAt(now);
        return response(crop, repository.save(settings));
    }

    public LinkCodeResponse shareCode(String ownerId, String cropId, ChannelType type) {
        Crop crop = cropService.getOwned(ownerId, cropId);
        NotificationChannel channel = channelService.available(type, true).orElseThrow();
        if (settings(crop, type).getRecipients().size() >= CropChannel.MAX_RECIPIENTS) {
            throw ApiException.badRequest("Puedes compartir un cultivo con hasta " + CropChannel.MAX_RECIPIENTS
                    + " chats");
        }
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        String code = SHARE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        cacheStore.put(codeKey(type, code), crop.getId(), ChannelService.CODE_TTL);
        return new LinkCodeResponse(type, code, channel.linkUrl(code), clock.instant().plus(ChannelService.CODE_TTL));
    }

    /**
     * Completa la invitación desde el canal: el chat queda recibiendo los avisos del cultivo.
     */
    public Optional<Crop> acceptShare(ChannelType type, String code, String address, String displayName) {
        if (!isShareCode(code) || code.length() > 64) {
            return Optional.empty();
        }
        Optional<Crop> crop = cacheStore.take(codeKey(type, code)).flatMap(cropService::find);
        crop.ifPresent(target -> {
            CropChannel settings = settings(target, type);
            List<CropChannel.Recipient> recipients = new ArrayList<>(settings.getRecipients());
            recipients.removeIf(recipient -> recipient.address().equals(address));
            if (recipients.size() >= CropChannel.MAX_RECIPIENTS) {
                return;
            }
            recipients.add(new CropChannel.Recipient(UUID.randomUUID().toString(), address, displayName,
                    clock.instant()));
            settings.setRecipients(recipients);
            settings.setUpdatedAt(clock.instant());
            repository.save(settings);
            log.info("Cultivo {} compartido con un chat de {}", target.getId(), type);
        });
        return crop;
    }

    public void removeRecipient(String ownerId, String cropId, ChannelType type, String recipientId) {
        Crop crop = cropService.getOwned(ownerId, cropId);
        CropChannel settings = repository.findByCropIdAndType(crop.getId(), type)
                .orElseThrow(() -> ApiException.notFound(RECIPIENT_NOT_FOUND));
        if (!settings.getRecipients().removeIf(recipient -> recipient.id().equals(recipientId))) {
            throw ApiException.notFound(RECIPIENT_NOT_FOUND);
        }
        settings.setUpdatedAt(clock.instant());
        repository.save(settings);
    }

    /**
     * Cultivos que se comparten con un chat.
     */
    public List<Crop> sharedWith(ChannelType type, String address) {
        return repository.findByTypeAndRecipientsAddress(type, address).stream()
                .map(settings -> cropService.find(settings.getCropId()))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * El chat deja de recibir los avisos de todos los cultivos compartidos con él.
     */
    public int leave(ChannelType type, String address) {
        List<CropChannel> shared = repository.findByTypeAndRecipientsAddress(type, address);
        shared.forEach(settings -> removeAddress(settings, address));
        return shared.size();
    }

    public Optional<CropChannel> find(String cropId, ChannelType type) {
        return repository.findByCropIdAndType(cropId, type);
    }

    void removeAddress(CropChannel settings, String address) {
        if (settings.getRecipients().removeIf(recipient -> recipient.address().equals(address))) {
            settings.setUpdatedAt(clock.instant());
            repository.save(settings);
        }
    }

    public void deleteAllForOwner(String ownerId) {
        repository.deleteByOwnerId(ownerId);
    }

    @Async
    @EventListener
    public void onCropDeleted(CropDeletedEvent event) {
        repository.deleteByCropId(event.cropId());
    }

    /**
     * Lo guardado o, si no hay nada, lo que el dueño eligió en su perfil, al instante.
     */
    CropChannel settings(Crop crop, ChannelType type) {
        return repository.findByCropIdAndType(crop.getId(), type).orElseGet(() -> CropChannel.builder()
                .cropId(crop.getId())
                .ownerId(crop.getOwnerId())
                .type(type)
                .enabled(true)
                .events(EnumSet.copyOf(channelService.link(crop.getOwnerId(), type)
                        .map(ChannelLink::getEvents)
                        .filter(events -> !events.isEmpty())
                        .orElse(ChannelService.DEFAULT_EVENTS)))
                .delivery(CropChannel.Delivery.INSTANT)
                .digestHours(DEFAULT_DIGEST_HOURS)
                .recipients(new ArrayList<>())
                .build());
    }

    private CropChannelResponse response(Crop crop, CropChannel settings) {
        ChannelType type = settings.getType();
        return new CropChannelResponse(type, ChannelService.nameOf(type),
                channelService.available(type, false).isPresent(),
                channelService.link(crop.getOwnerId(), type).isPresent(), settings.isEnabled(),
                settings.getEvents() == null ? EnumSet.noneOf(NotificationType.class) : settings.getEvents(),
                settings.getDelivery(), settings.getDigestHours(), settings.getDailySummaryAt(),
                settings.getRecipients().stream()
                        .map(recipient -> new CropChannelResponse.RecipientResponse(recipient.id(),
                                recipient.displayName(), recipient.addedAt()))
                        .toList());
    }
}
