package smartpot.com.api.Models.DAO.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import smartpot.com.api.Models.DAO.Repository.RCommand;
import smartpot.com.api.Models.DAO.Repository.RUser;
import smartpot.com.api.Models.Entity.Command;
import smartpot.com.api.Models.Entity.User;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Service
public class SCommands {
    @Autowired
    private RCommand repositoryCommand;

    public List<Command> getAllCommands() {
        return repositoryCommand.findAll();
    }

    public Command getCommandById(String id) {
        return repositoryCommand.findById(id).orElse(null);
    }

    public Command createCommand(Command newCommand) {
        newCommand.setDateCreated(new Date());
        newCommand.setStatus("PENDING");
        return repositoryCommand.save(newCommand);
    }

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

    public void deleteCommand(String id) {
        repositoryCommand.deleteById(id);
    }

    public List<Command> getCommandsByStatus(String status) {
        return repositoryCommand.findByStatus(status);
    }

    public List<Command> getCommandsByCropId(String cropId) {
        return repositoryCommand.findByCrop_Id(cropId);
    }
}
