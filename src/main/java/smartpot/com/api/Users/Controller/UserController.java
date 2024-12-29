package smartpot.com.api.Users.Controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smartpot.com.api.Responses.DeleteResponse;
import smartpot.com.api.Responses.ErrorResponse;
import smartpot.com.api.Users.Model.DAO.Service.SUserI;
import smartpot.com.api.Users.Model.DTO.UserDTO;

/**
 * Controlador REST para las operaciones relacionadas con los usuarios.
 * <p>Este controlador proporciona una serie de métodos para gestionar usuarios en el sistema.</p>
 * @see SUserI
 */
@RestController
@RequestMapping("/Users")
@Tag(name = "Usuarios", description = "Operaciones relacionadas con usuarios")
public class UserController {

    /**
     * TODO: Refactorizar métodos para lanzar excepciones personalizadas en lugar de manejar errores con try-catch.
     * TODO: Considerar lanzar una excepción `ConflictException` si el correo electrónico ya está registrado en el sistema por ejemplo.
     * ! Asegurarse de que el flujo de errores sea consistente y se maneje correctamente en el controlador global.
     * ? ¿Qué tipo de error se debe lanzar si el correo electrónico no está válido o es incorrecto (formato de email inválido)?
     */

    private final SUserI serviceUser;

    /**
     * Constructor del controlador {@link UserController}.
     * <p>Se utiliza la inyección de dependencias para asignar el servicio {@link SUserI} que gestionará las operaciones
     * relacionadas con los usuarios.</p>
     *
     * @param serviceUser El servicio que contiene la lógica de negocio para manejar usuarios.
     * @throws NullPointerException Si el servicio proporcionado es {@code null}.
     * @see SUserI
     */
    @Autowired
    public UserController(SUserI serviceUser) {
        this.serviceUser = serviceUser;
    }

    /**
     * Crea un nuevo usuario en el sistema con la información proporcionada.
     * <p>Recupera los datos del usuario desde el objeto {@link UserDTO} proporcionado en el cuerpo de la solicitud.</p>
     * <p>El correo electrónico debe ser único, y el sistema validará que los campos obligatorios como el nombre y el apellido estén completos.</p>
     * <p>Si se crea con éxito, se devolverá el usuario generado con el código HTTP 201.</p>
     * <p>En caso de error (por ejemplo, si el correo electrónico ya está registrado), se devolverá un mensaje de error con el código HTTP 404.</p>
     *
     * @param userDTO El objeto {@link UserDTO} que contiene la información del nuevo usuario.
     *                Este objeto debe contener todos los campos obligatorios, como nombre, apellido y correo electrónico.
     * @return Un objeto {@link ResponseEntity} con el usuario creado (código HTTP 201) o un mensaje de error (código HTTP 404).
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>201 Created</b>: El usuario fue creado correctamente y se retorna un objeto {@link UserDTO} con los datos del usuario.<br></li>
     *   <li><b>404 Not Found</b>: Error al generar el usuario, por ejemplo, si el correo electrónico ya está registrado o faltan datos obligatorios.<br></li>
     * </ul>
     */
    @PostMapping("/Create")
    @Operation(summary = "Crear un nuevo usuario",
            description = "Crea un nuevo usuario con la información proporcionada. "
                    + "El correo electrónico debe ser único y los campos obligatorios deben estar completos.",
            responses = {
                    @ApiResponse(description = "Usuario Creado",
                            responseCode = "201",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = UserDTO.class))),
                    @ApiResponse(responseCode = "404",
                            description = "No se pudo crear el usuario.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> createUser(
            @Parameter(description = "Datos del nuevo usuario que se va a crear. Debe incluir nombre, apellido y un correo electrónico único.",
                    required = true) @RequestBody UserDTO userDTO) {
        try {
            return new ResponseEntity<>(serviceUser.CreateUser(userDTO), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al crear el usuario [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Recupera todos los usuarios registrados en el sistema.
     * <p>Este método devuelve una lista con todos los usuarios que están registrados en el sistema.</p>
     * <p>Si no se encuentran usuarios, se devolverá una lista vacía con el código HTTP 200.</p>
     * <p>En caso de error (por ejemplo, problemas con la conexión a la base de datos o un fallo en el servicio),
     * se devolverá un mensaje de error con el código HTTP 404.</p>
     *
     * @return Un objeto {@link ResponseEntity} que contiene una lista de todos los usuarios registrados (código HTTP 200).
     * En caso de error, se devolverá un mensaje de error con el código HTTP 404.
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Se retorna una lista de objetos {@link UserDTO} con la información de todos los usuarios registrados.<br></li>
     *   <li><b>404 Not Found</b>: No se encontraron usuarios registrados o hubo un error al recuperar los datos.<br></li>
     * </ul>
     */
    @GetMapping("/All")
    @Operation(summary = "Obtener todos los usuarios",
            description = "Recupera todos los usuarios registrados en el sistema. "
                    + "En caso de no haber usuarios, se devolverá una lista vacía.",
            responses = {
                    @ApiResponse(description = "Usuarios encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = UserDTO.class)))),
                    @ApiResponse(responseCode = "404",
                            description = "No se encontraron usuarios registrados.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getAllUsers() {
        try {
            return new ResponseEntity<>(serviceUser.getAllUsers(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar los usuarios [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Busca un usuario utilizando su ID único.
     * <p>Este método recupera un usuario de la base de datos utilizando su identificador único. Si el usuario con el ID proporcionado existe, se devolverá un objeto {@link UserDTO} con la información del usuario.</p>
     * <p>Si no se encuentra un usuario con el ID proporcionado, se devolverá un mensaje de error con el código HTTP 404.</p>
     *
     * @param id El identificador único del usuario que se desea recuperar. Este parámetro debe ser el ID único del usuario.
     *           El ID es obligatorio para la búsqueda del usuario.
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>El usuario correspondiente al ID si se encuentra (código HTTP 200).</li>
     *           <li>Un mensaje de error si no se encuentra el usuario (código HTTP 404).</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Si el usuario es encontrado, se retorna un objeto {@link UserDTO} con los detalles del usuario.<br></li>
     *   <li><b>404 Not Found</b>: Si no se encuentra el usuario con el ID proporcionado, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @GetMapping("/id/{id}")
    @Operation(summary = "Buscar usuario por ID",
            description = "Recupera un usuario utilizando su ID único. "
                    + "Si el usuario no existe, se devolverá un error con el código HTTP 404.",
            responses = {
                    @ApiResponse(description = "Usuario encontrado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))),
                    @ApiResponse(responseCode = "404",
                            description = "Usuario no encontrado con el ID especificado.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getUserById(@PathVariable @Parameter(description = "ID único del usuario a buscar.", required = true) String id) {
        try {
            return new ResponseEntity<>(serviceUser.getUserById(id), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar el usuario con id '" + id + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Busca un usuario por su dirección de correo electrónico único.
     * <p>Este método recupera un usuario del sistema utilizando su correo electrónico único. Si el usuario con el correo electrónico proporcionado existe, se devolverá un objeto {@link UserDTO} con la información del usuario.</p>
     * <p>Si no se encuentra un usuario con el correo electrónico proporcionado, se devolverá un mensaje de error con el código HTTP 404.</p>
     *
     * @param email La dirección de correo electrónico del usuario. Este parámetro debe ser único en el sistema y se utilizará para buscar al usuario.
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>El usuario correspondiente al correo electrónico proporcionado si se encuentra (código HTTP 200).</li>
     *           <li>Un mensaje de error si no se encuentra el usuario (código HTTP 404).</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Si el usuario es encontrado, se retorna un objeto {@link UserDTO} con los detalles del usuario.<br></li>
     *   <li><b>404 Not Found</b>: Si no se encuentra el usuario con el correo electrónico proporcionado, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @GetMapping("/email/{email}")
    @Operation(summary = "Buscar usuario por correo electrónico",
            description = "Recupera un usuario utilizando su correo electrónico único. "
                    + "Si no se encuentra el usuario, se devolverá un error.",
            responses = {
                    @ApiResponse(description = "Usuario encontrado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))),
                    @ApiResponse(responseCode = "404",
                            description = "Usuario no encontrado con el correo electrónico proporcionado.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getUsersByEmail(@Parameter(description = "Correo electrónico del usuario", required = true) @PathVariable String email) {
        try {
            return new ResponseEntity<>(serviceUser.getUserByEmail(email), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar el usuario con email '" + email + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Busca todos los usuarios cuyo nombre coincida con el proporcionado.
     * <p>Este método recupera una lista de usuarios que coinciden con el nombre proporcionado. Puede ser el primer nombre o cualquier parte del nombre del usuario.</p>
     * <p>Si no se encuentran usuarios con el nombre proporcionado, se devolverá una lista vacía con el código HTTP 200.</p>
     *
     * @param name El nombre del usuario a buscar. Este parámetro puede ser el primer nombre o cualquier parte del nombre completo del usuario.
     *             El nombre es obligatorio para realizar la búsqueda.
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>Una lista de objetos {@link UserDTO} con los usuarios que coinciden con el nombre proporcionado (código HTTP 200).</li>
     *           <li>Un mensaje de error si no se encuentran usuarios con el nombre proporcionado (código HTTP 404).</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Si se encuentran usuarios con el nombre proporcionado, se retorna una lista de objetos {@link UserDTO} con la información de los usuarios.<br></li>
     *   <li><b>404 Not Found</b>: Si no se encuentran usuarios con el nombre proporcionado, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @GetMapping("/name/{name}")
    @Operation(summary = "Buscar usuarios por nombre",
            description = "Recupera usuarios cuyo nombre coincide con el proporcionado. "
                    + "Si no se encuentran usuarios, se devolverá una lista vacía.",
            responses = {
                    @ApiResponse(description = "Usuarios encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = UserDTO.class)))),
                    @ApiResponse(responseCode = "404",
                            description = "No se encontraron usuarios con el nombre proporcionado.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getUsersByName(@Parameter(description = "Nombre del usuario a buscar.", required = true) @PathVariable String name) {
        try {
            return new ResponseEntity<>(serviceUser.getUsersByName(name), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar el usuario con nombre '" + name + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Busca todos los usuarios cuyo apellido coincida con el proporcionado.
     * <p>Este método recupera una lista de usuarios que coinciden con el apellido proporcionado. Puede ser el apellido completo o parte de él.</p>
     * <p>Si no se encuentran usuarios con el apellido proporcionado, se devolverá una lista vacía con el código HTTP 200.</p>
     *
     * @param lastname El apellido del usuario a buscar. Este parámetro puede ser el apellido completo o parte de él.
     *                 El apellido es obligatorio para realizar la búsqueda.
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>Una lista de objetos {@link UserDTO} con los usuarios cuyo apellido coincide con el proporcionado (código HTTP 200).</li>
     *           <li>Un mensaje de error si no se encuentran usuarios con el apellido proporcionado (código HTTP 404).</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Si se encuentran usuarios con el apellido proporcionado, se retorna una lista de objetos {@link UserDTO} con la información de los usuarios.<br></li>
     *   <li><b>404 Not Found</b>: Si no se encuentran usuarios con el apellido proporcionado, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @GetMapping("/lastname/{lastname}")
    @Operation(summary = "Buscar usuarios por apellido",
            description = "Recupera usuarios cuyo apellido coincide con el proporcionado. "
                    + "Si no se encuentran usuarios, se devolverá una lista vacía.",
            responses = {
                    @ApiResponse(description = "Usuarios encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = UserDTO.class)))),
                    @ApiResponse(responseCode = "404",
                            description = "No se encontraron usuarios con el apellido proporcionado.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getUsersByLastname(@Parameter(description = "Apellido del usuario a buscar", required = true) @PathVariable String lastname) {
        try {
            return new ResponseEntity<>(serviceUser.getUsersByLastname(lastname), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar el usuario con apellido '" + lastname + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Busca todos los usuarios que tengan el rol especificado.
     * <p>Este método recupera una lista de usuarios que tienen el rol proporcionado. El rol debe coincidir exactamente con el rol de los usuarios.</p>
     * <p>Si no se encuentran usuarios con el rol proporcionado, se devolverá una lista vacía con el código HTTP 200.</p>
     *
     * @param role El rol que se desea buscar. Este parámetro debe coincidir exactamente con el rol de los usuarios en el sistema.
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>Una lista de objetos {@link UserDTO} con los usuarios que tienen el rol especificado (código HTTP 200).</li>
     *           <li>Un mensaje de error si no se encuentran usuarios con el rol especificado (código HTTP 404).</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Si se encuentran usuarios con el rol proporcionado, se retorna una lista de objetos {@link UserDTO} con la información de los usuarios.<br></li>
     *   <li><b>404 Not Found</b>: Si no se encuentran usuarios con el rol proporcionado, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @GetMapping("/role/{role}")
    @Operation(summary = "Buscar usuarios por rol",
            description = "Recupera usuarios cuyo rol coincide con el proporcionado. "
                    + "Si no se encuentran usuarios, se devolverá una lista vacía.",
            responses = {
                    @ApiResponse(description = "Usuarios encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = UserDTO.class)))),
                    @ApiResponse(responseCode = "404",
                            description = "No se encontraron usuarios con el rol proporcionado.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getUsersByRole(@Parameter(description = "Rol del usuario a buscar", required = true) @PathVariable String role) {
        try {
            return new ResponseEntity<>(serviceUser.getUsersByRole(role), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar el usuario con rol '" + role + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Actualiza la información de un usuario existente.
     * <p>Este método recibe un ID de usuario y un objeto {@link UserDTO} con los datos actualizados.
     * Si el usuario con el ID proporcionado existe, sus datos se actualizan con la información proporcionada.</p>
     * <p>Si el usuario no existe o si ocurre algún error durante el proceso de actualización, se devuelve un mensaje de error con el código HTTP 404.</p>
     *
     * @param id          El identificador único del usuario que se desea actualizar. Este parámetro debe ser el ID del usuario a modificar.
     * @param updatedUser El objeto {@link UserDTO} que contiene la nueva información del usuario a actualizar.
     * @return Un objeto {@link ResponseEntity} con:
     *         <ul>
     *           <li>El usuario actualizado con el código HTTP 200 si la actualización es exitosa.</li>
     *           <li>Un mensaje de error con el código HTTP 404 si no se encuentra el usuario o si ocurre un error.</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: El usuario fue actualizado correctamente y se retorna un objeto {@link UserDTO} con los datos actualizados del usuario.<br></li>
     *   <li><b>404 Not Found</b>: No se pudo actualizar el usuario. El usuario puede no existir o los datos pueden ser incorrectos.<br></li>
     * </ul>
     */
    @PutMapping("/Update/{id}")
    @Operation(summary = "Actualizar un usuario",
            description = "Actualiza los datos de un usuario existente utilizando su ID. "
                    + "Si el usuario no existe o hay un error, se devolverá un error con código HTTP 404.",
            responses = {
                    @ApiResponse(description = "Usuario actualizado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))),
                    @ApiResponse(responseCode = "404",
                            description = "No se pudo actualizar el usuario. El usuario puede no existir o los datos pueden ser incorrectos.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> updateUser(
            @Parameter(description = "ID único del usuario que se desea actualizar.", required = true) @PathVariable String id,
            @Parameter(description = "Datos actualizados del usuario.") @RequestBody UserDTO updatedUser) {
        try {
            return new ResponseEntity<>(serviceUser.UpdateUser(id, updatedUser), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al actualizar el usuario con id '" + id + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Elimina un usuario existente por su ID.
     * <p>Este método permite eliminar a un usuario de la base de datos utilizando su ID único.
     * Si el usuario no se encuentra o si ocurre un error durante la eliminación, se devolverá un mensaje de error con el código HTTP 404.</p>
     *
     * @param id El identificador único del usuario que se desea eliminar. Este parámetro debe ser el ID del usuario a eliminar.
     * @return Un objeto {@link ResponseEntity} con:
     *         <ul>
     *           <li>Un mensaje indicando el éxito de la eliminación con el código HTTP 200.</li>
     *           <li>Un mensaje de error si no se pudo eliminar el usuario, con el código HTTP 404.</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: El usuario fue eliminado exitosamente.<br></li>
     *   <li><b>404 Not Found</b>: No se encontró el usuario con el ID proporcionado o hubo un error al eliminarlo.<br></li>
     * </ul>
     */
    @DeleteMapping("/Delete/{id}")
    @Operation(summary = "Eliminar un usuario",
            description = "Elimina un usuario existente utilizando su ID. "
                    + "Si el usuario no existe o hay un error, se devolverá un error con código HTTP 404.",
            responses = {
                    @ApiResponse(description = "Usuario eliminado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeleteResponse.class))),
                    @ApiResponse(responseCode = "404",
                            description = "No se pudo eliminar el usuario.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> deleteUser(@Parameter(description = "ID único del usuario que se desea eliminar.", required = true) @PathVariable String id) {
        try {
            return new ResponseEntity<>(new DeleteResponse("Se ha eliminado un recurso [" + serviceUser.DeleteUser(id) + "]"), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al eliminar el usuario con id '" + id + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }
}
