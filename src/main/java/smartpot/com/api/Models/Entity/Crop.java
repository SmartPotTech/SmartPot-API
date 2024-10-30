package smartpot.com.api.Models.Entity;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cultivos")
public class Crop implements Serializable {
    @Id
    private String id;

    private String status;

    @NotEmpty(message = "El tipo no puede estar vacío")
    private String type;

    private String user;

    private String cultivation;
}
