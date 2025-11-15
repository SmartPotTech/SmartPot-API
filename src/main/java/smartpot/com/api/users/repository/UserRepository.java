package smartpot.com.api.users.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import smartpot.com.api.users.model.entity.User;

import java.util.List;

/**
 * Repositorio para la gestión de la entidad {@link User} en la base de datos MongoDB.
 * <p>
 * Esta interfaz extiende {@link MongoRepository}, lo que proporciona métodos CRUD básicos
 * para interactuar con la base de datos MongoDB de manera sencilla.
 * Además, incluye consultas personalizadas para buscar usuarios por distintos campos
 * como nombre, apellido, correo electrónico y rol.
 * <p>
 * Las consultas personalizadas están anotadas con {@link Query}, las cuales utilizan
 * la sintaxis de MongoDB para realizar búsquedas más específicas, incluyendo búsquedas
 * insensibles a mayúsculas y minúsculas (gracias a la opción "$options: 'i'").
 */

@Repository
public interface UserRepository extends MongoRepository<User, ObjectId> {

    /**
     * Busca una lista de usuarios cuyo nombre coincida con el patrón proporcionado,
     * realizando una búsqueda insensible a mayúsculas/minúsculas.
     *
     * @param name El nombre o patrón a buscar en el campo 'name' de los usuarios.
     * @return Una lista de usuarios cuyo nombre coincida con el patrón.
     */
    @Query("{ 'name' : { $regex: ?0, $options: 'i' } }")
    List<User> findByName(String name);

    /**
     * Busca una lista de usuarios cuyo apellido coincida con el patrón proporcionado,
     * realizando una búsqueda insensible a mayúsculas/minúsculas.
     *
     * @param lastname El apellido o patrón a buscar en el campo 'lastname' de los usuarios.
     * @return Una lista de usuarios cuyo apellido coincida con el patrón.
     */
    @Query("{ 'lastname' : { $regex: ?0, $options: 'i' } }")
    List<User> findByLastname(String lastname);

    /**
     * Busca una lista de usuarios cuyo nombre y apellido coincidan con los patrones
     * proporcionados, realizando una búsqueda insensible a mayúsculas/minúsculas.
     *
     * @param name     El nombre o patrón a buscar en el campo 'name' de los usuarios.
     * @param lastname El apellido o patrón a buscar en el campo 'lastname' de los usuarios.
     * @return Una lista de usuarios cuyo nombre y apellido coincidan con los patrones.
     */
    @Query("{ 'name' : { $regex: ?0, $options: 'i' }, 'lastname' : { $regex: ?1, $options: 'i' } }")
    List<User> findByFullName(String name, String lastname);

    /**
     * Busca una lista de usuarios cuyo correo electrónico coincida exactamente
     * con el correo proporcionado.
     *
     * @param email El correo electrónico a buscar en el campo 'email' de los usuarios.
     * @return Una lista de usuarios cuyo correo electrónico coincida exactamente.
     */
    @Query("{ 'email' : ?0 }")
    List<User> findByEmail(String email);

    /**
     * Busca una lista de usuarios cuyo rol coincida exactamente con el rol proporcionado.
     *
     * @param role El rol a buscar en el campo 'role' de los usuarios.
     * @return Una lista de usuarios cuyo rol coincida exactamente.
     */
    @Query("{ 'role' : ?0 }")
    List<User> findByRole(String role);

    /**
     * Verifica si existe un usuario con el correo electrónico proporcionado.
     *
     * <p>Este método devuelve {@code true} si existe un usuario con el correo electrónico especificado,
     * y {@code false} en caso contrario.</p>
     *
     * @param email El correo electrónico a verificar.
     * @return {@code true} si existe un usuario con el correo electrónico proporcionado, {@code false} en caso contrario.
     */
    boolean existsByEmail(String email);
}
