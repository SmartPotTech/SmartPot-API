package smartpot.com.api.Users.Controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smartpot.com.api.Exception.ApiResponse;
import smartpot.com.api.Users.Model.DAO.Service.SUser;
import smartpot.com.api.Users.Model.DTO.UserDTO;
import smartpot.com.api.Users.Model.Entity.User;

import java.util.List;

@RestController
@RequestMapping("/Users")
@Tag(name = "Usuarios", description = "Operaciones relacionadas con usuarios")
public class UserController {

    private final SUser serviceUser;

    @Autowired
    public UserController(SUser serviceUser) {
        this.serviceUser = serviceUser;
    }

    /**
     * Crea un nuevo usuario.
     *
     * @param newUser El objeto Usuario que contiene los datos del usuario que se guardarán.
     * @return El objeto Usuario creado.
     */
    @PostMapping("/Create")
    @Operation(summary = "Crear un nuevo usuario", description = "Crea un nuevo usuario con la información proporcionada.")
    @ResponseStatus(HttpStatus.CREATED)
    public User createUser(@Parameter(description = "Datos del nuevo usuario", required = true) @RequestBody UserDTO newUser) {
        return serviceUser.CreateUser(newUser);
    }

    /**
     * Recupera todos los usuarios registrados.
     *
     * @return Una lista de todos los usuarios registrados.
     */
    @GetMapping("/All")
    @Operation(summary = "Obtener todos los usuarios", description = "Recupera todos los usuarios registrados en el sistema.")
    public List<User> getAllUsers() {
        return serviceUser.getAllUsers();
    }

    /**
     * Encuentra un usuario por su ID.
     *
     * @param id El ID del usuario a recuperar.
     * @return El objeto Usuario correspondiente al ID proporcionado.
     */
    @GetMapping("/id/{id}")
    @Operation(summary = "Buscar usuario por ID", description = "Recupera un usuario utilizando su ID.")
    public User getUserById(@PathVariable @Parameter(description = "ID del usuario") String id) {
        return serviceUser.getUserById(id);
    }

    /**
     * Encuentra usuarios por su dirección de correo electrónico.
     *
     * @param email La dirección de correo electrónico por la que filtrar los usuarios.
     * @return Un usuario que coincide con el correo electrónico proporcionado.
     */
    @GetMapping("/email/{email}")
    @Operation(summary = "Buscar usuario por correo electrónico", description = "Recupera un usuario utilizando su correo electrónico.")
    public User getUsersByEmail(@PathVariable @Parameter(description = "Correo electrónico del usuario") String email) {
        return serviceUser.getUserByEmail(email);
    }

    /**
     * Encuentra usuarios por su nombre.
     *
     * @param name El nombre por el que filtrar los usuarios.
     * @return Una lista de usuarios que coinciden con el nombre proporcionado.
     */
    @GetMapping("/name/{name}")
    @Operation(summary = "Buscar usuarios por nombre", description = "Recupera usuarios cuyo nombre coincide con el proporcionado.")
    public List<User> getUsersByName(@PathVariable @Parameter(description = "Nombre del usuario") String name) {
        return serviceUser.getUsersByName(name);
    }

    /**
     * Encuentra usuarios por su apellido.
     *
     * @param lastname El apellido por el que filtrar los usuarios.
     * @return Una lista de usuarios que coinciden con el apellido proporcionado.
     */
    @GetMapping("/lastname/{lastname}")
    @Operation(summary = "Buscar usuarios por apellido", description = "Recupera usuarios cuyo apellido coincide con el proporcionado.")
    public List<User> getUsersByLastname(@PathVariable @Parameter(description = "Apellido del usuario") String lastname) {
        return serviceUser.getUsersByLastname(lastname);
    }

    /**
     * Encuentra usuarios por su nombre y apellido.
     *
     * @param name     El nombre por el que filtrar los usuarios.
     * @param lastname El apellido por el que filtrar los usuarios.
     * @return Una lista de usuarios que coinciden con el nombre y apellido proporcionado.
     */
    @GetMapping("/fullname/{name}/{lastname}")
    @Operation(summary = "Buscar usuarios por nombre y apellido", description = "Recupera usuarios cuyo nombre y apellido coinciden con los proporcionados.")
    public List<User> getUsersByFullname(@PathVariable @Parameter(description = "Nombre del usuario") String name,
                                         @PathVariable @Parameter(description = "Apellido del usuario") String lastname) {
        return serviceUser.getUsersByFullName(name, lastname);
    }

    /**
     * Encuentra usuarios por su rol.
     *
     * @param role El rol por el que filtrar los usuarios.
     * @return Una lista de usuarios que coinciden con el rol especificado.
     */
    @GetMapping("/role/{role}")
    @Operation(summary = "Buscar usuarios por rol", description = "Recupera usuarios cuyo rol coincide con el proporcionado.")
    public List<User> getUsersByRole(@PathVariable @Parameter(description = "Rol del usuario") String role) {
        return serviceUser.getUsersByRole(role);
    }

    /**
     * Actualiza un usuario existente.
     *
     * @param id          El ID del usuario a actualizar.
     * @param updatedUser El objeto Usuario que contiene los nuevos datos.
     * @return El objeto Usuario actualizado.
     */
    @PutMapping("/Update/{id}")
    @Operation(summary = "Actualizar un usuario", description = "Actualiza los datos de un usuario existente utilizando su ID.")
    public User updateUser(@PathVariable @Parameter(description = "ID del usuario a actualizar") String id,
                           @RequestBody @Parameter(description = "Datos actualizados del usuario") UserDTO updatedUser) {
        return serviceUser.updateUser(serviceUser.getUserById(id), updatedUser);
    }

    /**
     * Elimina un usuario existente por su ID.
     *
     * @param id El ID del usuario a eliminar.
     */
    @DeleteMapping("/Delete/{id}")
    @Operation(summary = "Eliminar un usuario", description = "Elimina un usuario existente utilizando su ID.")
    public ResponseEntity<ApiResponse> deleteUser(@PathVariable @Parameter(description = "ID del usuario a eliminar") String id) {
        return serviceUser.deleteUser(serviceUser.getUserById(id));
    }
}
