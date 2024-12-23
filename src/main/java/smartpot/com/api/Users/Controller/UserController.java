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
import smartpot.com.api.Users.Model.DAO.Service.SUserI;
import smartpot.com.api.Users.Model.DTO.UserDTO;
import smartpot.com.api.Users.Model.Entity.User;

import java.util.List;

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
     * Crea un nuevo usuario.
     *
     * @param userDTO El objeto Usuario que contiene los datos del usuario que se guardarán.
     * @return El objeto Usuario creado.
     */
    @PostMapping("/Create")
    @Operation(summary = "Crear un nuevo usuario", description = "Crea un nuevo usuario con la información proporcionada." ,
            responses = {
                    @ApiResponse(description = "Usuario Creado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))
                    ),
                    @ApiResponse(responseCode = "404",
                            description = "Usuario no Creado",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    ),

            })
    public ResponseEntity<?> createUser(@Parameter(description = "Datos del nuevo usuario", required = true) @RequestBody UserDTO userDTO) {
        try {
            return new ResponseEntity<>(serviceUser.CreateUser(userDTO), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Recupera todos los usuarios registrados.
     *
     * @return Una lista de todos los usuarios registrados.
     */
    @GetMapping("/All")
    @Operation(summary = "Obtener todos los usuarios", description = "Recupera todos los usuarios registrados en el sistema.",
            responses = {
                    @ApiResponse(description = "Usuarios encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(
                                            schema = @Schema(implementation = UserDTO.class)
                                    ))
                    ),
                    @ApiResponse(responseCode = "404",
                            description = "Usuarios no encontrados",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    ),
            })
    public ResponseEntity<?> getAllUsers() {
        try {
            return new ResponseEntity<>(serviceUser.getAllUsers(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Encuentra un usuario por su ID.
     *
     * @param id El ID del usuario a recuperar.
     * @return El objeto Usuario correspondiente al ID proporcionado.
     */
    @GetMapping("/id/{id}")
    @Operation(summary = "Buscar usuario por ID", description = "Recupera un usuario utilizando su ID.",
            responses = {
                    @ApiResponse(description = "Usuario encontrado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))
                    ),
                    @ApiResponse(responseCode = "404",
                            description = "Usuario no encontrado",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    ),

            })
    public ResponseEntity<?> getUserById(@PathVariable @Parameter(description = "ID del usuario") String id) {
        try {
            return new ResponseEntity<>(serviceUser.getUserById(id), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Encuentra usuarios por su dirección de correo electrónico.
     *
     * @param email La dirección de correo electrónico por la que filtrar los usuarios.
     * @return Un usuario que coincide con el correo electrónico proporcionado.
     */
    @GetMapping("/email/{email}")
    @Operation(summary = "Buscar usuario por correo electrónico", description = "Recupera un usuario utilizando su correo electrónico.",
            responses = {
                    @ApiResponse(description = "Usuario encontrado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))
                    ),
                    @ApiResponse(responseCode = "404",
                            description = "Usuario no encontrado",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    ),

            })
    public ResponseEntity<?> getUsersByEmail(@PathVariable @Parameter(description = "Correo electrónico del usuario") String email) {
        try {
            return new ResponseEntity<>(serviceUser.getUserByEmail(email), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Encuentra usuarios por su nombre.
     *
     * @param name El nombre por el que filtrar los usuarios.
     * @return Una lista de usuarios que coinciden con el nombre proporcionado.
     */
    @GetMapping("/name/{name}")
    @Operation(summary = "Buscar usuarios por nombre", description = "Recupera usuarios cuyo nombre coincide con el proporcionado.",
            responses = {
                    @ApiResponse(description = "Usuarios encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(
                                        schema = @Schema(implementation = UserDTO.class)
                                    ))
                    ),
                    @ApiResponse(responseCode = "404",
                            description = "Usuarios no encontrados",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    ),
            })
    public ResponseEntity<?> getUsersByName(@PathVariable @Parameter(description = "Nombre del usuario") String name) {
        try {
            return new ResponseEntity<>(serviceUser.getUsersByName(name), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Encuentra usuarios por su apellido.
     *
     * @param lastname El apellido por el que filtrar los usuarios.
     * @return Una lista de usuarios que coinciden con el apellido proporcionado.
     */
    @GetMapping("/lastname/{lastname}")
    @Operation(summary = "Buscar usuarios por apellido", description = "Recupera usuarios cuyo apellido coincide con el proporcionado.",
            responses = {
                    @ApiResponse(description = "Usuarios encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(
                                            schema = @Schema(implementation = UserDTO.class)
                                    ))
                    ),
                    @ApiResponse(responseCode = "404",
                            description = "Usuarios no encontrados",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    ),
            })
    public ResponseEntity<?> getUsersByLastname(@PathVariable @Parameter(description = "Apellido del usuario") String lastname) {
        try {
            return new ResponseEntity<>(serviceUser.getUsersByLastname(lastname), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Encuentra usuarios por su nombre y apellido.
     *
     * @param name     El nombre por el que filtrar los usuarios.
     * @param lastname El apellido por el que filtrar los usuarios.
     * @return Una lista de usuarios que coinciden con el nombre y apellido proporcionado.
     */
    @GetMapping("/fullname/{name}/{lastname}")
    @Operation(summary = "Buscar usuarios por nombre y apellido", description = "Recupera usuarios cuyo nombre y apellido coinciden con los proporcionados.",
            responses = {
                    @ApiResponse(description = "Usuarios encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(
                                            schema = @Schema(implementation = UserDTO.class)
                                    ))
                    ),
                    @ApiResponse(responseCode = "404",
                            description = "Usuarios no encontrados",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    ),
            })
    public ResponseEntity<?> getUsersByFullname(@PathVariable @Parameter(description = "Nombre del usuario") String name,
                                         @PathVariable @Parameter(description = "Apellido del usuario") String lastname) {
        try {
            return new ResponseEntity<>(serviceUser.getUsersByFullName(name, lastname), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Encuentra usuarios por su rol.
     *
     * @param role El rol por el que filtrar los usuarios.
     * @return Una lista de usuarios que coinciden con el rol especificado.
     */
    @GetMapping("/role/{role}")
    @Operation(summary = "Buscar usuarios por rol", description = "Recupera usuarios cuyo rol coincide con el proporcionado.",
            responses = {
                    @ApiResponse(description = "Usuarios encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(
                                            schema = @Schema(implementation = UserDTO.class)
                                    ))
                    ),
                    @ApiResponse(responseCode = "404",
                            description = "Usuarios no encontrados",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    ),
            })
    public ResponseEntity<?> getUsersByRole(@PathVariable @Parameter(description = "Rol del usuario") String role) {
        try {
            return new ResponseEntity<>( serviceUser.getUsersByRole(role), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Actualiza un usuario existente.
     *
     * @param id          El ID del usuario a actualizar.
     * @param updatedUser El objeto Usuario que contiene los nuevos datos.
     * @return El objeto Usuario actualizado.
     */
    @PutMapping("/Update/{id}")
    @Operation(summary = "Actualizar un usuario", description = "Actualiza los datos de un usuario existente utilizando su ID.",
            responses = {
                    @ApiResponse(description = "Usuario actualizado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))
                    ),
                    @ApiResponse(responseCode = "404",
                            description = "Usuario no actualizado",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    ),

            })
    public ResponseEntity<?> updateUser(@PathVariable @Parameter(description = "ID del usuario a actualizar") String id,
                           @RequestBody @Parameter(description = "Datos actualizados del usuario") UserDTO updatedUser) {
        try {
            return new ResponseEntity<>(serviceUser.updateUser(serviceUser.getUserById(id), updatedUser), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Elimina un usuario existente por su ID.
     *
     * @param id El ID del usuario a eliminar.
     */
    @DeleteMapping("/Delete/{id}")
    @Operation(summary = "Eliminar un usuario", description = "Elimina un usuario existente utilizando su ID.",
            responses = {
                    @ApiResponse(description = "Usuario eliminado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = String.class))
                    ),
                    @ApiResponse(responseCode = "404",
                            description = "Usuario no eliminado",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
                    ),

            })
    public ResponseEntity<?> deleteUser(@PathVariable @Parameter(description = "ID del usuario a eliminar") String id) {
        try {
            return new ResponseEntity<>(serviceUser.deleteUser(serviceUser.getUserById(id)), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }
}
