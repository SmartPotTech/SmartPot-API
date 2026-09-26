package app.smartpot.api.notifications.controller;

import app.smartpot.api.notifications.mapper.NotificationMapper;
import app.smartpot.api.notifications.model.dto.NotificationResponse;
import app.smartpot.api.notifications.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notificaciones", description = "Alertas de los cultivos, del dispositivo y del asistente de IA")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Listar mis notificaciones", description = "Más recientes primero; máximo 100")
    public List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(defaultValue = "false") boolean unreadOnly,
                                           @RequestParam(defaultValue = "50") int limit) {
        return notificationService.list(jwt.getSubject(), unreadOnly, limit).stream()
                .map(NotificationMapper::toResponse)
                .toList();
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Contar notificaciones sin leer")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("unread", notificationService.unreadCount(jwt.getSubject()));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Marcar una notificación como leída")
    public NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return NotificationMapper.toResponse(notificationService.markRead(jwt.getSubject(), id));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Marcar todas como leídas")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        notificationService.markAllRead(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una notificación")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        notificationService.delete(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }
}
