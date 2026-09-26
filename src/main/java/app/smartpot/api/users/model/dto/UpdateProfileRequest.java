package app.smartpot.api.users.model.dto;

import app.smartpot.api.users.validator.UserPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 40, message = "El nombre debe tener entre 2 y 40 caracteres")
        @Pattern(regexp = UserPatterns.PERSON_NAME, message = "El nombre solo puede tener letras y espacios")
        String name,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(min = 2, max = 60, message = "El apellido debe tener entre 2 y 60 caracteres")
        @Pattern(regexp = UserPatterns.PERSON_NAME, message = "El apellido solo puede tener letras y espacios")
        String lastName
) {
}
