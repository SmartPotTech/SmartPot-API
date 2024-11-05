package smartpot.com.api.Controllers;

import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import smartpot.com.api.Models.DAO.Service.SCommand;
import smartpot.com.api.Models.DAO.Service.SCrop;
import smartpot.com.api.Models.Entity.Command;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import smartpot.com.api.Models.Entity.Crop;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/Comandos")
public class CommandController {

    @Autowired
    private SCommand sCommand;

    private final SCrop sCrop;

    public CommandController(SCrop sCrop) {
        this.sCrop = sCrop;
    }


    @GetMapping("/All")
    public List<Command> getAllCommand() {
        return sCommand.getAllCommands();
    }

    @GetMapping("/id/{id}")
    public Command getUserById(@PathVariable String id) {
        return sCommand.getCommandById(id);
    }

    @PostMapping("/Create")
    @ResponseStatus(HttpStatus.CREATED)
    public Command createCommand(@RequestBody Command newCommand) {
        return sCommand.createCommand(newCommand);
    }

    @PostMapping("/crop/{cropId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Command> createCommand(@PathVariable String cropId, @RequestBody Command newCommand) {
        Optional<Crop> crop = Optional.ofNullable(sCrop.getCropById(cropId));
        if (crop.isPresent()) {
            newCommand.setCrop(new ObjectId(cropId));
            newCommand.setDateCreated(new Date());
            newCommand.setStatus("PENDING");
            Command savedCommand = sCommand.createCommand(newCommand);
            return ResponseEntity.ok(savedCommand);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/ejecutar")
    public ResponseEntity<Command> executeCommand(@PathVariable String id, @RequestBody Command commandDetails) {
        Command command = sCommand.getCommandById(id);
        if (command != null) {
            command.setStatus("EXECUTED");
            command.setDateExecuted(new Date());
            command.setResponse(commandDetails.getResponse());
            Command updatedCommand = sCommand.updateCommand(command);
            return ResponseEntity.ok(updatedCommand);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteCommand(@PathVariable String id) {

        if(sCommand.getCommandById(id) != null) {
            sCommand.deleteCommand(id);
            return ResponseEntity.ok().build();

        }else{
            return ResponseEntity.notFound().build();
        }

    }


}
