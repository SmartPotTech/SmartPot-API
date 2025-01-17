package smartpot.com.api.Commands.Service;

import lombok.Builder;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import smartpot.com.api.Commands.Mapper.MCommand;
import smartpot.com.api.Commands.Model.DTO.CommandDTO;
import smartpot.com.api.Commands.Model.Entity.Command;
import smartpot.com.api.Commands.Repository.RCommand;
import smartpot.com.api.Crops.Service.SCropI;
import smartpot.com.api.Exception.ApiException;
import smartpot.com.api.Exception.ApiResponse;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Data
@Builder
@Service
public class SCommand implements SCommandI {

    private final RCommand repositoryCommand;
    private final SCropI serviceCrop;
    private final MCommand mapperCommand;

    @Autowired
    public SCommand(RCommand repositoryCommand, SCropI serviceCrop, MCommand mapperCommand) {
        this.repositoryCommand = repositoryCommand;
        this.serviceCrop = serviceCrop;
        this.mapperCommand = mapperCommand;
    }

    @Override
    public List<Command> getAllCommands() {
        return repositoryCommand.findAll();
    }

    @Override
    @Cacheable(value = "commands", key = "'id_'+#id")
    public CommandDTO getCommandById(String id) throws Exception {
        return Optional.of(id)
                .map(ObjectId::new)
                .map(repositoryCommand::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(mapperCommand::toDTO)
                .orElseThrow(() -> new Exception("El Comando no existe"));
    }

    @Override
    public CommandDTO createCommand(CommandDTO commandDTO) throws IllegalStateException {
        return Optional.of(commandDTO)
                .map(dto -> {
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    dto.setDateCreated(formatter.format(new Date()));
                    dto.setStatus("PENDING");
                    return dto;
                })
                .map(mapperCommand::toEntity)
                .map(repositoryCommand::save)
                .map(mapperCommand::toDTO)
                .orElseThrow(() -> new IllegalStateException("El Comando ya existe"));
    }

    @Override
    public Command updateCommand(String id, Command upCommand) throws Exception {
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

        if (upCommand.getCrop() == null && serviceCrop.getCropById(upCommand.getCrop().toString()) != null) {
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
        } else {
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

    @Override
    @CacheEvict(value = "commands", key = "'id_'+#id")
    public String deleteCommand(String id) throws Exception {
        return Optional.of(getCommandById(id))
                .map(command -> {
                    repositoryCommand.deleteById(new ObjectId(command.getId()));
                    return "El Comando con ID '" + id + "' fue eliminado.";
                })
                .orElseThrow(() -> new Exception("El Comando no existe."));
    }

    @Override
    @CachePut(value = "commands", key = "'id:'+#id")
    public CommandDTO excuteCommand(String id, String response) throws Exception {
        return Optional.of(getCommandById(id))
                .map(commandDTO -> {
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    commandDTO.setDateCreated(formatter.format(new Date()));
                    commandDTO.setStatus("EXECUTED");
                    commandDTO.setResponse(response);
                    return commandDTO;
                })
                .map(mapperCommand::toEntity)
                .map(repositoryCommand::save)
                .map(mapperCommand::toDTO)
                .orElseThrow(() -> new Exception("El Comando no se pudo actualizar"));
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
