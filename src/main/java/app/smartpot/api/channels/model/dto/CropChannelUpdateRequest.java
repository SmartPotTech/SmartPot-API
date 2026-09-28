package app.smartpot.api.channels.model.dto;

import app.smartpot.api.channels.model.entity.CropChannel;
import app.smartpot.api.notifications.model.entity.NotificationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

/**
 * Cambios en los avisos de un cultivo; lo que falta se conserva. dailySummaryAt vacío apaga el resumen diario.
 */
public record CropChannelUpdateRequest(
        Boolean enabled,
        Set<NotificationType> events,
        CropChannel.Delivery delivery,
        @Min(value = 1, message = "El resumen debe llegar al menos cada hora")
        @Max(value = 24, message = "El resumen debe llegar al menos una vez al día")
        Integer digestHours,
        @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Usa el formato HH:mm, por ejemplo 07:30")
        String dailySummaryAt
) {
}
