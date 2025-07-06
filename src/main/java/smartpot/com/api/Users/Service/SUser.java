package smartpot.com.api.Users.Service;

import jakarta.validation.ValidationException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartpot.com.api.Users.Mapper.MUser;
import smartpot.com.api.Users.Model.DTO.UserDTO;
import smartpot.com.api.Users.Model.Entity.UserRole;
import smartpot.com.api.Users.Repository.RUser;
import smartpot.com.api.Users.Validator.VUserI;

import java.text.SimpleDateFormat;
import java.util.Arrays;
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
    private final VUserI validatorUser;

    /**
     * Constructor que inyecta las dependencias del servicio.
     *
     * @param repositoryUser repositorio que maneja las operaciones de base de datos.
     * @param mapperUser     convertidor que convierte entidades User a UserDTO.
     * @param validatorUser  validador que valida los datos de usuario.
     */
    @Autowired
    public SUser(RUser repositoryUser, MUser mapperUser, VUserI validatorUser) {
        this.repositoryUser = repositoryUser;
        this.mapperUser = mapperUser;
        this.validatorUser = validatorUser;
    }

    /**
     * Crea un nuevo usuario en la base de datos a partir de un objeto {@link UserDTO}.
     * <br><br>
     * Este método valida que el usuario no exista previamente en la base de datos mediante su email.
     * Luego, realiza una serie de validaciones sobre los datos del usuario, como el nombre, apellido,
     * correo electrónico, contraseña y rol. Si las validaciones son exitosas, el usuario se crea y
     * se guarda en la base de datos. Si el usuario ya existe o si las validaciones fallan, se lanza una
     * excepción correspondiente.
     * <br><br>
     * **Transacciones (Spring Boot):** Este método está marcado con `@Transactional` de Spring, lo que significa
     * que la operación de base de datos se ejecuta dentro de una transacción. Si ocurre alguna excepción durante el
     * proceso (por ejemplo, si el usuario ya existe o alguna validación falla), la transacción será revertida
     * automáticamente, asegurando que ningún cambio se persista en la base de datos.
     * <br><br>
     * **Rollback:** Spring manejará el rollback de forma automática si se lanza una excepción no verificada (como una
     * `IllegalStateException` o `RuntimeException`). De este modo, si algo falla, se asegura la consistencia de la base de datos.
     * <br><br>
     *
     * @param userDTO el objeto {@link UserDTO} que contiene los datos del nuevo usuario.
     * @return un objeto {@link UserDTO} que representa al usuario creado.
     * @throws IllegalStateException si el usuario ya existe en la base de datos (por email).
     * @throws ValidationException   si las validaciones de los campos del usuario no son exitosas.
     * @throws ValidationException   si las validaciones de los campos del usuario no son exitosas.
     * @see UserDTO
     * @see ValidationException
     * @see Transactional
     */
    @Override
    @Transactional
    @CachePut(value = "users", key = "'email_' + #userDTO.email")
    public UserDTO CreateUser(UserDTO userDTO) throws ValidationException, IllegalStateException {
        return Optional.of(userDTO)
                .filter(dto -> !repositoryUser.existsByEmail(dto.getEmail()))
                .map(ValidDTO -> {
                    validatorUser.validateName(ValidDTO.getName());
                    validatorUser.validateLastname(ValidDTO.getLastname());
                    validatorUser.validateEmail(ValidDTO.getEmail());
                    validatorUser.validatePassword(ValidDTO.getPassword());
                    validatorUser.validateRole(ValidDTO.getRole());
                    if (!validatorUser.isValid()) {
                        throw new ValidationException(validatorUser.getErrors().toString());
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
                .orElseThrow(() -> new IllegalStateException("El Usuario ya existe"));
    }


    /**
     * Obtiene todos los usuarios de la base de datos y los convierte a DTOs.
     * *
     * Este método consulta todos los usuarios almacenados en la base de datos utilizando el
     * repositorio `repositoryUser`. Si la lista de usuarios está vacía, lanza una excepción.
     * Los usuarios obtenidos se mapean a objetos `UserDTO` utilizando el objeto `mapperUser`.
     *
     * @return una lista de objetos {@link UserDTO} que representan a todos los usuarios.
     * @throws Exception si no se encuentran usuarios en la base de datos.
     * @see UserDTO
     */
    @Override
    @Cacheable(value = "users", key = "'all_users'")
    public List<UserDTO> getAllUsers() throws Exception {
        return Optional.of(repositoryUser.findAll())
                .filter(users -> !users.isEmpty())
                .map(users -> users.stream()
                        .map(mapperUser::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No existe ningún usuario"));
    }

    /**
     * Obtiene un usuario de la base de datos a partir de su ID.
     * *
     * Este método busca un usuario en la base de datos utilizando el ID proporcionado.
     * Si el usuario existe, se valida el ID utilizando el validador. Si el ID es válido,
     * se convierte el usuario de entidad a DTO y se devuelve. Si el usuario no existe o
     * el ID no es válido, se lanza una excepción correspondiente.
     *
     * @param id el ID del usuario que se desea obtener. El ID debe ser una cadena que representa un {@link ObjectId}.
     * @return un objeto {@link UserDTO} que representa al usuario encontrado.
     * @throws Exception           si el usuario no existe en la base de datos o si el ID no es válido.
     * @throws ValidationException si el ID proporcionado no es válido según las reglas de validación.
     * @see UserDTO
     * @see ValidationException
     */
    @Override
    @Cacheable(value = "users", key = "'id_'+#id")
    public UserDTO getUserById(String id) throws Exception {
        return Optional.of(id)
                .map(ValidId -> {
                    validatorUser.validateId(ValidId);
                    if (!validatorUser.isValid()) {
                        throw new ValidationException(validatorUser.getErrors().toString());
                    }
                    validatorUser.Reset();
                    return new ObjectId(ValidId);
                })
                .map(repositoryUser::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(mapperUser::toDTO)
                .orElseThrow(() -> new Exception("El Usuario no existe"));
    }

    /**
     * Obtiene un usuario de la base de datos a partir de su correo electrónico.
     * *
     * Este método busca un usuario en la base de datos utilizando su correo electrónico.
     * Primero, valida que el correo proporcionado sea válido. Si el correo es válido,
     * realiza una búsqueda en la base de datos. Si el usuario con el correo electrónico
     * proporcionado existe, se convierte a un objeto {@link UserDTO} y se devuelve.
     * Si el correo no es válido o el usuario no existe, se lanzará una excepción correspondiente.
     *
     * @param email el correo electrónico del usuario que se desea obtener.
     * @return un objeto {@link UserDTO} que representa al usuario encontrado.
     * @throws Exception           si el usuario no existe en la base de datos o si el correo no es válido.
     * @throws ValidationException si el correo electrónico proporcionado no es válido según las reglas de validación.
     * @see UserDTO
     * @see ValidationException
     */
    @Override
    @Cacheable(value = "users", key = "'email_'+#email")
    public UserDTO getUserByEmail(String email) throws Exception {
        return Optional.of(email)
                .map(ValidEmail -> {
                    validatorUser.validateEmail(email);
                    if (!validatorUser.isValid()) {
                        throw new ValidationException(validatorUser.getErrors().toString());
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
     * Obtiene una lista de usuarios de la base de datos a partir de su nombre.
     * *
     * Este método busca usuarios en la base de datos utilizando el nombre proporcionado.
     * Primero, valida que el nombre proporcionado sea válido. Si el nombre es válido,
     * realiza una búsqueda en la base de datos. Si existen usuarios con el nombre proporcionado,
     * se convierten a una lista de objetos {@link UserDTO} y se devuelven. Si el nombre no es válido
     * o no se encuentran usuarios con ese nombre, se lanza una excepción correspondiente.
     *
     * @param name el nombre del usuario que se desea obtener.
     * @return una lista de objetos {@link UserDTO} que representan a los usuarios encontrados.
     * @throws Exception           si no existen usuarios con el nombre proporcionado o si el nombre no es válido.
     * @throws ValidationException si el nombre proporcionado no es válido según las reglas de validación.
     * @see UserDTO
     * @see ValidationException
     */
    @Override
    @Cacheable(value = "users", key = "'name_'+#name")
    public List<UserDTO> getUsersByName(String name) throws Exception {
        return Optional.of(name)
                .map(ValidName -> {
                    validatorUser.validateName(ValidName);
                    if (!validatorUser.isValid()) {
                        throw new ValidationException(validatorUser.getErrors().toString());
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
     * Obtiene una lista de usuarios de la base de datos a partir de su apellido.
     * *
     * Este método busca usuarios en la base de datos utilizando el apellido proporcionado.
     * Primero, valida que el apellido proporcionado sea válido. Si el apellido es válido,
     * realiza una búsqueda en la base de datos. Si existen usuarios con el apellido proporcionado,
     * se convierten a una lista de objetos {@link UserDTO} y se devuelven. Si el apellido no es válido
     * o no se encuentran usuarios con ese apellido, se lanza una excepción correspondiente.
     *
     * @param lastname el apellido del usuario o los usuarios que se desea obtener.
     * @return una lista de objetos {@link UserDTO} que representan a los usuarios encontrados.
     * @throws Exception           si no existen usuarios con el apellido proporcionado o si el apellido no es válido.
     * @throws ValidationException si el apellido proporcionado no es válido según las reglas de validación.
     * @see UserDTO
     * @see ValidationException
     */
    @Override
    @Cacheable(value = "users", key = "'lastname_'+#lastname")
    public List<UserDTO> getUsersByLastname(String lastname) throws Exception {
        return Optional.of(lastname)
                .map(ValidLastname -> {
                    validatorUser.validateLastname(ValidLastname);
                    if (!validatorUser.isValid()) {
                        throw new ValidationException(validatorUser.getErrors().toString());
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
     * Obtiene una lista de usuarios de la base de datos a partir de su rol.
     * *
     * Este método busca usuarios en la base de datos utilizando el rol proporcionado.
     * Primero, valida que el rol proporcionado sea válido. Si el rol es válido,
     * realiza una búsqueda en la base de datos. Si existen usuarios con ese rol,
     * se convierten a una lista de objetos {@link UserDTO} y se devuelven. Si el rol no es válido
     * o no se encuentran usuarios con ese rol, se lanza una excepción correspondiente.
     *
     * @param role el rol del usuario o los usuarios que se desea obtener.
     * @return una lista de objetos {@link UserDTO} que representan a los usuarios encontrados.
     * @throws Exception           si no existen usuarios con el rol proporcionado o si el rol no es válido.
     * @throws ValidationException si el rol proporcionado no es válido según las reglas de validación.
     * @see UserDTO
     * @see ValidationException
     */
    @Override
    @Cacheable(value = "users", key = "'role:'+#role")
    public List<UserDTO> getUsersByRole(String role) throws Exception {
        return Optional.of(role)
                .map(ValidRole -> {
                    validatorUser.validateRole(ValidRole);
                    if (!validatorUser.isValid()) {
                        throw new ValidationException(validatorUser.getErrors().toString());
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
     * Obtiene todos los roles de usuario de la base de datos.
     * *
     * Este método consulta todos los roles de usuario almacenados en la base de datos utilizando el
     * enum `Role`. Si la lista de roles de usuario está vacía, lanza una excepción.
     *
     * @return una lista de objetos {@link String} que representan a todos los roles de usuario.
     * @throws Exception si no se encuentra ningún rol de usuario en la base de datos.
     * @see UserRole
     */
    @Override
    @Cacheable(value = "users", key = "'all_rols'")
    public List<String> getAllRoles() throws Exception {
        return Optional.of(
                        Arrays.stream(UserRole.values())
                                .map(Enum::name)
                                .collect(Collectors.toList())
                )
                .filter(roles -> !roles.isEmpty())
                .orElseThrow(() -> new Exception("No existe ningún rol"));
    }

    /**
     * Actualiza la información de un usuario en la base de datos.
     * <br><br>
     * Este método permite actualizar los detalles de un usuario existente en la base de datos. Primero,
     * se busca el usuario por su ID. Si el usuario existe, se actualizan los campos del usuario con los
     * valores proporcionados en el objeto {@link UserDTO}. Luego, se validan los nuevos valores antes de
     * guardar el usuario actualizado. Si alguno de los valores es inválido, se lanza una excepción de validación.
     * <br><br>
     * **Transacciones (Spring Boot):** Al igual que el método `CreateUser`, este método está marcado con la anotación
     * `@Transactional`, lo que significa que la operación de actualización se ejecutará dentro de una transacción de Spring.
     * Si ocurre algún error, la transacción se revertirá, asegurando que los cambios no se apliquen si algo falla en el proceso.
     * <br><br>
     * **Rollback:** Las excepciones que extienden `RuntimeException` causarán un rollback automático, mientras que
     * las excepciones comprobadas, como `ValidationException`, no harán que la transacción se revierta a menos que se
     * indique explícitamente lo contrario.
     *
     * @param id          el identificador del usuario a actualizar.
     * @param updatedUser el objeto {@link UserDTO} que contiene los nuevos valores para el usuario.
     * @return un objeto {@link UserDTO} con la información actualizada del usuario.
     * @throws Exception           si el usuario no se pudo actualizar debido a algún error general.
     * @throws ValidationException si alguno de los campos del usuario proporcionado no es válido según las reglas de validación.
     * @see UserDTO
     * @see ValidationException
     * @see Transactional
     */
    @Override
    @Transactional
    @CachePut(value = "users", key = "'id:'+#id")
    public UserDTO UpdateUser(String id, UserDTO updatedUser) throws Exception {
        //noinspection SpringCacheableMethodCallsInspection
        UserDTO existingUser = getUserById(id);
        return Optional.of(updatedUser)
                .map(dto -> {
                    existingUser.setName(dto.getName() != null ? dto.getName() : existingUser.getName());
                    existingUser.setLastname(dto.getLastname() != null ? dto.getLastname() : existingUser.getLastname());
                    existingUser.setEmail(dto.getEmail() != null ? dto.getEmail() : existingUser.getEmail());
                    existingUser.setPassword(dto.getPassword() != null ? dto.getPassword() : existingUser.getPassword());
                    existingUser.setRole(dto.getRole() != null ? dto.getRole() : existingUser.getRole());
                    return existingUser;
                })
                .map(dto -> {
                    validatorUser.validateName(dto.getName());
                    validatorUser.validateLastname(dto.getLastname());
                    validatorUser.validateEmail(dto.getEmail());
                    validatorUser.validatePassword(dto.getPassword());
                    validatorUser.validateRole(dto.getRole());
                    if (!validatorUser.isValid()) {
                        throw new ValidationException(validatorUser.getErrors().toString());
                    }

                    validatorUser.Reset();
                    return dto;
                })
                .map(mapperUser::toEntity)
                .map(repositoryUser::save)
                .map(mapperUser::toDTO)
                .orElseThrow(() -> new Exception("El usuario no se pudo actualizar"));
    }

    /**
     * Elimina un usuario de la base de datos.
     * <br><br>
     * Este método permite eliminar un usuario de la base de datos utilizando su ID. Primero, se verifica
     * si el usuario existe. Si el usuario existe, se elimina de la base de datos. Si no se encuentra
     * el usuario con el ID proporcionado, se lanza una excepción indicando que el usuario no existe.
     * <br><br>
     * **Transacciones (Spring Boot):** Este método también está marcado con `@Transactional`, lo que garantiza
     * que si ocurre algún error durante la eliminación (por ejemplo, si el usuario no existe o si hay un fallo
     * en la base de datos), la transacción será revertida y no se eliminará el usuario.
     * <br><br>
     * **Rollback:** Al igual que los otros métodos, si ocurre una excepción de tipo `RuntimeException`, Spring
     * realizará un rollback automáticamente para mantener la integridad de los datos.
     *
     * @param id el identificador del usuario que se desea eliminar.
     * @return un mensaje indicando que el usuario ha sido eliminado correctamente.
     * @throws Exception si el usuario no existe o si ocurre un error durante el proceso de eliminación.
     * @see UserDTO
     * @see Transactional
     */
    @Override
    @Transactional
    @CacheEvict(value = "users", key = "'id_'+#id")
    public String DeleteUser(String id) throws Exception {
        //noinspection SpringCacheableMethodCallsInspection
        return Optional.of(getUserById(id))
                .map(user -> {
                    repositoryUser.deleteById(new ObjectId(user.getId()));
                    return "El Usuario con ID '" + id + "' fue eliminado.";
                })
                .orElseThrow(() -> new Exception("El Usuario no existe."));
    }

    /**
     * Carga los detalles del usuario a partir de su nombre de usuario (en este caso, el correo electrónico).
     * *
     * Este método implementa la interfaz  UserDetailsService de Spring Security y se utiliza para cargar
     * la información del usuario desde la base de datos a través de su nombre de usuario. El nombre de usuario
     * es en este caso el correo electrónico del usuario. Si no se encuentra un usuario con el correo electrónico
     * proporcionado, se lanza una excepción {@link UsernameNotFoundException}.
     *
     * @param username el correo electrónico del usuario que se desea cargar.
     * @return un objeto {@link UserDetails} que contiene la información del usuario cargado.
     * @throws UsernameNotFoundException si no se encuentra un usuario con el correo electrónico proporcionado.
     * @see UserDetails
     * @see UsernameNotFoundException
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
