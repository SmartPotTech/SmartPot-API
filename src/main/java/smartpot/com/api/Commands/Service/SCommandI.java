package smartpot.com.api.Commands.Service;

import smartpot.com.api.Commands.Model.DTO.CommandDTO;
import smartpot.com.api.Commands.Model.Entity.Command;

import java.util.List;

public interface SCommandI {
    List<Command> getAllCommands();

    Command getCommandById(String id);

    CommandDTO createCommand(CommandDTO newCommand);

    Command updateCommand(String id, Command upCommand) throws Exception;

    void deleteCommand(String id);
}
