package smartpot.com.api.Models.DAO;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import smartpot.com.api.Models.Entity.User;

import java.util.Collections;
import java.util.List;

@Service
public class RUserImp  {
    @Autowired
    private RUser userService;

    // Método para guardar/crear un usuario
    public User save(User usuario) {
        return userService.save(usuario);
    }

    // Método para obtener todos los usuarios
    public List<User> findAll() {
        return userService.findAll();
    }


    // se debe manejar como objectId porque asi lo maneja Mongo si lo usamos como string no lo encontrara
    public User findById(String id) {
        if (ObjectId.isValid(id)) {
            return userService.findById(new ObjectId(id).toString()) // convertir ObjectId a String
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Usuario no encontrado con ID: " + id
                    ));
        } else {
            // Si el ID no es un ObjectId, intenta buscar directamente con el String
            return userService.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Usuario no encontrado con ID: " + id
                    ));
        }
    }

    // Método para buscar por email
    public List<User> findByEmail(String email) {
        List<User> usuarios = userService.findByEmail(email);
        if (usuarios.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Usuario no encontrado con email: " + email
            );
        }
        return usuarios;
    }

    // Método para buscar por role
    public List<User> findByRole(String role) {
        List<User> usuarios = userService.findByRole(role);
        if (usuarios.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Usuarios no encontrados con role: " + role
            );
        }
        return usuarios;
    }

    // Método para actualizar usuario
    public User  updateUser(String id, User usuarioActualizado) {
        User usuarioExistente = findById(id);
        usuarioExistente.setName(usuarioActualizado.getName());
        usuarioExistente.setLastname(usuarioActualizado.getLastname());
        usuarioExistente.setEmail(usuarioActualizado.getEmail());
        usuarioExistente.setRole(usuarioActualizado.getRole());
        return userService.save(usuarioExistente);
    }

    // Método para eliminar usuario
    public void delete(String id) {
        User usuarioExistente = findById(id);
        userService.delete(usuarioExistente);
    }




}
