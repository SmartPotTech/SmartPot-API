package smartpot.com.api.Records.Mapper;

import org.mapstruct.Mapper;
import smartpot.com.api.Records.Model.DTO.RecordDTO;

@Mapper(componentModel="spring")
public interface MRecords {
    /**
     * Convierte un RecordDto a una entidad User.
     *
     * @param recordDTO El DTO del usuario.
     * @return La entidad Registro.
     */
    public Record toEntity(RecordDTO recordDTO);

    /**
     * Convierte una entidad Record a un RecordDTO.
     *
     * @param record La entidad Registro.
     * @return El DTO del Registro.
     */
    public RecordDTO toDTO(Record record);
}
