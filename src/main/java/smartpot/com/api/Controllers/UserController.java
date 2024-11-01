package smartpot.com.api.Controllers;
import org.springframework.http.HttpStatus;
import smartpot.com.api.Models.DAO.Service.SUser;
import smartpot.com.api.Models.Entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/Users")
public class UserController {

    @Autowired
    private SUser serviceUser;

    /**
     * Crea un nuevo usuario.
     *
     * @param newUser El objeto Usuario que contiene los datos del usuario que se guardarán.
     * @return El objeto Usuario creado.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User createUser(@RequestBody User newUser) {
        return serviceUser.saveUser(newUser);
    }

    /**
     * Recupera todos los usuarios registrados.
     *
     * @return Una lista de todos los usuarios.
     */
    @GetMapping({"/",""})
    public List<User> getAllUsers() {
        return serviceUser.getAllUsers();
    }

    /**
     * Encuentra un usuario por su ID.
     *
     * @param id El ID del usuario a recuperar.
     * @return El objeto Usuario correspondiente al ID proporcionado.
     */
    @GetMapping("/{id}")
    public User getUserById(@PathVariable String id) {
        return serviceUser.getUserById(id);
    }

    /**
     * Encuentra usuarios por su dirección de correo electrónico.
     *
     * @param email La dirección de correo electrónico por la que filtrar los usuarios.
     * @return Una lista de usuarios que coinciden con el correo electrónico proporcionado.
     */
    @GetMapping("/email/{email}")
    public List<User> getUsersByEmail(@PathVariable String email) {
        return serviceUser.getUsersByEmail(email);
    }

    /**
     * Encuentra usuarios por su nombre.
     *
     * @param name El nombre por el que filtrar los usuarios.
     * @return Una lista de usuarios que coinciden con el nombre proporcionado.
     */
    @GetMapping("/name/{name}")
    public List<User> getUsersByName(@PathVariable String name) {
        return serviceUser.getUsersByName(name);
    }

    /**
     * Encuentra usuarios por su rol.
     *
     * @param role El rol por el que filtrar los usuarios.
     * @return Una lista de usuarios que coinciden con el rol especificado.
     */
    @GetMapping("/role/{role}")
    public List<User> getUsersByRole(@PathVariable String role) {
        return serviceUser.getUsersByRole(role);
    }

    /**
     * Actualiza un usuario existente.
     *
     * @param id El ID del usuario a actualizar.
     * @param updatedUser El objeto Usuario que contiene los nuevos datos.
     * @return El objeto Usuario actualizado.
     */
    @PutMapping("/{id}")
    public User updateUser(@PathVariable String id, @RequestBody User updatedUser) {
        return serviceUser.updateUser(id, updatedUser);
    }

    /**
     * Elimina un usuario existente por su ID.
     *
     * @param id El ID del usuario a eliminar.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable String id) {
        serviceUser.deleteUser(id);
    }
}

