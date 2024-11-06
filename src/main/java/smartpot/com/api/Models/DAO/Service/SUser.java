package smartpot.com.api.Models.DAO.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import smartpot.com.api.Models.DAO.Repository.RUser;
import smartpot.com.api.Models.Entity.Role;
import smartpot.com.api.Models.Entity.User;
import smartpot.com.api.Validation.Exception.ApiResponse;
import smartpot.com.api.Validation.Exception.ApiException;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;


@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Service
public class SUser implements UserDetailsService {
    /**
     * Esta clase implementa los métodos de servicio para la gestión de usuarios.
     * Incluye la validación de campos y la interacción con el repositorio de usuarios.
     */


    @Autowired
    private RUser repositoryUser;

    //Validations

    /* * Patrón para nombres y apellidos (mínimo 4, máximo 15 caracteres) */
    public static final String NAME_PATTERN = "^[a-zA-Z]{4,15}$";

    /* * Patrón para apellidos (mínimo 4, máximo 30 caracteres) */
    public static final String LASTNAME_PATTERN = "^[a-zA-Z]{4,30}$";

    /* * Patrón para correos electrónicos */
    public static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    /* * Patrón para contraseñas (8 caracteres, un digito, una minuscula, una mayuscula y un caracter especial) */
    public  static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";

    /**
     * Valida si el ID proporcionado es un ObjectId válido de MongoDB.
     *
     * @param id El identificador a validar.
     * @throws ApiException Si el ID no es válido.
     */
    private void ValidationId(String id){
        if(!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "El id '"+ id +"' no es válido. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
    }

    /**
     * Valida que el nombre cumpla con el patrón especificado.
     *
     * @param name El nombre a validar.
     * @throws ApiException Si el nombre no es válido.
     */
    private void ValidationName(String name){
        if (!Pattern.matches(NAME_PATTERN, name)) {
            throw new ApiException(new ApiResponse(
                    "El nombre '" + name + "' no es válido. Debe tener entre 4 y 15 caracteres y solo letras.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
    }

    /**
     * Valida que el apellido cumpla con el patrón especificado.
     *
     * @param lastname El apellido a validar.
     * @throws ApiException Si el apellido no es válido.
     */
    private void ValidationLastname(String lastname){
        if (!Pattern.matches(LASTNAME_PATTERN,lastname)) {
            throw new ApiException(new ApiResponse(
                    "El apellido '" + lastname + "' no es valido. El apellido debe tener entre 4 y 30 caracteres",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
    }

    /**
     * Valida que el correo electrónico cumpla con el patrón especificado.
     *
     * @param email El correo electrónico a validar.
     * @throws ApiException Si el correo no es válido.
     */
    private void ValidationEmail(String email){
        if (!Pattern.matches(EMAIL_PATTERN, email)) {
            throw new ApiException(new ApiResponse(
                    "El correo electrónico '" + email + "' no es válido. Asegúrate de que sigue el formato correcto.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
    }

    /**
     * Valida que la contraseña cumpla con el patrón especificado.
     *
     * @param password La contraseña a validar.
     * @throws ApiException Si la contraseña no es válida.
     */
    private void ValidationPassword(String password){
        if (!Pattern.matches(PASSWORD_PATTERN, password)) {
            throw new ApiException(new ApiResponse(
                    "La Contraseña '" + password + "' no es válida. La contraseña debe tener al menos 8 caracteres, una letra mayúscula, una letra minúscula, un número y un carácter especial.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
    }

    /**
     * Valida que el rol proporcionado sea un rol válido.
     *
     * @param role El rol a validar.
     * @throws ApiException Si el rol no es válido.
     */
    private void ValidationRole(String role){
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
                    "El Rol '" + role + "' no válido.",
                    HttpStatus.BAD_REQUEST.value()));
        }
    }

    /**
     * Valida que el correo electrónico proporcionado no esté en uso.
     *
     * @param email El correo electrónico a verificar.
     * @throws ApiException Si el correo ya está en uso.
     */
    private void isEmailExist(String email){
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
    public List<User> getAllUsers() {
        List<User> users = repositoryUser.findAll();
        if (users == null || users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontro ningun usuario en la db",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return users;
    }

    /**
     * Crea un nuevo usuario, validando los datos antes de guardarlos.
     *
     * @param user El usuario a crear.
     * @return El usuario creado.
     * @throws ApiException Si ocurre algún error durante la creación.
     */
    public User CreateUser(User user) {
        ValidationName(user.getName());
        ValidationLastname(user.getLastname());
        ValidationEmail(user.getEmail());
        ValidationPassword(user.getPassword());
        ValidationRole(user.getRole().toString());
        isEmailExist(user.getEmail());
        user.setCreateAt(new Date());
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);
        try {
            return repositoryUser.save(user);
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
    public User getUserById(String id) {
        ValidationId(id);

        return repositoryUser.findById(new ObjectId(id))
                .orElseThrow(() -> new ApiException(
                        new ApiResponse("El usuario con id '"+ id +"' no fue encontrado.",
                                HttpStatus.NOT_FOUND.value())
                ));
    }

    /**
     * Obtiene un usuario por su correo electrónico.
     *
     * @param email El correo electrónico del usuario a buscar.
     * @return El usuario encontrado.
     * @throws ApiException Si no se encuentra un usuario con el correo electrónico proporcionado.
     */
    public User getUserByEmail(String email) {
        ValidationEmail(email);

        List<User> users = repositoryUser.findByEmail(email);
        if (users == null || users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontro ningun usuario con el correo electrónico: '" + email + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }

        return users.get(0);
    }

    /**
     * Obtiene una lista de usuarios filtrados por su nombre completo.
     *
     * @param name El nombre del usuario.
     * @param lastname El apellido del usuario.
     * @return Lista de usuarios que coinciden con el nombre y apellido.
     * @throws ApiException Si no se encuentra un usuario con el nombre y apellido proporcionados.
     */
    public List<User> getUsersByFullName(String name, String lastname) {
        ValidationName(name);
        ValidationLastname(lastname);

        List<User> users = repositoryUser.findByFullName(name, lastname);
        if (users == null || users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontro ningun usuario con el nombre '"+ name +"' y apellido '" + lastname + "'.",
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
    public List<User> getUsersByName(String name) {
        ValidationName(name);

        List<User> users = repositoryUser.findByName(name);
        if (users == null || users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontro ningun usuario con el nombre '"+ name +"'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return users;
    }

    /**
     * Obtiene una lista de usuarios filtrados por su apellido.
     *
     * @param lastname El apellido del usuario.
     * @return Lista de usuarios que coinciden con el apellido.
     * @throws ApiException Si no se encuentra un usuario con el apellido proporcionado.
     */
    public List<User> getUsersByLastname(String lastname) {
        ValidationLastname(lastname);

        List<User> users = repositoryUser.findByLastname(lastname);
        if (users == null || users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontro ningun usuario con el apellido '" + lastname + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return users;
    }

    /**
     * Obtiene una lista de usuarios filtrados por su rol.
     * Este método permite recuperar todos los usuarios que tienen asignado un rol específico.
     * Si no se encuentran usuarios con el rol proporcionado, se lanza una excepción.
     *
     * @param role El rol que se utilizará para filtrar los usuarios. Este valor debe ser una cadena que corresponda con uno de los roles definidos en la enumeración `Role`.
     * @return Una lista de usuarios que tienen el rol especificado.
     * @throws ApiException Si no se encuentran usuarios con el rol proporcionado.
     */
    public List<User> getUsersByRole(String role) {
        ValidationRole(role);

        List<User> users = repositoryUser.findByRole(role);

        if (users.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontraron usuarios con el rol '" + role + "'.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }

        return users;
    }

    /**
     * Actualiza la información de un usuario existente.
     *
     * @param existingUser El User del usuario a actualizar.
     * @param updatedUser Los nuevos datos del usuario.
     * @return El usuario actualizado.
     * @throws ApiException Si ocurre algún error durante la actualización.
     */
    public User updateUser(User existingUser, User updatedUser) {
        if (updatedUser.getName() != null) {
            System.out.println("nuevo nombre");
            String name = updatedUser.getName();
            System.out.println("nombre update"+name);
            ValidationName(name);
            existingUser.setName(name);
            System.out.println("nombre setado"+existingUser.getName());
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
            String role = updatedUser.getRole().toString();
            ValidationRole(role);
            existingUser.setRole(updatedUser.getRole());
        }

        if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
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
     * @param existingUser El usuario existente que devuelve getUserById, con la finalidad de validar si existe, para poder eliminarlo.
     * @throws ApiException Si no se encuentra el usuario con el ID proporcionado.
     */
    public ResponseEntity<ApiResponse> deleteUser(User existingUser) {
        try {
            repositoryUser.deleteUserById(existingUser.getId());
            return ResponseEntity.status(HttpStatus.OK.value()).body(
                    new ApiResponse("El usuario con ID '" + existingUser.getId() + "' fue eliminado.",
                            HttpStatus.OK.value())
            );
        } catch (Exception e) {
            log.error("e: ", e);
            throw new ApiException(
                    new ApiResponse("No se pudo eliminar el usuario con ID '" + existingUser.getId() + "'.",
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    /**
     * Carga un usuario basado en su nombre de usuario (en este caso, el correo electrónico).
     * Este metodo es parte de la implementación de `UserDetailsService` de Spring Security.
     * Busca un usuario en la base de datos utilizando el correo electrónico como nombre de usuario.
     * Si no se encuentra un usuario con el correo electrónico proporcionado, se lanza una excepción `UsernameNotFoundException`.
     *
     * @param username El nombre de usuario (correo electrónico) con el que se desea autenticar al usuario.
     * @return Un objeto `UserDetails` que contiene la información del usuario autenticado, como su correo electrónico, contraseña y roles.
     * @throws UsernameNotFoundException Si no se encuentra un usuario con el correo electrónico proporcionado.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user  = repositoryUser.findByEmail(username).get(0);
        if (user != null) {
            return org.springframework.security.core.userdetails.User.builder()
                    .username(user.getEmail())
                    .password(user.getPassword())
                    .build();
        } else {
            throw new UsernameNotFoundException(username);
        }
    }
}
