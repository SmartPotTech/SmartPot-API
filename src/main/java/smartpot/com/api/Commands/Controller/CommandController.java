package smartpot.com.api.Commands.Controller;

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
import smartpot.com.api.Commands.Model.DTO.CommandDTO;
import smartpot.com.api.Commands.Model.Entity.Command;
import smartpot.com.api.Commands.Service.SCommandI;
import smartpot.com.api.Crops.Model.DTO.CropDTO;
import smartpot.com.api.Responses.ErrorResponse;

import java.util.Date;

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

    @PutMapping("/{id}/ejecutar")
    public ResponseEntity<Command> executeCommand(@PathVariable String id) throws Exception {
        Command command = serviceCommand.getCommandById(id);
        if (command != null) {
            command.setStatus("EXECUTED");
            command.setDateExecuted(new Date());
            command.setResponse("SUCCESSFUL");
            Command updatedCommand = serviceCommand.updateCommand(id, command);
            return ResponseEntity.ok(updatedCommand);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Object> deleteCommand(@PathVariable String id) {
        if (serviceCommand.getCommandById(id) != null) {
            serviceCommand.deleteCommand(id);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/Update/{id}")
    public Command updateCommand(@PathVariable String id, @RequestBody Command updatedCommad) throws Exception {
        return serviceCommand.updateCommand(id, updatedCommad);
    }

}
