package smartpot.com.api.Models.Entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "comandos")
public class Command {

    @Id
    @Field("id")
    private String id;

    @Field("commandType")
    private String commandType;

    @Field("status")
    private String status;

    @NotNull(message = "La fecha de creación no puede estar vacía")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    @Field("dateCreated")
    private Date dateCreated;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    @Field("dateExecuted")
    private Date dateExecuted;

    @Field("parameters")
    private Map<String, String> parameters;

    @Field("response")
    private String response;

    @DBRef
    @NotNull(message = "El comando debe ejecutarse en un cultivo")
    @Field("crop")
    private Crop crop;
}

/*
 * TODO: Clase Command
 *
 * Descripción: Esta clase representa un comando que puede ser ejecutado en un cultivo específico.
 *
 * Campos:
 * - id: Identificador único del comando.
 * - commandType: Tipo de comando (ej. "ACTIVATE_WATER_PUMP", "TURN_ON_UV_LIGHT").
 * - status: Estado del comando (ej. "PENDING", "EXECUTED", "FAILED").
 * - dateCreated: Fecha de creación del comando (debe ser proporcionada, no puede ser nula).
 * - dateExecuted: Fecha en la que se ejecutó el comando (opcional, puede ser nula si no se ha ejecutado).
 * - parameters: Mapa de parámetros adicionales del comando (ej. duración, intensidad).
 * - response: Respuesta después de la ejecución (ej. éxito, error).
 * - crop: Referencia al cultivo asociado (debe ser proporcionado, no puede ser nulo).
 */