package smartpot.com.api.Commands.Service;

import smartpot.com.api.Commands.Model.DTO.CommandDTO;

import java.util.List;

/**
 * Interface for the Command Service.
 *
 * <p>
 * Defines the contract for managing command-related operations, including
 * CRUD operations and execution logic. Implementations of this interface
 * are expected to provide the core functionality for working with commands.
 * </p>
 *
 * <h3>Responsibilities:</h3>
 * <ul>
 *     <li>Retrieve all commands from the data source.</li>
 *     <li>Retrieve a specific command by its ID.</li>
 *     <li>Create a new command with specified details.</li>
 *     <li>Update the details of an existing command.</li>
 *     <li>Delete a command by its ID.</li>
 *     <li>Execute a command and record its response.</li>
 * </ul>
 *
 * <h3>Methods:</h3>
 * <ul>
 *     <li>{@link #getAllCommands()} - Fetches all available commands.</li>
 *     <li>{@link #getCommandById(String)} - Retrieves a command by its unique ID.</li>
 *     <li>{@link #createCommand(CommandDTO)} - Creates a new command in the system.</li>
 *     <li>{@link #updateCommand(String, CommandDTO)} - Updates the details of a command.</li>
 *     <li>{@link #deleteCommand(String)} - Deletes a command by its ID.</li>
 *     <li>{@link #executeCommand(String, String)} - Executes a command and logs its response.</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <p>
 * This interface is typically implemented by a service class, such as {@code SCommand},
 * to provide the actual logic for managing commands. It is used by controllers
 * or other services to interact with command-related functionality.
 * </p>
 *
 * @see SCommand
 * @see CommandDTO
 */
public interface SCommandI {
    /**
     * Retrieves all commands available in the system.
     *
     * @return a list of {@link CommandDTO} representing all commands.
     * @throws Exception if an error occurs during retrieval or if no commands exist.
     */
    List<CommandDTO> getAllCommands() throws Exception;

    /**
     * Retrieves a specific command by its ID.
     *
     * @param id the unique identifier of the command.
     * @return the {@link CommandDTO} representing the command.
     * @throws Exception if the command does not exist or retrieval fails.
     */
    CommandDTO getCommandById(String id) throws Exception;

    /**
     * Creates a new command in the system.
     *
     * @param newCommand the {@link CommandDTO} containing the command details.
     * @return the created {@link CommandDTO}.
     */
    CommandDTO createCommand(CommandDTO newCommand);

    /**
     * Updates the details of an existing command.
     *
     * @param id            the unique identifier of the command to update.
     * @param updateCommand the {@link CommandDTO} containing updated details.
     * @return the updated {@link CommandDTO}.
     * @throws Exception if the command does not exist or the update fails.
     */
    CommandDTO updateCommand(String id, CommandDTO updateCommand) throws Exception;

    /**
     * Deletes a command by its unique ID.
     *
     * @param id the unique identifier of the command to delete.
     * @return a message confirming the deletion.
     * @throws Exception if the command does not exist or the deletion fails.
     */
    String deleteCommand(String id) throws Exception;

    /**
     * Executes a command and records its response.
     *
     * @param id       the unique identifier of the command to execute.
     * @param response the response or result of the command execution.
     * @return the updated {@link CommandDTO} reflecting the execution details.
     * @throws Exception if the command does not exist or execution fails.
     */
    CommandDTO executeCommand(String id, String response) throws Exception;
}
