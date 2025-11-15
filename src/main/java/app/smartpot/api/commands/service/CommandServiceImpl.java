package app.smartpot.api.commands.service;

import app.smartpot.api.commands.mapper.CommandMapper;
import app.smartpot.api.commands.repository.CommandRepository;
import lombok.Builder;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.smartpot.api.commands.model.dto.CommandDTO;
import app.smartpot.api.crops.service.CropService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service implementation for managing commands.
 *
 * <p>
 * This class provides the business logic for managing commands, including
 * CRUD operations and execution logic. It interacts with the repository,
 * a mapper, and additional services to fulfill its responsibilities.
 * </p>
 *
 * <h3>Annotations:</h3>
 * <ul>
 *     <li>{@code @Data} - Generates boilerplate code such as getters, setters, and toString.</li>
 *     <li>{@code @Builder} - Enables the builder pattern for constructing instances of this class.</li>
 *     <li>{@code @Service} - Marks this class as a Spring-managed service component.</li>
 * </ul>
 *
 * <h3>Dependencies:</h3>
 * <ul>
 *     <li>{@code RCommand} - Repository for accessing and persisting command entities.</li>
 *     <li>{@code SCropI} - Service for managing crops, used for operations involving crop data.</li>
 *     <li>{@code MCommand} - Mapper for converting between entity and DTO representations of commands.</li>
 * </ul>
 *
 * <h3>Responsibilities:</h3>
 * <ul>
 *     <li>Provides methods for creating, retrieving, updating, and deleting commands.</li>
 *     <li>Implements caching for frequently accessed commands to improve performance.</li>
 *     <li>Validates and processes business logic for commands before interacting with the repository.</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <p>
 * This service is typically used by controllers to handle HTTP requests related to commands,
 * or by other services that depend on command-related operations.
 * </p>
 *
 * @see CommandService
 * @see CommandRepository
 * @see CropService
 * @see CommandMapper
 */
@Data
@Builder
@Service
public class CommandServiceImpl implements CommandService {

    private final CommandRepository repositoryCommand;
    private final CropService serviceCrop;
    private final CommandMapper mapperCommand;

    /**
     * Constructs an instance of {@code SCommand} with the required dependencies.
     *
     * @param repositoryCommand the repository for command-related database operations
     * @param serviceCrop       the service responsible for crop-related logic
     * @param mapperCommand     the mapper for converting entities to DTOs and vice versa
     */
    @Autowired
    public CommandServiceImpl(CommandRepository repositoryCommand, CropService serviceCrop, CommandMapper mapperCommand) {
        this.repositoryCommand = repositoryCommand;
        this.serviceCrop = serviceCrop;
        this.mapperCommand = mapperCommand;
    }

    /**
     * Retrieves all commands from the repository and maps them to DTOs.
     * <p>
     * This method is cached to improve performance when retrieving the list of commands.
     * The cache is identified by the key 'all_commands' under the 'commands' cache.
     * </p>
     *
     * @return a list of {@code CommandDTO} objects representing all commands
     * @throws Exception if no commands exist in the repository
     */
    @Override
    @Cacheable(value = "commands", key = "'all_commands'")
    public List<CommandDTO> getAllCommands() throws Exception {
        return Optional.of(repositoryCommand.findAll())
                .filter(commands -> !commands.isEmpty())
                .map(crops -> crops.stream()
                        .map(mapperCommand::toDTO)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new Exception("No existe ningún comando"));
    }

    /**
     * Retrieves a command by its unique identifier and maps it to a DTO.
     * <p>
     * This method is cached to improve performance for retrieving individual commands.
     * The cache is identified by the key pattern 'id_{id}' under the 'commands' cache.
     * </p>
     *
     * @param id the unique identifier of the command to retrieve
     * @return a {@code CommandDTO} representing the command with the specified ID
     * @throws Exception if the command with the specified ID does not exist
     */
    @Override
    @Cacheable(value = "commands", key = "'id_'+#id")
    public CommandDTO getCommandById(String id) throws Exception {
        return Optional.of(id)
                .map(ObjectId::new)
                .map(repositoryCommand::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(mapperCommand::toDTO)
                .orElseThrow(() -> new Exception("El Comando no existe"));
    }

    /**
     * Creates a new command based on the provided DTO, sets its creation date and status,
     * and persists it in the repository.
     *
     * <p>
     * This method assigns a default status of "PENDING" and records the current timestamp
     * as the creation date in the format "yyyy-MM-dd HH:mm:ss".
     * </p>
     *
     * @param commandDTO the {@code CommandDTO} containing the details of the command to create
     * @return a {@code CommandDTO} representing the created command
     * @throws IllegalStateException if a command with the same details already exists
     */
    @Override
    @Transactional
    public CommandDTO createCommand(CommandDTO commandDTO) throws IllegalStateException {
        return Optional.of(commandDTO)
                .map(dto -> {
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    dto.setDateCreated(formatter.format(new Date()));
                    dto.setStatus("PENDING");
                    return dto;
                })
                .map(mapperCommand::toEntity)
                .map(repositoryCommand::save)
                .map(mapperCommand::toDTO)
                .orElseThrow(() -> new IllegalStateException("El Comando ya existe"));
    }

    /**
     * Executes a command by updating its status to "EXECUTED" and setting a response message.
     *
     * <p>
     * This method updates the specified command with the current timestamp, sets its status to
     * "EXECUTED", and records the provided response message.
     * </p>
     *
     * @param id       the ID of the command to execute
     * @param response the response message to associate with the executed command
     * @return a {@code CommandDTO} representing the updated command
     * @throws Exception if the command cannot be found or updated
     */
    @Override
    @CachePut(value = "commands", key = "'id:'+#id")
    public CommandDTO executeCommand(String id, String response) throws Exception {
        return Optional.of(getCommandById(id))
                .map(commandDTO -> {
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    commandDTO.setDateCreated(formatter.format(new Date()));
                    commandDTO.setStatus("EXECUTED");
                    commandDTO.setResponse(response);
                    return commandDTO;
                })
                .map(mapperCommand::toEntity)
                .map(repositoryCommand::save)
                .map(mapperCommand::toDTO)
                .orElseThrow(() -> new Exception("El Comando no se pudo actualizar"));
    }

    /**
     * Updates the specified command with new details provided in the update DTO.
     *
     * <p>
     * This method updates fields in the command only if the corresponding fields in the
     * update DTO are non-null. The existing values are retained for fields that are null in the update DTO.
     * </p>
     *
     * @param id            the ID of the command to update
     * @param updateCommand the {@code CommandDTO} containing the updated details
     * @return a {@code CommandDTO} representing the updated command
     * @throws Exception if the command cannot be found or updated
     */
    @Override
    @Transactional
    @CachePut(value = "commands", key = "'id_'+#id")
    public CommandDTO updateCommand(String id, CommandDTO updateCommand) throws Exception {
        CommandDTO existingCommand = getCommandById(id);
        return Optional.of(updateCommand)
                .map(dto -> {
                    existingCommand.setId(dto.getCommandType() != null ? dto.getCommandType() : existingCommand.getCommandType());
                    existingCommand.setResponse(dto.getResponse() != null ? dto.getResponse() : existingCommand.getResponse());
                    existingCommand.setCrop(dto.getCrop() != null ? dto.getCrop() : existingCommand.getCrop());
                    return existingCommand;
                })
                .map(mapperCommand::toEntity)
                .map(repositoryCommand::save)
                .map(mapperCommand::toDTO)
                .orElseThrow(() -> new Exception("El Comando no se pudo actualizar"));
    }

    /**
     * Deletes the specified command by its ID.
     *
     * <p>
     * This method removes the command from the repository and evicts the corresponding cache entry.
     * </p>
     *
     * @param id the ID of the command to delete
     * @return a confirmation message indicating the successful deletion
     * @throws Exception if the command cannot be found
     */
    @Override
    @Transactional
    @CacheEvict(value = "commands", key = "'id_'+#id")
    public String deleteCommand(String id) throws Exception {
        return Optional.of(getCommandById(id))
                .map(command -> {
                    repositoryCommand.deleteById(new ObjectId(command.getId()));
                    return "El Comando con ID '" + id + "' fue eliminado.";
                })
                .orElseThrow(() -> new Exception("El Comando no existe."));
    }


}
