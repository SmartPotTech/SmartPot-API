package smartpot.com.api.Users.Model.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {
    @Schema(description = "ID del usuario", hidden = true)
    private String id = null;

    private String name;

    private String lastname;

    @Schema(description = "Correo electrónico del usuario. Este campo es obligatorio y debe ser único.",
            example = "usuario@ejemplo.com")
    private String email;

    @Schema(description = "Contraseña del usuario. Este campo es obligatorio.",
            example = "password123")
    private String password;
    private String role;
}
