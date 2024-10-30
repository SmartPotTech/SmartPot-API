package smartpot.com.api.Models.Entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "comandos")
public class Command {

    @Id
    private String id;
    private String commandType;       // Tipo de comando (ej. "ACTIVATE_WATER_PUMP", "TURN_ON_UV_LIGHT")
    private String status;            // Estado del comando (ej. "PENDING", "EXECUTED", "FAILED")
    private Date dateCreated;         // Fecha de creación del comando
    private Date dateExecuted;        // Fecha en la que se ejecutó el comando (si corresponde)
    private Map<String, String> parameters;  // Parámetros adicionales del comando (ej. duración, intensidad)
    private String response;          // Respuesta después de la ejecución (ej. éxito, error)

    @DBRef
    private Crop crop;
}
