package smartpot.com.api.Commands.Service;

import smartpot.com.api.Commands.Model.DTO.CommandDTO;
import smartpot.com.api.Commands.Model.Entity.Command;

import java.util.List;

public interface SCommandI {
    List<Command> getAllCommands();

    CommandDTO getCommandById(String id) throws Exception;

    CommandDTO createCommand(CommandDTO newCommand);

    Command updateCommand(String id, Command upCommand) throws Exception;

    String deleteCommand(String id) throws Exception;

    CommandDTO excuteCommand(String id, String reponse) throws Exception;
}
