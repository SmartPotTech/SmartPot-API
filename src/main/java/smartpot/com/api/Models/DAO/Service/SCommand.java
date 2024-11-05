package smartpot.com.api.Models.DAO.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import smartpot.com.api.Models.DAO.Repository.RCommand;
import smartpot.com.api.Models.Entity.Command;

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

    public Command updateCommand(Command newCommand) {
        return repositoryCommand.save(newCommand);
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
