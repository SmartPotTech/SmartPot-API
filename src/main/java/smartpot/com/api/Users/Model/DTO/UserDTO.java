package smartpot.com.api.Users.Model.DTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.Serializable;

@Data
@RequiredArgsConstructor
public class UserDTO implements Serializable {
    @Schema(description = "ID único del usuario, generado automáticamente por la base de datos.",
            example = "676ae2a9b909de5f9607fcb6", hidden = true)
    private String id = null;

    @Schema(description = "Nombre del usuario. Este campo es obligatorio.",
            example = "Juan")
    private String name;

    @Schema(description = "Apellido del usuario. Este campo es obligatorio.",
            example = "Pérez")
    private String lastname;

    @Schema(description = "Correo electrónico del usuario. Este campo es obligatorio y debe ser único.",
            example = "usuario@example.com")
    private String email;

    @Schema(description = "Fecha de creación del usuario. Este campo es generado automáticamente en el momento de la creación y es oculto en las respuestas API.",
            example = "2024-12-24 11:34:49", hidden = true)
    private String createAt = null;

    @Schema(description = "Contraseña del usuario. Este campo es obligatorio y debe ser segura. Se almacenará de forma cifrada.",
            example = "Password123@")
    private String password;

    @Schema(description = "Rol asignado al usuario. Este campo define los permisos del usuario en el sistema. Ejemplo de valores: 'USER', 'ADMIN'.",
            example = "USER")
    private String role;
}
