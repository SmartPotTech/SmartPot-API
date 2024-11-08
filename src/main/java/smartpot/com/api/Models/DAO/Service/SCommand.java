package smartpot.com.api.Models.DAO.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import smartpot.com.api.Models.DAO.Repository.RCommand;
import smartpot.com.api.Models.Entity.Command;
import smartpot.com.api.Exception.ApiResponse;
import smartpot.com.api.Exception.ApiException;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Service
public class SCommand {


    @Autowired
    private RCommand repositoryCommand;

    private final SCrop sCrop = new SCrop();

    public List<Command> getAllCommands() {
        return repositoryCommand.findAll();
    }

    public Command getCommandById(String id) {
        return repositoryCommand.findById(new ObjectId(id)).orElse(null);
    }

    public Command createCommand(Command newCommand) {
        newCommand.setDateCreated(new Date());
        newCommand.setStatus("PENDING");
        return repositoryCommand.save(newCommand);
    }

    public Command updateCommand(String id, Command upCommand) {
        if (!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "El ID '" + id + "' no es válido. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        Command exCommand = repositoryCommand.findById(new ObjectId(id)).orElseThrow(() -> new ApiException(
                new ApiResponse("El Comando con ID '" + id + "' no fue encontrado.",
                        HttpStatus.NOT_FOUND.value())
        ));

        if (upCommand == null) {
            throw new IllegalArgumentException("El comando de actualización no puede ser nulo");
        }

        if (upCommand.getCrop() == null && sCrop.getCropById(upCommand.getCrop().toString())!=null  ) {
            throw new IllegalArgumentException("El campo 'crop' no puede ser nulo");
        }

        if (upCommand.getDateCreated() == null) {
            throw new IllegalArgumentException("El campo 'dateCreated' no puede ser nulo");
        }

        // Validar y convertir commandType a mayúsculas
        if (upCommand.getCommandType() == null || upCommand.getCommandType().isEmpty()) {
            throw new IllegalArgumentException("El campo 'commandType' no puede estar vacío");
        } else {
            exCommand.setCommandType(upCommand.getCommandType().toUpperCase());
        }

        // Validar y convertir status a mayúsculas
        if (upCommand.getStatus() == null || upCommand.getStatus().isEmpty()) {
            throw new IllegalArgumentException("El campo 'status' no puede estar vacío");
        } else {
            exCommand.setStatus(upCommand.getStatus().toUpperCase());
        }

        // Si se cumplen todas las validaciones, se procede a actualizar el comando
        if (exCommand != null) {
            exCommand.setCrop(upCommand.getCrop());
            exCommand.setResponse(upCommand.getResponse());
            exCommand.setDateCreated(upCommand.getDateCreated());
            return repositoryCommand.save(exCommand);
        }
        else{
            return null;
        }
        /*


        if (!upCommand.getCommandType().matches(exCommand.getCommandType())) {
            throw new Exception(new ErrorResponse(
                    "El nombre '" + upCommand.getCommandType() + "' no es válido.",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        if (!upCommand.ge) {
        }
        */


    }

    public void deleteCommand(String id) {
        repositoryCommand.deleteById(new ObjectId(id));
    }

/*




    public Command executeCommand(String id, String response) {
        Command command = repositoryCommand.findById(id).orElse(null);
        if (command != null) {
            command.setStatus("EXECUTED");
            command.setDateExecuted(new Date());
            command.setResponse(response);
            return repositoryCommand.save(command);
        }
        return null;
    }



    public List<Command> getCommandsByStatus(String status) {
        return repositoryCommand.findByStatus(status);
    }

    public List<Command> getCommandsByCropId(String cropId) {
        return repositoryCommand.findByCrop_Id(cropId);
    }

     */
}
