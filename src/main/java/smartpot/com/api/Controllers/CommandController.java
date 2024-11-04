package smartpot.com.api.Controllers;

import org.bson.types.ObjectId;
import smartpot.com.api.Models.DAO.Repository.RCommand;
import smartpot.com.api.Models.DAO.Repository.RCrop;
import smartpot.com.api.Models.Entity.Command;
import smartpot.com.api.Models.Entity.Crop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Optional;

//@RestController
//@RequestMapping("/Comandos")
public class CommandController {
    /*
    private final RCommand repositoryCommand;
    private final RCrop repositoryCrop;

    @Autowired
    public CommandController(RCommand repositoryCommand, RCrop repositoryCrop) {
        this.repositoryCommand = repositoryCommand;
        this.repositoryCrop = repositoryCrop;
    }

    @GetMapping
    public List<Command> getAllCommands() {
        return repositoryCommand.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Command> getCommand(@PathVariable String id) {
        return repositoryCommand.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/crop/{cropId}")
    public ResponseEntity<Command> createCommand(@PathVariable String cropId, @RequestBody Command newCommand) {
        Optional<Crop> crop = repositoryCrop.findById(cropId);
        if (crop.isPresent()) {
            newCommand.setCrop(new ObjectId(cropId));
            newCommand.setDateCreated(new Date());
            newCommand.setStatus("PENDING");
            Command savedCommand = repositoryCommand.save(newCommand);
            return ResponseEntity.ok(savedCommand);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/ejecutar")
    public ResponseEntity<Command> executeCommand(@PathVariable String id, @RequestBody Command commandDetails) {
        return repositoryCommand.findById(id)
                .map(command -> {
                    command.setStatus("EXECUTED");
                    command.setDateExecuted(new Date());
                    command.setResponse(commandDetails.getResponse());
                    Command updatedCommand = repositoryCommand.save(command);
                    return ResponseEntity.ok(updatedCommand);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteCommand(@PathVariable String id) {
        return repositoryCommand.findById(id)
                .map(command -> {
                    repositoryCommand.delete(command);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

     */
}
