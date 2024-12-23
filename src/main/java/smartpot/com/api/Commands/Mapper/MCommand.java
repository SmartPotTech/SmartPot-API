package smartpot.com.api.Commands.Mapper;

import org.mapstruct.Mapper;
import smartpot.com.api.Commands.Model.DTO.CommandDTO;
import smartpot.com.api.Commands.Model.Entity.Command;

@Mapper(componentModel="spring")
public interface MCommand {
    public Command toEntity(CommandDTO commandDTO);
    public CommandDTO toDTO(Command command);
}
