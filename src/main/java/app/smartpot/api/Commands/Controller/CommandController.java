package app.smartpot.api.Commands.Controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import app.smartpot.api.Commands.Model.DTO.CommandDTO;
import app.smartpot.api.Commands.Service.SCommandI;
import app.smartpot.api.crops.model.DTO.CropDTO;
import app.smartpot.api.responses.DeleteResponse;
import app.smartpot.api.responses.ErrorResponse;

@RestController
@RequestMapping("/Comandos")
public class CommandController {

    private final SCommandI serviceCommand;

    @Autowired
    public CommandController(SCommandI serviceCommand) {
        this.serviceCommand = serviceCommand;
    }

    @PostMapping("/Create")
    @Operation(summary = "Crear un nuevo comando",
            description = "Crea un nuevo comando utilizando los datos proporcionados en el objeto CommandDTO. "
                    + "Si la creación es exitosa, se devuelve el comando recién creado.",
            responses = {
                    @ApiResponse(description = "Comando creado",
                            responseCode = "201",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CropDTO.class))),
                    @ApiResponse(responseCode = "404",
                            description = "No se pudo crear el Comando debido a un error.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> createCommand(@Parameter(description = "Datos del nuevo comando que se va a crear. Debe incluir tipo y cultivo asociado.",
            required = true) @RequestBody CommandDTO commandDTO) {
        try {
            return new ResponseEntity<>(serviceCommand.createCommand(commandDTO), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al crear el comando [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);

        }
    }

    @GetMapping("/All")
    @Operation(summary = "Obtener todos los comandos",
            description = "Recupera todos los comandos registrados en el sistema. "
                    + "En caso de no haber comandos, se devolverá una excepción.",
            responses = {
                    @ApiResponse(description = "Comandos encontrados",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = CommandDTO.class)))),
                    @ApiResponse(responseCode = "404",
                            description = "No se encontraron Comandos registrados.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getAllCommand() {
        try {
            return new ResponseEntity<>(serviceCommand.getAllCommands(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al obtener los comandos [" + e.getMessage() + "]", HttpStatus.INTERNAL_SERVER_ERROR.value()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/id/{id}")
    @Operation(summary = "Buscar comando por ID",
            description = "Recupera un comando utilizando su ID único. "
                    + "Si el comando no existe, se devolverá un error con el código HTTP 404.",
            responses = {
                    @ApiResponse(description = "Comando encontrado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommandDTO.class))),
                    @ApiResponse(responseCode = "404",
                            description = "Comando no encontrado con el ID especificado.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> getUserById(@PathVariable String id) {
        try {
            return new ResponseEntity<>(serviceCommand.getCommandById(id), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al buscar el comando con ID '" + id + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/{id}/run/{response}")
    @Operation(summary = "Actualizar un comando a ejecutado",
            description = "Actualiza los datos de un comando existente utilizando su ID. "
                    + "Si el comando no existe o hay un error, se devolverá un error con código HTTP 404.",
            responses = {
                    @ApiResponse(description = "Comando actualizado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommandDTO.class))),
                    @ApiResponse(responseCode = "404",
                            description = "No se pudo actualizar el Comando. El Comando puede no existir o los datos pueden ser incorrectos.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> executeCommand(@PathVariable String id, @PathVariable String response) {
        try {
            return new ResponseEntity<>(serviceCommand.executeCommand(id, response), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al actualizar el comando con ID '" + id + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/Delete/{id}")
    @Operation(summary = "Eliminar un Comando",
            description = "Elimina un Comando existente utilizando su ID. "
                    + "Si el Comando no existe o hay un error, se devolverá un error con código HTTP 404.",
            responses = {
                    @ApiResponse(description = "Comando eliminado",
                            responseCode = "200",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeleteResponse.class))),
                    @ApiResponse(responseCode = "404",
                            description = "No se pudo eliminar el Comando.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> deleteCommand(@Parameter(description = "ID único del comando que se desea eliminar.", required = true) @PathVariable String id) {
        try {
            return new ResponseEntity<>(new DeleteResponse("Se ha eliminado un recurso [" + serviceCommand.deleteCommand(id) + "]"), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al actualizar el comando con ID '" + id + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/Update/{id}")
    @Operation(summary = "Eliminar un comando",
            description = "Elimina un comando existente utilizando su ID. "
                    + "Si el comando no existe o hay un error en el proceso, se devolverá un error con el código HTTP 404.",
            responses = {
                    @ApiResponse(description = "Comando eliminado",
                            responseCode = "204",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "404",
                            description = "Comando no encontrado o error en la eliminación.",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> updateCommand(@PathVariable String id, @RequestBody CommandDTO updatedCommand) {
        try {
            return new ResponseEntity<>(new DeleteResponse("Se ha eliminado un recurso [" + serviceCommand.updateCommand(id, updatedCommand) + "]"), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse("Error al actualizar el comando con ID '" + id + "' [" + e.getMessage() + "]", HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

}
