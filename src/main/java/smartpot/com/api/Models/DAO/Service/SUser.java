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
import smartpot.com.api.utilitys.ErrorResponse;

import java.util.List;

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
     * @param id El identificador del usuario a buscar, puede ser un ObjectId o un String.
     * @return El usuario correspondiente al ID proporcionado.
     * @throws ResponseStatusException Si no se encuentra un usuario con el ID especificado.
     */
    public User getUserById(String id) {
        if (!ObjectId.isValid(id)) {
            throw new Exception
                    (new ErrorResponse(
                            "El ID " + id + " no es valido.",
                            HttpStatus.BAD_REQUEST.value()
                    )
                    );
        }
        return repositoryUser.findById(new ObjectId(id))
                .orElseThrow(() -> new Exception(
                        new ErrorResponse(
                                "Usuario con ID " + id + " no fue encontrado.",
                                HttpStatus.NOT_FOUND.value()
                        )
                ));
    }

    /**
     * Busca usuarios por su dirección de correo electrónico.
     *
     * @param email La dirección de correo electrónico por la que filtrar.
     * @return Una lista de usuarios que coinciden con el correo electrónico proporcionado.
     * @throws ResponseStatusException Si no se encuentran usuarios con el correo electrónico especificado.
     */
    public List<User> getUsersByEmail(String email) {
        /*
        List<User> Users = repositoryUser.findByEmail(email);
        if (Users.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Usuario no encontrado con email: " + email
            );
        }
        return Users;
        */
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
        /*
        List<User> users = repositoryUser.findByName(name);
        if (users.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No users found with name: " + name
            );
        }
        return users;
        */
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
    public User updateUser(String id, User updatedUser) {
        User existingUser = getUserById(id);
        existingUser.setName(updatedUser.getName());
        existingUser.setLastname(updatedUser.getLastname());
        existingUser.setEmail(updatedUser.getEmail());
        existingUser.setRole(updatedUser.getRole());
        return repositoryUser.save(existingUser);
    }

    /**
     * Elimina un usuario existente por su identificador.
     *
     * @param id El identificador del usuario que se desea eliminar.
     * @throws ResponseStatusException Si no se encuentra un usuario con el ID proporcionado.
     */
    public void deleteUser(String id) {
        User existingUser = getUserById(id);
        repositoryUser.delete(existingUser);
    }
}
