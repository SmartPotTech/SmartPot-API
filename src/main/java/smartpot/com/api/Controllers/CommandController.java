package smartpot.com.api.Controllers;

import smartpot.com.api.Models.DAO.RCommand;
import smartpot.com.api.Models.DAO.RCrop;
import smartpot.com.api.Models.Entity.Command;
import smartpot.com.api.Models.Entity.Crop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/Comandos")
public class CommandController {

    @Autowired
    private RCommand repositoryCommand;

    @Autowired
    private RCrop repositoryCrop;

    @GetMapping
    public List<Command> getAllCommands() {
        return repositoryCommand.findAll();
    }

    @GetMapping("/crop/{cropId}")
    public List<Command> getCommandsByCrop(@PathVariable String cropId) {
        return repositoryCommand.findAll().stream()
                .filter(command -> command.getCrop() != null && cropId.equals(command.getCrop().getId()))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Command> getCommand(@PathVariable String id) {
        return repositoryCommand.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Comando con id " + id + " no encontrado"));
    }

    @PostMapping("/crop/{cropId}")
    public Command createCommand(@PathVariable String cropId, @RequestBody Command newCommand) {
        Optional<Crop> crop = repositoryCrop.findById(cropId);
        if (crop.isPresent()) {
            newCommand.setCrop(crop.get());
            newCommand.setDateCreated(new Date());
            newCommand.setStatus("PENDING");
            return repositoryCommand.save(newCommand);
        } else {
            throw new RuntimeException("Jardín con id " + cropId + " no encontrado");
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
                .orElseThrow(() -> new RuntimeException("Comando con id " + id + " no encontrado"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCommand(@PathVariable String id) {
        return repositoryCommand.findById(id)
                .map(command -> {
                    repositoryCommand.delete(command);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElseThrow(() -> new RuntimeException("Comando con id " + id + " no encontrado"));
    }
}
