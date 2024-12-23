package smartpot.com.api.Sessions.Mapper;

import org.mapstruct.Mapper;
import smartpot.com.api.Sessions.Model.DTO.SessionDTO;
import smartpot.com.api.Sessions.Model.Entity.Session;

@Mapper(componentModel="spring")
public interface MSession {
    /**
     * Convierte un SessionDTO a una entidad Session.
     *
     * @param sessionDTO El DTO de la session.
     * @return La entidad Session.
     */
    public Session toEntity(SessionDTO sessionDTO);

    /**
     * Convierte una entidad User a un UserDTO.
     *
     * @param session La entidad session.
     * @return El DTO de la Session.
     */
    public SessionDTO toDTO(Session session);
}
