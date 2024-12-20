package smartpot.com.api.Commands.Controller;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smartpot.com.api.Commands.Model.DAO.Service.SCommand;
import smartpot.com.api.Crops.Model.DAO.Service.SCrop;
import smartpot.com.api.Commands.Model.Entity.Command;
import smartpot.com.api.Crops.Model.Entity.Crop;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/Comandos")
public class CommandController {

    private final SCommand serviceCommand;
    private final SCrop serviceCrop;

    @Autowired
    public CommandController(SCommand serviceCommand, SCrop serviceCrop) {
        this.serviceCommand = serviceCommand;
        this.serviceCrop = serviceCrop;
    }

    @GetMapping("/All")
    public List<Command> getAllCommand() {
        return serviceCommand.getAllCommands();
    }

    @GetMapping("/id/{id}")
    public Command getUserById(@PathVariable String id) {
        return serviceCommand.getCommandById(id);
    }

    @PostMapping("/commandCreate/{cropId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Command> createCommand(@PathVariable String cropId, @RequestBody Command newCommand) {
        Optional<Crop> cropOpt = Optional.ofNullable(serviceCrop.getCropById(cropId));
        if (cropOpt.isPresent()) {
            newCommand.setCrop(new ObjectId(cropId));
            newCommand.setDateCreated(new Date());
            newCommand.setStatus("PENDING");
            Command savedCommand = serviceCommand.createCommand(newCommand);
            return ResponseEntity.ok(savedCommand);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/ejecutar")
    public ResponseEntity<Command> executeCommand(@PathVariable String id) {
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
    public Command updateCommand(@PathVariable String id, @RequestBody Command updatedCommad) {
        return serviceCommand.updateCommand(id, updatedCommad);
    }

}
