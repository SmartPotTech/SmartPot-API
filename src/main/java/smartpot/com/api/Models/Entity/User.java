package smartpot.com.api.Models.Entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "usuarios")
public class User implements Serializable {

    @Id
    private String id;

    @NotEmpty(message = "El nombre no puede estar vacío")
    @Size(min = 4, max = 15, message = "El nombre debe tener entre 4 y 15 caracteres")
    private String name;

    @NotEmpty(message = "El apellido no puede estar vacío")
    @Size(min = 4, max = 30, message = "El apellido debe tener entre 4 y 30 caracteres")
    private String lastname;

    @NotEmpty(message = "El email no puede estar vacío")
    @Size(min = 14, max = 60, message = "El email debe tener entre 14 y 60 caracteres")
    @Email(message = "El email debe ser válido")
    @Indexed(unique = true)
    private String email;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date createAt;

    @NotEmpty(message = "La contraseña no puede estar vacío")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "La contraseña debe tener al menos 8 caracteres, " +
                    "una letra mayúscula, una letra minúscula, " +
                    "un número y un carácter especial.")
    private String password;

    @NotEmpty(message = "El rol no puede estar vacío")
    private String role;

    public void setCreateAt() {
        this.createAt = new Date();
    }
}
