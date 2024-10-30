package smartpot.com.api.Models.Entity;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sesiones")
public class Session implements Serializable {
    @Id
    private String id;

    @NotNull(message = "La fecha de registro no puede estar vacía")
    private Date registration;

    private String user;
}
