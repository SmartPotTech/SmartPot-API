package smartpot.com.api.Users.Service;

import jakarta.validation.ValidationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import smartpot.com.api.Users.Model.DTO.UserDTO;

import java.util.List;

/**
 * Interfaz que define los métodos para la gestión de usuarios.
 * Esta interfaz extiende {@link UserDetailsService}, lo que permite integrar
 * las funcionalidades de autenticación de usuarios en un sistema basado en Spring Security.
 * Los métodos permiten crear, obtener, actualizar y eliminar usuarios,
 * así como realizar búsquedas por diferentes criterios como nombre, apellido,
 * correo electrónico y rol.
 */
public interface SUserI extends UserDetailsService {
    /**
     * Crea un nuevo usuario en el sistema.
     *
     * @param userDTO el objeto que contiene los datos del nuevo usuario a crear.
     * @return el objeto {@link UserDTO} del usuario creado.
     * @throws ValidationException,IllegalStateException si el usuario ya existe o si ocurre un error durante la creación.
     */
    UserDTO CreateUser(UserDTO userDTO) throws ValidationException, IllegalStateException;

    /**
     * Obtiene todos los usuarios registrados en el sistema.
     *
     * @return una lista de objetos {@link UserDTO} que representan a todos los usuarios.
     * @throws Exception si ocurre un error al obtener los usuarios.
     */
    List<UserDTO> getAllUsers() throws Exception;

    /**
     * Obtiene un usuario por su ID.
     *
     * @param id el identificador único del usuario a obtener.
     * @return el objeto {@link UserDTO} correspondiente al usuario con el ID proporcionado.
     * @throws Exception si no se encuentra el usuario o si ocurre un error.
     */
    UserDTO getUserById(String id) throws Exception;

    /**
     * Obtiene un usuario por su correo electrónico.
     *
     * @param email el correo electrónico del usuario a obtener.
     * @return el objeto {@link UserDTO} correspondiente al usuario con el correo electrónico proporcionado.
     * @throws Exception si no se encuentra el usuario o si ocurre un error.
     */
    UserDTO getUserByEmail(String email) throws Exception;

    /**
     * Obtiene una lista de usuarios que coinciden con un nombre.
     *
     * @param name el nombre del usuario a buscar.
     * @return una lista de objetos {@link UserDTO} que coinciden con el nombre proporcionado.
     * @throws Exception si no se encuentran usuarios o si ocurre un error durante la búsqueda.
     */
    List<UserDTO> getUsersByName(String name) throws Exception;

    /**
     * Obtiene una lista de usuarios que coinciden con un apellido.
     *
     * @param lastname el apellido del usuario a buscar.
     * @return una lista de objetos {@link UserDTO} que coinciden con el apellido proporcionado.
     * @throws Exception si no se encuentran usuarios o si ocurre un error durante la búsqueda.
     */
    List<UserDTO> getUsersByLastname(String lastname) throws Exception;

    /**
     * Obtiene una lista de usuarios que coinciden con un rol específico.
     *
     * @param role el rol de usuario a buscar.
     * @return una lista de objetos {@link UserDTO} que coinciden con el rol proporcionado.
     * @throws Exception si no se encuentran usuarios o si ocurre un error durante la búsqueda.
     */
    List<UserDTO> getUsersByRole(String role) throws Exception;

    /**
     * Obtiene todos los roles de usuario registrados en el sistema.
     *
     * @return una lista de objetos {@link String} que representan a todos los roles de usuario.
     * @throws Exception si ocurre un error al obtener los roles de usuario.
     */
    List<String> getAllRoles() throws Exception;

    /**
     * Actualiza los datos de un usuario.
     *
     * @param id          el identificador único del usuario a actualizar.
     * @param updatedUser un objeto {@link UserDTO} con los datos actualizados del usuario.
     * @return el objeto {@link UserDTO} con los datos del usuario actualizado.
     * @throws Exception si no se encuentra el usuario o si ocurre un error durante la actualización.
     */
    UserDTO UpdateUser(String id, UserDTO updatedUser) throws Exception;

    /**
     * Elimina un usuario del sistema.
     *
     * @param id el identificador único del usuario a eliminar.
     * @return un mensaje indicando que el usuario fue eliminado.
     * @throws Exception si no se encuentra el usuario o si ocurre un error durante la eliminación.
     */
    String DeleteUser(String id) throws Exception;

    /**
     * Actualiza únicamente la contraseña del usuario, sin verificar los demás datos.
     *
     * @param user     El objeto {@link UserDTO} del usuario que va a actualizar.
     * @param password Contraseña en texto claro.
     * @return el objeto {@link UserDTO} con los datos del usuario actualizado.
     * @throws Exception si no se encuentra el usuario o si ocurre un error durante la actualización.
     */
    UserDTO UpdateUserPassword(UserDTO user, String password) throws Exception;
}
