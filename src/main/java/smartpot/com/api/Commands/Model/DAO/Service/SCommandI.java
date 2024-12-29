package smartpot.com.api.Commands.Model.DAO.Service;

import smartpot.com.api.Commands.Model.Entity.Command;

import java.util.List;

public interface SCommandI {
    List<Command> getAllCommands();

    Command getCommandById(String id);

    Command createCommand(Command newCommand);

    Command updateCommand(String id, Command upCommand) throws Exception;

    void deleteCommand(String id);
}
