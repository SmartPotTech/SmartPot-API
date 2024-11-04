package smartpot.com.api.Models.Entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.constraints.*;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import smartpot.com.api.Validation.ObjectIdSerializer;

import java.io.Serializable;
import java.util.Date;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password")
@Document(collection = "usuarios")
public class User implements Serializable {
    /**
     * Representa un usuario en el sistema.
     * * Esta clase contiene la información básica de un usuario, incluyendo
     * * atributos como nombre, apellido, correo electrónico, fecha de registro,
     * * contraseña y rol. Se utiliza en la colección "usuarios" de MongoDB.
     * TODO: Considerar implementar un método para hacer hash de la contraseña antes de almacenarla.
     * ! Asegurarse de que los datos sensibles como contraseñas estén protegidos.
     * ? ¿Qué medidas se tomarán si un usuario olvida su contraseña?
     */

    @Id
    @JsonSerialize(using = ObjectIdSerializer.class)
    @Field("_id")
    private ObjectId id;

    @NotEmpty(message = "El nombre no puede estar vacío")
    @Size(min = 4, max = 15, message = "El nombre debe tener entre 4 y 15 caracteres")
    @Field("name")
    private String name;

    @NotEmpty(message = "El apellido no puede estar vacío")
    @Size(min = 4, max = 30, message = "El apellido debe tener entre 4 y 30 caracteres")
    @Field("lastname")
    private String lastname;

    @NotEmpty(message = "El email no puede estar vacío")
    @Size(min = 14, max = 60, message = "El email debe tener entre 14 y 60 caracteres")
    @Email(message = "El email debe ser válido")
    @Indexed(unique = true)
    @Field("email")
    private String email;

    @NotNull(message = "La fecha de registro no puede estar vacía")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    @Field("create_at")
    private Date createAt = new Date();

    @NotEmpty(message = "La contraseña no puede estar vacío")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "La contraseña debe tener al menos 8 caracteres, " +
                    "una letra mayúscula, una letra minúscula, " +
                    "un número y un carácter especial.")
    @Field("password")
    private String password;

    @NotEmpty(message = "El rol no puede estar vacío")
    @Field("role")
    private Role role;
}
