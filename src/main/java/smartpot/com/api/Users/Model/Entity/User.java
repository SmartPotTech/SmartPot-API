package smartpot.com.api.Users.Model.Entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import smartpot.com.api.Users.Model.DTO.UserDTO;

import java.io.Serializable;
import java.util.Date;


/**
 * Representa un usuario en el sistema.
 * <p>
 * Esta clase contiene los datos esenciales de un usuario, tales como nombre, apellido,
 * correo electrónico, contraseña, fecha de registro y rol. Se utiliza para almacenar y recuperar
 * la información de los usuarios desde la colección "usuarios" en MongoDB.
 * <p>
 * La clase incluye validaciones para asegurar que los datos sean correctos antes de persistirlos.
 * Además, la contraseña se encripta antes de almacenarse en la base de datos, utilizando BCrypt.
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password")
@Document(collection = "usuarios")
public class User implements Serializable {
    /**
     * TODO: Considerar implementar un método para hacer hash de la contraseña antes de almacenarla.
     * ! Asegurarse de que los datos sensibles como contraseñas estén protegidos.
     * ? ¿Qué medidas se tomarán si un usuario olvida su contraseña?
     */

    @Id
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
    @Size(min = 14, max = 254, message = "El email debe tener entre 14 y 254 caracteres")
    @Email(message = "El email debe ser válido")
    @Indexed(unique = true)
    @Field("email")
    private String email;

    @NotNull(message = "La fecha de registro no puede estar vacía")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    @Field("create_at")
    private Date createAt;

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

    /**
     * Constructor que mapea los datos desde un {@link UserDTO}.
     * Este constructor toma los datos de un DTO y los convierte a la entidad {@link User}.
     * La contraseña se encripta utilizando BCrypt antes de ser almacenada.
     *
     * @param userDTO El objeto DTO con los datos del usuario.
     */
    public User(UserDTO userDTO) {
        this.name = userDTO.getName();
        this.lastname = userDTO.getLastname();
        this.email = userDTO.getEmail();
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        this.password = passwordEncoder.encode(userDTO.getPassword());
        this.role = Role.valueOf(userDTO.getRole());
    }
}
