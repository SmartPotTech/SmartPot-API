package smartpot.com.api.Controllers;
import smartpot.com.api.Models.Entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/Usuarios")
public class UserController {

    @Autowired
    private SUser userService; // Cambia aquí a RUserImp

    // Crear un nuevo usuario
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User createUser(@RequestBody User usuario) {
        return userService.save(usuario);
    }

    // Obtener todos los usuarios
    @GetMapping
    public List<User> getAllUsers() {
        return userService.findAll();
    }

    // Buscar usuario por ID
    @GetMapping("/{id}")
    public User getUserById(@PathVariable String id) {
        return userService.findById(id);
    }

    @GetMapping("/email/{email}")
    public List<User> getUsersByEmail(@PathVariable String email) {
        return repositoryUser.findByEmail(email);
    }

    @GetMapping("/name/{name}")
    public List<User> getUsersByName(@PathVariable String name) {
        return repositoryUser.findByName(name);
    }

    @PostMapping
    public User createUser(@RequestBody User newUser) {
        return repositoryUser.save(newUser);

    // Buscar usuarios por email
    @GetMapping("/email/{email}")
    public List<User> getUsersByEmail(@PathVariable String email) {
        return userService.findByEmail(email);
    }

    // Buscar usuarios por rol
    @GetMapping("/role/{role}")
    public List<User> getUsersByRole(@PathVariable String role) {
        return userService.findByRole(role);
    }

    // Actualizar usuario completo (PUT)
    @PutMapping("/{id}")
    public User updateUser(
            @PathVariable String id,
            @RequestBody User usuarioActualizado
    ) {
        return userService.updateUser(id, usuarioActualizado);
    }


    // Eliminar usuario
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable String id) {
        userService.delete(id);
    }
}

