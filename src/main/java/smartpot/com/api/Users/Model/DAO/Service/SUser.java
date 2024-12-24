package smartpot.com.api.Users.Model.DAO.Service;

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

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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

    @Autowired
    public SUser(RUser repositoryUser, MUser mapperUser) {
        this.repositoryUser = repositoryUser;
        this.mapperUser = mapperUser;
    }

    /**
     * Patrón para validar nombres:
     * - Solo permite letras (mayúsculas y minúsculas).
     * - Longitud entre 4 y 15 caracteres.
     */
    public static final String NAME_PATTERN = "^[a-zA-Z]{4,15}$";

    /**
     * Patrón para validar apellidos:
     * - Solo permite letras (mayúsculas y minúsculas).
     * - Longitud entre 4 y 30 caracteres.
     */
    public static final String LASTNAME_PATTERN = "^[a-zA-Z]{4,30}$";

    /**
     * Patrón para validar correos electrónicos:
     * - Asegura que haya un texto antes y después del símbolo @.
     * - Requiere un dominio válido con un punto (.) después del símbolo @.
     * - No permite espacios en ninguna parte del correo.
     */
    public static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    /**
     * Patrón para validar contraseñas:
     * - Debe contener al menos 8 caracteres.
     * - Debe tener al menos una letra minúscula, una letra mayúscula, un dígito y un carácter especial.
     * - Permite letras (mayúsculas y minúsculas), dígitos y los caracteres especiales: @$!%*?&.
     */
    public static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";


    /**
     * Válida si el ID proporcionado es un ObjectId válido de MongoDB.
     *
     * @param id El identificador a validar.
     * @throws ApiException Si el ID no es válido.
     */
    @Override
    public void ValidationId(String id) {
        if (!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "El id '" + id + "' no es válido. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
    }

    /**
     * Válida que el nombre cumpla con el patrón especificado.
     *
     * @param name El nombre a validar.
     * @throws ApiException Si el nombre no es válido.
     */
    @Override
    public void ValidationName(String name) {
        if (!Pattern.matches(NAME_PATTERN, name)) {
            throw new ApiException(new ApiResponse(
                    "El nombre '" + name + "' no es válido. Debe tener entre 4 y 15 caracteres y solo letras.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
    }

    /**
     * Válida que el apellido cumpla con el patrón especificado.
     *
     * @param lastname El apellido a validar.
     * @throws ApiException Si el apellido no es válido.
     */
    @Override
    public void ValidationLastname(String lastname) {
        if (!Pattern.matches(LASTNAME_PATTERN, lastname)) {
            throw new ApiException(new ApiResponse(
                    "El apellido '" + lastname + "' no es valido. El apellido debe tener entre 4 y 30 caracteres",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
    }

    /**
     * Válida que el correo electrónico cumpla con el patrón especificado.
     *
     * @param email El correo electrónico a validar.
     * @throws ApiException Si el correo no es válido.
     */
    @Override
    public void ValidationEmail(String email) {
        if (!Pattern.matches(EMAIL_PATTERN, email)) {
            throw new ApiException(new ApiResponse(
                    "El correo electrónico '" + email + "' no es válido. Asegúrate de que sigue el formato correcto.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
    }

    /**
     * Válida que la contraseña cumpla con el patrón especificado.
     *
     * @param password La contraseña a validar.
     * @throws ApiException Si la contraseña no es válida.
     */
    @Override
    public void ValidationPassword(String password) {
        if (!Pattern.matches(PASSWORD_PATTERN, password)) {
            throw new ApiException(new ApiResponse(
                    "La Contraseña '" + password + "' no es válida. La contraseña debe tener al menos 8 caracteres, una letra mayúscula, una letra minúscula, un número y un carácter especial.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
    }

    /**
     * Válida que el rol proporcionado sea un rol válido.
     *
     * @param role El rol a validar.
     * @throws ApiException Si el rol no es válido.
     */
    @Override
    public void ValidationRole(String role) {
        if (role == null || role.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "El rol no puede estar vacío",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        boolean isValidRole = Stream.of(Role.values())
                .anyMatch(r -> r.name().equalsIgnoreCase(role));

        if (!isValidRole) {
            throw new ApiException(new ApiResponse(
                    "El Rol '" + role + "' es no válido.",
                    HttpStatus.BAD_REQUEST.value()));
        }
    }

    /**
     * Valida que el correo electrónico proporcionado no esté en uso.
     *
     * @param email El correo electrónico a verificar.
     * @throws ApiException Si el correo ya está en uso.
     */
    @Override
    public void isEmailExist(String email) {
        if (!repositoryUser.findByEmail(email).isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "El email '" + email + "' ya está en uso.",
                    HttpStatus.CONFLICT.value()
            ));
        }
    }

    /**
     * Obtiene todos los usuarios registrados en la base de datos.
     *
     * @return Lista de usuarios.
     * @throws ApiException Si no se encuentran usuarios en la base de datos.
     */
    @Override
    public List<User> getAllUsers() {
        List<User> users = repositoryUser.findAll();
        if (users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontró ningún usuario en la db",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return users;
    }

    /**
     * Crea un nuevo usuario, validando los datos antes de guardarlos.
     *
     * @param userDTO El usuario a crear.
     * @return El usuario creado.
     * @throws ApiException Sí ocurre algún error durante la creación.
     */
    @Override
    public User CreateUser(UserDTO userDTO) {
        ValidationName(userDTO.getName());
        ValidationLastname(userDTO.getLastname());
        ValidationEmail(userDTO.getEmail());
        ValidationPassword(userDTO.getPassword());
        ValidationRole(userDTO.getRole());
        isEmailExist(userDTO.getEmail());
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String hashedPassword = passwordEncoder.encode(userDTO.getPassword());
        userDTO.setPassword(hashedPassword);

        User newUser = new User(userDTO);
        newUser.setCreateAt(new Date());

        try {
            return repositoryUser.save(newUser);
        } catch (Exception e) {
            log.error("e: ", e);
            throw new ApiException(
                    new ApiResponse("No se pudo crear el usuario.",
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
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
    public List<User> getUsersByFullName(String name, String lastname) {
        ValidationName(name);
        ValidationLastname(lastname);

        List<User> users = repositoryUser.findByFullName(name, lastname);
        if (users == null || users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontró ningún usuario con el nombre '" + name + "' y apellido '" + lastname + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
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
        return Optional.ofNullable(repositoryUser.findByName(name))
                .filter(users -> !users.isEmpty())
                .map(users -> users.stream()
                        .map(mapperUser::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No se encontró ningún usuario con el nombre"));
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
        return Optional.ofNullable(repositoryUser.findByLastname(lastname))
                .filter(users -> !users.isEmpty())
                .map(users -> users.stream()
                        .map(mapperUser::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No se encontró ningún usuario con el apellido"));
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
        return Optional.ofNullable(repositoryUser.findByRole(role))
                .filter(users -> !users.isEmpty())
                .map(users -> users.stream()
                        .map(mapperUser::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No se encontró ningún usuario con el rol"));
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
            ValidationName(name);
            existingUser.setName(name);
        }

        if (updatedUser.getLastname() != null) {
            String lastname = updatedUser.getLastname();
            ValidationLastname(lastname);
            existingUser.setLastname(lastname);
        }

        if (updatedUser.getEmail() != null) {
            String email = updatedUser.getEmail();
            ValidationEmail(email);
            isEmailExist(email);
            existingUser.setEmail(updatedUser.getEmail());
        }

        if (updatedUser.getRole() != null) {
            String role = updatedUser.getRole();
            ValidationRole(role);
            existingUser.setRole(Role.valueOf(role));
        }

        if (updatedUser.getPassword() != null) {
            if (!new BCryptPasswordEncoder().matches(updatedUser.getPassword(), existingUser.getPassword())) {
                ValidationPassword(updatedUser.getPassword());
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
