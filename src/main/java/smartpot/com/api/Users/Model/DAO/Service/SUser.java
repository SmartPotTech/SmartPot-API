package smartpot.com.api.Users.Model.DAO.Service;

import jakarta.validation.ValidationException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import smartpot.com.api.Exception.ApiException;
import smartpot.com.api.Exception.ApiResponse;
import smartpot.com.api.Users.Mapper.MUser;
import smartpot.com.api.Users.Model.DAO.Repository.RUser;
import smartpot.com.api.Users.Model.DTO.UserDTO;
import smartpot.com.api.Users.Model.Entity.Role;
import smartpot.com.api.Users.Model.Entity.User;
import smartpot.com.api.Users.Validation.VUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * Esta clase implementa los métodos de servicio para la gestión de usuarios.
 * Incluye la validación de campos y la interacción con el repositorio de usuarios.
 */
@Slf4j
@Data
@Builder
@Service
public class SUser implements SUserI {
    private final RUser repositoryUser;
    private final MUser mapperUser;
    private final VUser validatorUser;

    @Autowired
    public SUser(RUser repositoryUser, MUser mapperUser, VUser validatorUser) {
        this.repositoryUser = repositoryUser;
        this.mapperUser = mapperUser;
        this.validatorUser = validatorUser;
    }

    /**
     * Obtiene todos los usuarios registrados en la base de datos.
     *
     * @return Lista de usuarios.
     * @throws ApiException Si no se encuentran usuarios en la base de datos.
     */
    @Override
    public List<UserDTO> getAllUsers() throws Exception {
        return Optional.of(repositoryUser.findAll())
                .filter(users -> !users.isEmpty())
                .map(users -> users.stream()
                        .map(mapperUser::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No existe ningún usuario"));
    }

    /**
     * Crea un nuevo usuario, validando los datos antes de guardarlos.
     *
     * @param userDTO El usuario a crear.
     * @return El usuario creado.
     * @throws ApiException Sí ocurre algún error durante la creación.
     */
    @Override
    public UserDTO CreateUser(UserDTO userDTO) throws Exception {
        return Optional.of(userDTO)
                .filter(dto -> !repositoryUser.existsByEmail(dto.getEmail()))
                .map(ValidDTO -> {
                    validatorUser.validateName(userDTO.getName());
                    validatorUser.validateLastname(userDTO.getLastname());
                    validatorUser.validateEmail(userDTO.getEmail());
                    validatorUser.validatePassword(userDTO.getPassword());
                    validatorUser.validateRole(userDTO.getRole());
                    if (!validatorUser.valid) {
                        throw new ValidationException(validatorUser.errors.toString());
                    }
                    validatorUser.Reset();
                    return ValidDTO;
                })
                .map(dto -> {
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    dto.setCreateAt(formatter.format(new Date()));
                    return dto;
                })
                .map(mapperUser::toEntity)
                .map(repositoryUser::save)
                .map(mapperUser::toDTO)
                .orElseThrow(() -> new Exception("El Usuario ya existe"));
    }

    /**
     * Obtiene un usuario por su ID. Si el ID es válido, se busca el usuario en la base de datos.
     *
     * @param id El identificador del usuario a buscar.
     * @return El usuario encontrado.
     * @throws ApiException Si no se encuentra el usuario con el ID proporcionado.
     */
    @Override
    public UserDTO getUserById(String id) throws Exception {
        return repositoryUser.findById(new ObjectId(id))
                .map(ValidId -> {
                    validatorUser.validateId(id);
                    if (!validatorUser.valid) {
                        throw new ValidationException(validatorUser.errors.toString());
                    }
                    validatorUser.Reset();
                    return ValidId;
                })
                .map(mapperUser::toDTO)
                .orElseThrow(() -> new Exception("El Usuario no existe"));
    }

    /**
     * Obtiene un usuario por su correo electrónico.
     *
     * @param email El correo electrónico del usuario a buscar.
     * @return El usuario encontrado.
     */
    @Override
    public UserDTO getUserByEmail(String email) throws Exception {
        return Optional.of(email)
                .map(ValidEmail -> {
                    validatorUser.validateEmail(email);
                    if (!validatorUser.valid) {
                        throw new ValidationException(validatorUser.errors.toString());
                    }
                    validatorUser.Reset();
                    return ValidEmail;
                })
                .map(repositoryUser::findByEmail)
                .filter(users -> !users.isEmpty())
                .map(users -> users.get(0))
                .map(mapperUser::toDTO)
                .orElseThrow(() -> new Exception("El usuario no existe"));
    }

    /**
     * Obtiene una lista de usuarios filtrados por su nombre completo.
     *
     * @param name     El nombre del usuario.
     * @param lastname El apellido del usuario.
     * @return Lista de usuarios que coinciden con el nombre y apellido.
     * @throws ApiException Si no se encuentra un usuario con el nombre y apellido proporcionados.
     */
    @Override
    public List<User> getUsersByFullName(String name, String lastname) throws Exception {
        validatorUser.validateName(name);
        validatorUser.validateLastname(lastname);

        List<User> users = repositoryUser.findByFullName(name, lastname);
        if (users == null || users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontró ningún usuario con el nombre '" + name + "' y apellido '" + lastname + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        validatorUser.Reset();
        return users;
    }

    /**
     * Obtiene una lista de usuarios filtrados por su nombre.
     *
     * @param name El nombre del usuario.
     * @return Lista de usuarios que coinciden con el nombre.
     * @throws ApiException Si no se encuentra un usuario con el nombre proporcionado.
     */
    @Override
    public List<UserDTO> getUsersByName(String name) throws Exception {
        return Optional.of(name)
                .map(ValidName -> {
                    validatorUser.validateName(ValidName);
                    if (!validatorUser.valid) {
                        throw new ValidationException(validatorUser.errors.toString());
                    }
                    validatorUser.Reset();
                    return ValidName;
                })
                .map(repositoryUser::findByName)
                .filter(users -> !users.isEmpty())
                .map(users -> users.stream()
                        .map(mapperUser::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No existe ningún usuario con el nombre"));
    }

    /**
     * Obtiene una lista de usuarios filtrados por su apellido.
     *
     * @param lastname El apellido del usuario.
     * @return Lista de usuarios que coinciden con el apellido.
     * @throws ApiException Si no se encuentra un usuario con el apellido proporcionado.
     */
    @Override
    public List<UserDTO> getUsersByLastname(String lastname) throws Exception {
        return Optional.of(lastname)
                .map(ValidLastname -> {
                    validatorUser.validateLastname(ValidLastname);
                    if (!validatorUser.valid) {
                        throw new ValidationException(validatorUser.errors.toString());
                    }
                    validatorUser.Reset();
                    return ValidLastname;
                })
                .map(repositoryUser::findByLastname)
                .filter(users -> !users.isEmpty())
                .map(users -> users.stream()
                        .map(mapperUser::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No existe ningún usuario con el apellido"));
    }

    /**
     * Obtiene una lista de usuarios filtrados por su rol.
     * Este método permite recuperar todos los usuarios que tiene asignado un rol especifico.
     * Si no se encuentran usuarios con el rol proporcionado, se lanza una excepción.
     *
     * @param role El rol que se utilizará para filtrar los usuarios. Este valor debe ser una cadena que corresponda con uno de los roles definidos en la enumeración `Role`.
     * @return Una lista de usuarios que tienen el rol especificado.
     * @throws ApiException Si no se encuentran usuarios con el rol proporcionado.
     */
    @Override
    public List<UserDTO> getUsersByRole(String role) throws Exception {
        return Optional.of(role)
                .map(ValidRole -> {
                    validatorUser.validateRole(ValidRole);
                    if (!validatorUser.valid) {
                        throw new ValidationException(validatorUser.errors.toString());
                    }
                    validatorUser.Reset();
                    return ValidRole;
                })
                .map(repositoryUser::findByRole)
                .filter(users -> !users.isEmpty())
                .map(users -> users.stream()
                        .map(mapperUser::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No existe ningún usuario con el rol"));
    }

    /**
     * Actualiza la información de un usuario existente.
     *
     * @param existingUser El User del usuario a actualizar.
     * @param updatedUser  Los nuevos datos del usuario.
     * @return El usuario actualizado.
     * @throws ApiException Sí ocurre algún error durante la actualización.
     */
    @Override
    public User updateUser(User existingUser, UserDTO updatedUser) {

        if (updatedUser.getName() != null) {
            String name = updatedUser.getName();
            existingUser.setName(name);
        }

        if (updatedUser.getLastname() != null) {
            String lastname = updatedUser.getLastname();
            existingUser.setLastname(lastname);
        }

        if (updatedUser.getEmail() != null) {
            String email = updatedUser.getEmail();
            existingUser.setEmail(updatedUser.getEmail());
        }

        if (updatedUser.getRole() != null) {
            String role = updatedUser.getRole();
            existingUser.setRole(Role.valueOf(role));
        }

        if (updatedUser.getPassword() != null) {
            if (!new BCryptPasswordEncoder().matches(updatedUser.getPassword(), existingUser.getPassword())) {
                existingUser.setPassword(new BCryptPasswordEncoder().encode(updatedUser.getPassword()));
            }
        }

        try {
            return repositoryUser.save(existingUser);
        } catch (Exception e) {
            log.error("e: ", e);
            throw new ApiException(
                    new ApiResponse("No se pudo actualizar el usuario con ID '" + existingUser.getId() + "'.",
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Elimina un usuario por su ID.
     *
     * @param id del usuario con la finalidad de validar si existe, para poder eliminarlo.
     * @throws ApiException Si no se encuentra el usuario con el ID proporcionado.
     */
    @Override
    public String deleteUser(String id) throws Exception {
        return Optional.of(getUserById(id))
                .map(product -> {
                    repositoryUser.deleteById(new ObjectId(id));
                    return "El Usuario con ID '" + id + "' fue eliminado.";
                })
                .orElseThrow(() -> new Exception("El Usuario no existe."));
    }

    /**
     * Carga un usuario basado en su nombre de usuario (en este caso, el correo electrónico).
     * Este método es parte de la implementación de `UserDetailsService` de Spring Security.
     * Busca un usuario en la base de datos utilizando el correo electrónico como nombre de usuario.
     * Si no se encuentra un usuario con el correo electrónico proporcionado, se lanza una excepción `UsernameNotFoundException`.
     *
     * @param username El nombre de usuario (correo electrónico) con el que se desea autenticar al usuario.
     * @return Un objeto `UserDetails` que contiene la información del usuario autenticado, como su correo electrónico, contraseña y roles.
     * @throws UsernameNotFoundException Si no se encuentra un usuario con el correo electrónico proporcionado.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return repositoryUser.findByEmail(username).stream().findFirst()
                .map(user -> org.springframework.security.core.userdetails.User.builder()
                        .username(user.getEmail())
                        .password(user.getPassword())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con el email: " + username));
    }
}
