package smartpot.com.api.Crops.Controller;

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
import smartpot.com.api.Crops.Model.DTO.CropDTO;
import smartpot.com.api.Crops.Service.SCropI;
import smartpot.com.api.Responses.ErrorResponse;
import smartpot.com.api.Users.Model.DTO.UserDTO;

@RestController
@RequestMapping("/Crops")
@Tag(name = "Cultivos", description = "Operaciones relacionadas con cultivos")
public class CropController {

    private final SCropI serviceCrop;

    /**
     * Constructor del controlador {@link CropController}.
     * <p>Se utiliza la inyección de dependencias para asignar el servicio {@link SCropI} que gestionará las operaciones
     * relacionadas con los cultivos.</p>
     *
     * @param serviceCrop El servicio que contiene la lógica de negocio para manejar cultivos.
     * @throws NullPointerException Si el servicio proporcionado es {@code null}.
     * @see SCropI
     */
    @Autowired
    public CropController(SCropI serviceCrop) {
        this.serviceCrop = serviceCrop;
    }

    /**
     * Crea un nuevo cultivo en el sistema.
     * <p>Este método permite crear un nuevo cultivo en la base de datos utilizando la información proporcionada en el objeto {@link CropDTO}.
     * Si el cultivo es creado exitosamente, se devolverá el objeto con la información del cultivo recién creado.</p>
     * <p>En caso de que ocurra un error durante la creación del cultivo, se devolverá un mensaje de error con el código HTTP 404.</p>
     *
     * @param cropDTO El objeto {@link CropDTO} que contiene los datos del nuevo cultivo a crear. Este objeto debe incluir toda la información necesaria para crear el cultivo.
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>El cultivo recién creado (código HTTP 201).</li>
     *           <li>Un mensaje de error en caso de que no se pueda crear el cultivo (código HTTP 404).</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>201 Created</b>: Si el cultivo es creado exitosamente, se retorna un objeto {@link CropDTO} con los detalles del cultivo creado.<br></li>
     *   <li><b>404 Not Found</b>: Si ocurre un error durante la creación del cultivo, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @PostMapping("/Create")
    @Operation(summary = "Crear un nuevo cultivo",
            description = "Crea un nuevo cultivo utilizando los datos proporcionados en el objeto CropDTO. "
                    + "Si la creación es exitosa, se devuelve el cultivo recién creado.",
            responses = {
                    @ApiResponse(description = "Cultivo creado",
                            responseCode = "201",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CropDTO.class))),
                    @ApiResponse(responseCode = "404",
                            description = "No se pudo crear el cultivo debido a un error.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> createCrop(@Parameter(description = "Datos del nuevo cultivo que se va a crear. Debe incluir tipo y usuario asociado.",
            required = true) @RequestBody CropDTO cropDTO) {
        try {
            return new ResponseEntity<>(serviceCrop.createCrop(cropDTO), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al crear el cultivo [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);

        }
    }

    /**
     * Recupera todos los cultivos registrados en el sistema.
     * <p>Este método devuelve una lista con todos los cultivos que están registrados en el sistema.</p>
     * <p>Si no se encuentran cultivos, se devolverá una lista vacía con el código HTTP 200.</p>
     * <p>En caso de error (por ejemplo, problemas con la conexión a la base de datos o un fallo en el servicio),
     * se devolverá un mensaje de error con el código HTTP 404.</p>
     *
     * @return Un objeto {@link ResponseEntity} que contiene una lista de todos los cultivos registrados (código HTTP 200).
     * En caso de error, se devolverá un mensaje de error con el código HTTP 404.
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Se retorna una lista de objetos {@link CropDTO} con la información de todos los cultivos registrados.<br></li>
     *   <li><b>404 Not Found</b>: No se encontraron cultivos registrados o hubo un error al recuperar los datos.<br></li>
     * </ul>
     */
    @GetMapping("/All")
    @Operation(summary = "Obtener todos los cultivos",
            description = "Recupera todos los cultivos registrados en el sistema. "
                    + "En caso de no haber cultivos, se devolverá una excepción.",
            responses = {
                    @ApiResponse(description = "Cultivos encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = UserDTO.class)))),
                    @ApiResponse(responseCode = "404",
                            description = "No se encontraron cultivos registrados.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getAllCrops() {
        try {
            return new ResponseEntity<>(serviceCrop.getAllCrops(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }


    /**
     * Busca un cultivo utilizando su ID único.
     * <p>Este método recupera un cultivo de la base de datos utilizando su identificador único. Si el cultivo con el ID proporcionado existe, se devolverá un objeto {@link CropDTO} con la información del cultivo.</p>
     * <p>Si no se encuentra un cultivo con el ID proporcionado, se devolverá un mensaje de error con el código HTTP 404.</p>
     *
     * @param id El identificador único del cultivo que se desea recuperar. Este parámetro debe ser el ID único del cultivo.
     *           El ID es obligatorio para la búsqueda del cultivo.
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>El cultivo correspondiente al ID si se encuentra (código HTTP 200).</li>
     *           <li>Un mensaje de error si no se encuentra el cultivo (código HTTP 404).</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Si el cultivo es encontrado, se retorna un objeto {@link CropDTO} con los detalles del cultivo.<br></li>
     *   <li><b>404 Not Found</b>: Si no se encuentra el cultivo con el ID proporcionado, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @GetMapping("/id/{id}")
    @Operation(summary = "Buscar cultivo por ID",
            description = "Recupera un cultivo utilizando su ID único. "
                    + "Si el cultivo no existe, se devolverá un error con el código HTTP 404.",
            responses = {
                    @ApiResponse(description = "Cultivo encontrado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))),
                    @ApiResponse(responseCode = "404",
                            description = "Cultivo no encontrado con el ID especificado.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getCropById(@Parameter(description = "ID único del cultivo", required = true) @PathVariable String id) {
        try {
            return new ResponseEntity<>(serviceCrop.getCropById(id), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar el cultivo con ID '" + id + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Busca cultivos por su estado actual.
     * <p>Este método recupera una lista de cultivos en el sistema filtrados por su estado actual. El parámetro `status` es utilizado para determinar el estado de los cultivos que se desean recuperar. Si se encuentran cultivos con el estado especificado, se devolverá una lista de objetos {@link CropDTO} que representan los cultivos encontrados.</p>
     * <p>Si no se encuentran cultivos con el estado proporcionado, se devolverá un mensaje de error con el código HTTP 404.</p>
     *
     * @param status El estado del cultivo que se desea buscar. Este parámetro es un valor que representa el estado en el que se encuentran los cultivos (por ejemplo, "ACTIVO", "INACTIVO", "EN_REPOSO", etc.).
     *               El valor del estado será utilizado para filtrar los cultivos.
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>Una lista de objetos {@link CropDTO} que representan los cultivos en el estado especificado, si se encuentran (código HTTP 200).</li>
     *           <li>Un mensaje de error con el código HTTP 404 si no se encuentran cultivos con el estado proporcionado.</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Si se encuentran cultivos con el estado proporcionado, se retorna una lista de objetos {@link CropDTO} con los detalles de los cultivos.<br></li>
     *   <li><b>404 Not Found</b>: Si no se encuentran cultivos con el estado proporcionado, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "Buscar cultivos por estado",
            description = "Recupera una lista de cultivos filtrados por el estado proporcionado. Si no se encuentran cultivos en el estado especificado, se devolverá un error.",
            responses = {
                    @ApiResponse(description = "Cultivos encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CropDTO.class))),
                    @ApiResponse(responseCode = "404",
                            description = "No se encontraron cultivos con el estado proporcionado.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getCropsByStatus(@Parameter(description = "Estado de los cultivos a buscar", required = true) @PathVariable String status) {
        try {
            return new ResponseEntity<>(serviceCrop.getCropsByStatus(status), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar cultivos con estado '" + status + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Recupera todos los estados de cultivo registrados en el sistema.
     * <p>Este método obtiene una lista de todos los estados de cultivo disponibles en el sistema. Si no se encuentran estados de cultivo,
     * se lanzará una excepción y se devolverá un código HTTP 404 con un mensaje de error.</p>
     *
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>Una lista de cadenas {@link String} con los estados de cultivo (código HTTP 200).</li>
     *           <li>Un mensaje de error si ocurre un problema al obtener los estados de cultivo o no se encuentran registrados (código HTTP 404).</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Si se encuentran estados de cultivo registrados, se retorna una lista de cadenas con los nombres de los estados de cultivo en formato JSON.<br></li>
     *   <li><b>404 Not Found</b>: Si no se encuentran estados de cultivo registrados o ocurre un error al obtenerlos, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @GetMapping("/status/All")
    @Operation(summary = "Obtener todos los estados de cultivo",
            description = "Recupera todos los estados de cultivos registrados en el sistema. "
                    + "En caso de no haber estados de cultivos, se devolverá una excepción.",
            responses = {
                    @ApiResponse(description = "Estados de cultivo encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = String.class)))),
                    @ApiResponse(responseCode = "404",
                            description = "No se encontraron estados de cultivo registrados.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getAllStatus() {
        try {
            return new ResponseEntity<>(serviceCrop.getAllStatus(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar los estados de cultivo [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Busca cultivos por su tipo.
     * <p>Este método recupera una lista de cultivos en el sistema filtrados por su tipo. El parámetro `type` es utilizado para determinar el tipo de los cultivos que se desean recuperar. Si se encuentran cultivos con el tipo especificado, se devolverá una lista de objetos {@link CropDTO} que representan los cultivos encontrados.</p>
     * <p>Si no se encuentran cultivos con el tipo proporcionado, se devolverá un mensaje de error con el código HTTP 404.</p>
     *
     * @param type El tipo del cultivo que se desea buscar. Este parámetro es un valor que representa el tipo del cultivo (por ejemplo, "Frutal", "Hortaliza", "Cereal", etc.).
     *             El valor del tipo será utilizado para filtrar los cultivos.
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>Una lista de objetos {@link CropDTO} que representan los cultivos del tipo especificado, si se encuentran (código HTTP 200).</li>
     *           <li>Un mensaje de error con el código HTTP 404 si no se encuentran cultivos con el tipo proporcionado.</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Si se encuentran cultivos con el tipo proporcionado, se retorna una lista de objetos {@link CropDTO} con los detalles de los cultivos.<br></li>
     *   <li><b>404 Not Found</b>: Si no se encuentran cultivos con el tipo proporcionado, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @GetMapping("/type/{type}")
    @Operation(summary = "Buscar cultivos por tipo",
            description = "Recupera una lista de cultivos filtrados por el tipo proporcionado. Si no se encuentran cultivos con el tipo especificado, se devolverá un error.",
            responses = {
                    @ApiResponse(description = "Cultivos encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CropDTO.class))),
                    @ApiResponse(responseCode = "404",
                            description = "No se encontraron cultivos con el tipo proporcionado.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getCropsByType(@Parameter(description = "Tipo de los cultivos a buscar", required = true) @PathVariable String type) {
        try {
            return new ResponseEntity<>(serviceCrop.getCropsByType(type), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar cultivos con tipo '" + type + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Recupera todos los tipos de cultivo registrados en el sistema.
     * <p>Este método obtiene una lista de todos los tipos de cultivo disponibles en el sistema. Si no se encuentran tipos de cultivo,
     * se lanzará una excepción y se devolverá un código HTTP 404 con un mensaje de error.</p>
     *
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>Una lista de cadenas {@link String} con los tipos de cultivo (código HTTP 200).</li>
     *           <li>Un mensaje de error si ocurre un problema al obtener los tipos de cultivo o no se encuentran registrados (código HTTP 404).</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Si se encuentran tipos de cultivo registrados, se retorna una lista de cadenas con los nombres de los tipos de cultivo en formato JSON.<br></li>
     *   <li><b>404 Not Found</b>: Si no se encuentran tipos de cultivo registrados o ocurre un error al obtenerlos, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @GetMapping("/type/All")
    @Operation(summary = "Obtener todos los tipos de cultivo",
            description = "Recupera todos los tipos de cultivos registrados en el sistema. "
                    + "En caso de no haber tipos de cultivos, se devolverá una excepción.",
            responses = {
                    @ApiResponse(description = "Tipos de cultivo encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = String.class)))),
                    @ApiResponse(responseCode = "404",
                            description = "No se encontraron tipos de cultivo registrados.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getAllTypes() {
        try {
            return new ResponseEntity<>(serviceCrop.getAllTypes(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar los tipos de cultivo [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Busca todos los cultivos asociados a un usuario específico.
     * <p>Este método recupera todos los cultivos pertenecientes a un usuario específico utilizando su ID único. Si el usuario con el ID proporcionado tiene cultivos, se devolverá una lista de objetos {@link CropDTO} con la información de cada cultivo.</p>
     * <p>Si no se encuentran cultivos asociados al usuario, se devolverá una lista vacía o un mensaje de error dependiendo de la implementación.</p>
     *
     * @param id El identificador único del usuario que posee los cultivos. Este parámetro es obligatorio y debe ser el ID único del usuario.
     *           Se utiliza para buscar los cultivos asociados a este usuario en la base de datos.
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>Una lista de cultivos si el usuario tiene cultivos asociados (código HTTP 200).</li>
     *           <li>Un mensaje de error si ocurre algún problema durante la búsqueda de los cultivos (código HTTP 404 o 500 dependiendo del tipo de error).</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Si se encuentran cultivos asociados al usuario, se retorna una lista de objetos {@link CropDTO} con los detalles de los cultivos.<br></li>
     *   <li><b>404 Not Found</b>: Si no se encuentra el usuario con el ID proporcionado o si el usuario no tiene cultivos asociados, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @GetMapping("/user/{id}")
    @Operation(summary = "Buscar cultivos por usuario",
            description = "Recupera todos los cultivos asociados a un usuario específico utilizando su ID único. "
                    + "Si no se encuentran cultivos para el usuario, se devolverá un error con el código HTTP 404.",
            responses = {
                    @ApiResponse(description = "Cultivos encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CropDTO.class)))),
                    @ApiResponse(responseCode = "404",
                            description = "No se encontraron cultivos para el usuario con el ID especificado.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getCropByUser(@PathVariable @Parameter(description = "ID único del usuario para buscar sus cultivos", required = true) String id) {
        try {
            return new ResponseEntity<>(serviceCrop.getCropsByUser(id), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar los cultivos del usuario con ID '" + id + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }


    /**
     * Cuenta el número total de cultivos que tiene un usuario.
     * <p>Este método recupera el número total de cultivos asociados a un usuario específico utilizando su ID único.
     * Si el usuario con el ID proporcionado tiene cultivos, se devolverá el número total de cultivos.</p>
     * <p>Si el usuario no tiene cultivos o no existe, se devolverá un mensaje de error con el código HTTP 404.</p>
     *
     * @param id El identificador único del usuario. Este parámetro es obligatorio y se utiliza para buscar al usuario en la base de datos
     *           y contar los cultivos asociados a su cuenta.
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>El número total de cultivos del usuario si existen cultivos asociados (código HTTP 200).</li>
     *           <li>Un mensaje de error si el usuario no tiene cultivos o no existe (código HTTP 404 o 500 dependiendo del tipo de error).</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Si el usuario tiene cultivos asociados, se retorna un valor numérico representando el número total de cultivos del usuario.<br></li>
     *   <li><b>404 Not Found</b>: Si no se encuentra el usuario con el ID proporcionado o si no tiene cultivos asociados, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @GetMapping("/count/{id}")
    @Operation(summary = "Contar cultivos de un usuario",
            description = "Recupera el número total de cultivos asociados a un usuario específico utilizando su ID único. "
                    + "Si el usuario no tiene cultivos o el ID no es válido, se devolverá un error con el código HTTP 404.",
            responses = {
                    @ApiResponse(description = "Número total de cultivos encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Integer.class))),
                    @ApiResponse(responseCode = "404",
                            description = "No se encontraron cultivos para el usuario con el ID especificado.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> countCropsByUser(@PathVariable @Parameter(description = "ID único del usuario para buscar sus cultivos", required = true) String id) {
        try {
            return new ResponseEntity<>(serviceCrop.countCropsByUser(id), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al contar los cultivos del usuario con ID '" + id + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Actualiza un cultivo existente en el sistema.
     * <p>Este método permite actualizar los datos de un cultivo ya existente en la base de datos utilizando su ID único.
     * Si el cultivo con el ID proporcionado existe, se actualizarán sus detalles con la información proporcionada en el objeto {@link CropDTO}.</p>
     * <p>Si el cultivo no existe o si ocurre algún error durante el proceso de actualización, se devolverá un mensaje de error con el código HTTP 404.</p>
     *
     * @param id          El identificador único del cultivo que se desea actualizar. Este parámetro es obligatorio para identificar el cultivo en la base de datos.
     *                    El ID debe ser válido y hacer referencia a un cultivo existente.
     * @param cropDetails El objeto {@link CropDTO} que contiene los nuevos datos del cultivo que se desean actualizar. Este objeto debe incluir toda la información que reemplazará los detalles actuales del cultivo.
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>El cultivo actualizado (código HTTP 200).</li>
     *           <li>Un mensaje de error si no se puede actualizar el cultivo (código HTTP 404).</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>200 OK</b>: Si el cultivo es actualizado exitosamente, se retorna un objeto {@link CropDTO} con los detalles del cultivo actualizado.<br></li>
     *   <li><b>404 Not Found</b>: Si no se encuentra el cultivo con el ID proporcionado, o si ocurre algún error en la actualización, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @PutMapping("/Update/{id}")
    @Operation(summary = "Actualizar un cultivo",
            description = "Actualiza los datos de un cultivo existente utilizando su ID. "
                    + "Si el cultivo no existe o los datos son inválidos, se devolverá un error con el código HTTP 404.",
            responses = {
                    @ApiResponse(description = "Cultivo actualizado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CropDTO.class))),
                    @ApiResponse(responseCode = "404",
                            description = "Cultivo no encontrado o error en la actualización.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> updateCrop(@Parameter(description = "ID único del cultivo a actualizar", required = true) @PathVariable String id, @Parameter(description = "Información del cultivo a actualizar", required = true) @RequestBody CropDTO cropDetails) {
        try {
            return new ResponseEntity<>(serviceCrop.updatedCrop(id, cropDetails), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al actualizar el cultivo con ID '" + id + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }


    /**
     * Elimina un cultivo existente del sistema por su ID único.
     * <p>Este método permite eliminar un cultivo existente en la base de datos utilizando su ID único.
     * Si el cultivo con el ID proporcionado existe, será eliminado del sistema.</p>
     * <p>Si no se encuentra el cultivo con el ID proporcionado, o si ocurre algún error durante el proceso de eliminación, se devolverá un mensaje de error con el código HTTP 404.</p>
     *
     * @param id El identificador único del cultivo que se desea eliminar. Este parámetro es obligatorio y debe hacer referencia a un cultivo existente.
     * @return Un objeto {@link ResponseEntity} que contiene:
     *         <ul>
     *           <li>Una respuesta vacía con código HTTP 204 (sin contenido) si el cultivo se elimina correctamente.</li>
     *           <li>Un mensaje de error si no se puede eliminar el cultivo (código HTTP 404).</li>
     *         </ul>
     *
     * <p><b>Respuestas posibles:</b></p>
     * <ul>
     *   <li><b>204 No Content</b>: Si el cultivo es eliminado exitosamente, se retorna una respuesta vacía con código HTTP 204, lo que indica que la operación se completó correctamente y no se retorna contenido.<br></li>
     *   <li><b>404 Not Found</b>: Si no se encuentra el cultivo con el ID proporcionado, o si ocurre algún error durante la eliminación, se retorna un objeto {@link ErrorResponse} con un mensaje de error.<br></li>
     * </ul>
     */
    @DeleteMapping("/Delete/{id}")
    @Operation(summary = "Eliminar un cultivo",
            description = "Elimina un cultivo existente utilizando su ID. "
                    + "Si el cultivo no existe o hay un error en el proceso, se devolverá un error con el código HTTP 404.",
            responses = {
                    @ApiResponse(description = "Cultivo eliminado",
                            responseCode = "204",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "404",
                            description = "Cultivo no encontrado o error en la eliminación.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> deleteCrop(@Parameter(description = "ID único del cultivo a eliminar", required = true) @PathVariable String id) {
        try {
            return new ResponseEntity<>(serviceCrop.deleteCrop(id), HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al eliminar el cultivo con ID '" + id + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }
}