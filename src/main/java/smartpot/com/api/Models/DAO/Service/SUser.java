package smartpot.com.api.Models.DAO.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import smartpot.com.api.Models.DAO.Repository.RUser;
import smartpot.com.api.Models.Entity.User;
import smartpot.com.api.utilitys.Exception;

import java.util.List;
import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Service
public class SUser {
    @Autowired
    private RUser repositoryUser;

    /**
     * Guarda o crea un nuevo user.
     *
     * @param user El objeto User que contiene los datos del user a guardar.
     * @return El user que ha sido guardado, incluyendo cualquier información generada por el sistema (como un ID).
     */
    public User saveUser(User user) {
        return repositoryUser.save(user);
    }

    /**
     * Obtiene todos los usuarios registrados.
     *
     * @return Una lista de todos los usuarios.
     */
    public List<User> getAllUsers() {
        return repositoryUser.findAll();
    }

    /**
     * Busca un usuario por su identificador.
     * Este método maneja el ID como un ObjectId de MongoDB. Si el ID es válido como ObjectId,
     * se convierte a String para la búsqueda. Si no, intenta buscar directamente con el ID como String.
     *
     * @param id El identificador del usuario a buscar
     * @return El usuario correspondiente al ID proporcionado.
     * @throws Exception Si no se encuentra un usuario con el ID especificado.
     */
    public Optional<User> getUserById(ObjectId id) {
        return repositoryUser.findById(id);
    }

    /**
     * Busca usuarios por su dirección de correo electrónico.
     *
     * @param email La dirección de correo electrónico por la que filtrar.
     * @return Una lista de usuarios que coinciden con el correo electrónico proporcionado.
     * @throws ResponseStatusException Si no se encuentran usuarios con el correo electrónico especificado.
     */
    public List<User> getUsersByEmail(String email) {
        return repositoryUser.findByEmail(email);
    }

    /**
     * Recupera una lista de usuarios por su nombre.
     *
     * @param name El nombre por el que filtrar los usuarios.
     * @return Una lista de usuarios que coinciden con el nombre proporcionado.
     * @throws ResponseStatusException Si no se encuentran usuarios con el nombre especificado.
     */
    public List<User> getUsersByName(String name) {
        return repositoryUser.findByName(name);
    }

    /**
     * Busca usuarios según su rol.
     *
     * @param role El rol por el cual filtrar los usuarios.
     * @return Una lista de usuarios que coinciden con el rol especificado.
     * @throws ResponseStatusException Si no se encuentran usuarios con el rol dado.
     */
    public List<User> getUsersByRole(String role) {
        List<User> Users = repositoryUser.findByRole(role);
        if (Users.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Usuarios no encontrados con rol: " + role
            );
        }
        return Users;
    }

    /**
     * Actualiza la información de un usuario existente.
     *
     * @param id El identificador del usuario a actualizar.
     * @param updatedUser Un objeto User que contiene los nuevos datos del usuario.
     * @return El usuario actualizado después de guardarlo en el servicio.
     * @throws ResponseStatusException Si no se encuentra un usuario con el ID proporcionado.
     */
    public User updateUser(ObjectId id, User updatedUser) {
        Optional<User> users = getUserById(id);
        User existingUser = users.stream().findFirst().orElse(null);
        if (existingUser != null) {
            existingUser.setName(updatedUser.getName());
            existingUser.setLastname(updatedUser.getLastname());
            existingUser.setEmail(updatedUser.getEmail());
            existingUser.setRole(updatedUser.getRole());
            return repositoryUser.save(existingUser);
        }
        return null;
    }

    /**
     * Elimina un usuario existente por su identificador.
     *
     * @param id El identificador del usuario que se desea eliminar.
     * @throws ResponseStatusException Si no se encuentra un usuario con el ID proporcionado.
     */
    public void deleteUser(ObjectId id) {
        Optional<User> users = getUserById(id);
        User existingUser = users.stream().findFirst().orElse(null);
        if (existingUser != null) {
            repositoryUser.delete(existingUser);
        }

    }
}
