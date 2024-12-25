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
import smartpot.com.api.Exception.ErrorResponse;
import smartpot.com.api.Users.Mapper.MUser;
import smartpot.com.api.Users.Model.DAO.Service.SUserI;
import smartpot.com.api.Users.Model.DTO.UserDTO;

@RestController
@RequestMapping("/Users")
@Tag(name = "Usuarios", description = "Operaciones relacionadas con usuarios")
public class UserController {

    private final SUserI serviceUser;

    @Autowired
    public UserController(SUserI serviceUser) {
        this.serviceUser = serviceUser;
    }

    /**
     * Crea un nuevo usuario en el sistema con la información proporcionada.
     * *
     * Recupera los datos del usuario desde el `UserDTO` proporcionado en el cuerpo de la solicitud.
     * El correo electrónico debe ser único y el sistema validará los campos obligatorios como nombre y apellido.
     * Si se crea con éxito, se devolverá el usuario generado con el código HTTP 201.
     * En caso de error (por ejemplo, correo electrónico duplicado), se devolverá un mensaje de error con el código HTTP 404.
     *
     * @param userDTO El objeto `UserDTO` que contiene la información del nuevo usuario.
     *                Este objeto debe contener todos los campos obligatorios, como nombre, apellido y correo electrónico.
     * @return Un objeto `ResponseEntity` con el usuario creado (código HTTP 201) o un mensaje de error (código HTTP 404).
     * *
     * Respuestas posibles:
     * - **201 Created**: El usuario fue creado correctamente y se retorna un objeto `UserDTO` con los datos del usuario.
     * - **404 Not Found**: Error al generar el usuario, como un correo electrónico duplicado.
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
                            description = "No se pudo crear el usuario, asegúrese de que el correo sea único y los datos sean correctos.",
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
     * *
     * Este método devuelve una lista con todos los usuarios que están registrados en el sistema. Si no se encuentran usuarios,
     * se devolverá una lista vacía con código HTTP 200.
     *
     * @return Un objeto `ResponseEntity` que contiene una lista de todos los usuarios registrados (código HTTP 200).
     * En caso de error, se devolverá un mensaje de error con el código HTTP 404.
     * *
     * Respuestas posibles:
     * - **200 OK**: Se retorna una lista de objetos `UserDTO` con la información de todos los usuarios.
     * - **404 Not Found**: No se encontraron usuarios registrados.
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
     * *
     * Recuperar un usuario de la base de datos proporcionando su identificador único.
     * Si el usuario con el ID proporcionado existe, se devolverá un objeto `UserDTO` con la información del usuario.
     * En caso de que el usuario no sea encontrado, se devolverá un mensaje de error con el código HTTP 404.
     *
     * @param id El identificador único del usuario que se desea recuperar. Este parámetro debe ser el ID del usuario.
     * @return Un objeto `ResponseEntity` que contiene:
     * - El usuario correspondiente al ID si se encuentra (código HTTP 200).
     * - Un mensaje de error si no se encuentra el usuario (código HTTP 404).
     * *
     * Respuestas posibles:
     * - **200 OK**: El usuario se encuentra, se retorna un objeto `UserDTO` con la información del usuario.
     * - **404 Not Found**: No se encuentra ningún usuario con el ID proporcionado, se retorna un objeto `ErrorResponse` con el mensaje de error.
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
     * Busca un usuario por su dirección de correo electrónico.
     * *
     * Recupera un usuario del sistema utilizando su correo electrónico único.
     * Si no se encuentra el usuario con el correo electrónico proporcionado, se devolverá un error con el código HTTP 404.
     *
     * @param email La dirección de correo electrónico del usuario. Este debe ser único en el sistema.
     * @return Un objeto `ResponseEntity` que contiene el usuario con el correo electrónico proporcionado.
     * En caso de error, se devolverá un mensaje de error con el código HTTP 404.
     * *
     * Respuestas posibles:
     * - **200 OK**: Se retorna el usuario que coincide con el correo electrónico proporcionado.
     * - **404 Not Found**: No se encuentra ningún usuario con el correo electrónico proporcionado.
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
    public ResponseEntity<?> getUsersByEmail(@PathVariable @Parameter(description = "Correo electrónico del usuario", required = true) String email) {
        try {
            return new ResponseEntity<>(serviceUser.getUserByEmail(email), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar el usuario con email '" + email + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Busca todos los usuarios cuyo nombre coincida con el proporcionado.
     * *
     * Recupera una lista de usuarios que coinciden con el nombre proporcionado.
     * Si no se encuentran usuarios, se devolverá una lista vacía con el código HTTP 200.
     *
     * @param name El nombre del usuario a buscar. Puede ser el primer nombre o cualquier parte del nombre.
     * @return Una lista de usuarios con el nombre proporcionado.
     * *
     * Respuestas posibles:
     * - **200 OK**: Se retorna una lista de objetos `UserDTO` con los usuarios que coinciden con el nombre proporcionado.
     * - **404 Not Found**: No se encontraron usuarios con el nombre proporcionado.
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
    public ResponseEntity<?> getUsersByName(@PathVariable @Parameter(description = "Nombre del usuario a buscar.", required = true) String name) {
        try {
            return new ResponseEntity<>(serviceUser.getUsersByName(name), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar el usuario con nombre '" + name + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Busca todos los usuarios cuyo apellido coincida con el proporcionado.
     * *
     * Recupera una lista de usuarios cuyo apellido coincide con el proporcionado.
     * Si no se encuentran usuarios, se devolverá una lista vacía con el código HTTP 200.
     * *
     *
     * @param lastname El apellido del usuario a buscar. Este parámetro puede ser el apellido completo o parte de él.
     * @return Una lista de usuarios que coinciden con el apellido proporcionado.
     * *
     * Respuestas posibles:
     * - **200 OK**: Se retorna una lista de objetos `UserDTO` con los usuarios cuyo apellido coincide con el proporcionado.
     * - **404 Not Found**: No se encontraron usuarios con el apellido proporcionado.
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
    public ResponseEntity<?> getUsersByLastname(@PathVariable @Parameter(description = "Apellido del usuario a buscar", required = true) String lastname) {
        try {
            return new ResponseEntity<>(serviceUser.getUsersByLastname(lastname), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar el usuario con apellido '" + lastname + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Busca todos los usuarios cuyo nombre y apellido coincidan con los proporcionados.
     * *
     * Recupera una lista de usuarios cuyo nombre y apellido coinciden con los parámetros proporcionados.
     * Si no se encuentran usuarios, se devolverá una lista vacía con el código HTTP 200.
     *
     * @param name     El nombre del usuario a buscar.
     * @param lastname El apellido del usuario a buscar.
     * @return Una lista de usuarios que coinciden con el nombre y apellido proporcionados.
     * *
     * Respuestas posibles:
     * - **200 OK**: Se retorna una lista de objetos `UserDTO` con los usuarios cuyo nombre y apellido coinciden con los proporcionados.
     * - **404 Not Found**: No se encontraron usuarios con el nombre y apellido proporcionados.
     */
    @GetMapping("/fullname/{name}/{lastname}")
    @Operation(summary = "Buscar usuarios por nombre y apellido",
            description = "Recupera usuarios cuyo nombre y apellido coinciden con los proporcionados. "
                    + "Si no se encuentran usuarios, se devolverá una lista vacía.",
            responses = {
                    @ApiResponse(description = "Usuarios encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = UserDTO.class)))),
                    @ApiResponse(responseCode = "404",
                            description = "No se encontraron usuarios con el nombre y apellido proporcionados.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getUsersByFullName(
            @PathVariable @Parameter(description = "Nombre del usuario a buscar.", required = true) String name,
            @PathVariable @Parameter(description = "Apellido del usuario a buscar.", required = true) String lastname) {
        try {
            return new ResponseEntity<>(serviceUser.getUsersByFullName(name, lastname), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar el usuario con nombre completo '" + name + " " + lastname + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Busca todos los usuarios que tengan el rol especificado.
     * *
     * Recupera una lista de usuarios que tienen el rol proporcionado.
     * Si no se encuentran usuarios con ese rol, se devolverá una lista vacía con el código HTTP 200.
     *
     * @param role El rol que se desea buscar. Este parámetro debe coincidir exactamente con el rol de los usuarios.
     * @return Una lista de usuarios que tienen el rol especificado.
     * *
     * Respuestas posibles:
     * - **200 OK**: Se retorna una lista de objetos `UserDTO` con los usuarios que tienen el rol proporcionado.
     * - **404 Not Found**: No se encontraron usuarios con el rol proporcionado.
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
    public ResponseEntity<?> getUsersByRole(@PathVariable @Parameter(description = "Rol del usuario a buscar", required = true) String role) {
        try {
            return new ResponseEntity<>(serviceUser.getUsersByRole(role), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar el usuario con rol '" + role + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Actualiza la información de un usuario existente.
     * *
     * Recupera un usuario utilizando su ID único y luego actualiza sus datos con la información proporcionada en el `UserDTO`.
     * Si el usuario no existe u ocurre un error durante la actualización, se devolverá un mensaje de error con el código HTTP 404.
     *
     * @param id          El identificador único del usuario que se desea actualizar. Este parámetro debe ser el ID del usuario a modificar.
     * @param updatedUser El objeto `UserDTO` que contiene la nueva información del usuario a actualizar.
     * @return Un objeto `ResponseEntity` con el usuario actualizado (código HTTP 200) o un mensaje de error (código HTTP 404).
     * *
     * Respuestas posibles:
     * - **200 OK**: El usuario fue actualizado correctamente y se retorna un objeto `UserDTO` con los datos actualizados del usuario.
     * - **404 Not Found**: No se encontró el usuario con el ID proporcionado o hubo un error al actualizarlo.
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
            @PathVariable @Parameter(description = "ID único del usuario que se desea actualizar.", required = true) String id,
            @RequestBody @Parameter(description = "Datos actualizados del usuario.") UserDTO updatedUser) {
        try {
            return new ResponseEntity<>(serviceUser.updateUser(MUser.INSTANCE.toEntity(serviceUser.getUserById(id)), updatedUser), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Elimina un usuario existente por su ID.
     * *
     * Este método permite eliminar a un usuario de la base de datos utilizando su ID único.
     * Si el usuario no se encuentra o hay un error durante la eliminación, se devolverá un mensaje de error con el código HTTP 404.
     *
     * @param id El identificador único del usuario que se desea eliminar. Este parámetro debe ser el ID del usuario a eliminar.
     * @return Un objeto `ResponseEntity` con un mensaje indicando el éxito de la eliminación (código HTTP 200) o un error si no se pudo eliminar (código HTTP 404).
     * *
     * Respuestas posibles:
     * - **200 OK**: El usuario fue eliminado exitosamente.
     * - **404 Not Found**: No se encontró el usuario con el ID proporcionado o hubo un error al eliminarlo.
     */
    @DeleteMapping("/Delete/{id}")
    @Operation(summary = "Eliminar un usuario",
            description = "Elimina un usuario existente utilizando su ID. "
                    + "Si el usuario no existe o hay un error, se devolverá un error con código HTTP 404.",
            responses = {
                    @ApiResponse(description = "Usuario eliminado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "404",
                            description = "No se pudo eliminar el usuario. El usuario puede no existir.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> deleteUser(@PathVariable @Parameter(description = "ID único del usuario que se desea eliminar.", required = true) String id) {
        try {
            return new ResponseEntity<>(serviceUser.deleteUser(id), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }
}
