package smartpot.com.api.Security.Mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.mapstruct.Mapper;

import smartpot.com.api.Security.Model.DTO.ResetTokenDTO;

@Mapper(componentModel = "spring")
public interface MResetToken {

    //TODO: Explicit Exception handling

    @SneakyThrows
    default String convertToJson(ResetTokenDTO resetToken) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(resetToken);
    }

    @SneakyThrows
    default ResetTokenDTO convertToDTO(String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(json, ResetTokenDTO.class);
    }
}
