package app.smartpot.api.security.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "El enlace de recuperación no es válido")
        @Size(max = 200, message = "El enlace de recuperación no es válido")
        String token,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        String password
) {
}
