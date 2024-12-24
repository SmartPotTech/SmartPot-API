package smartpot.com.api.Records.Model.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MeasuresDTO {
    private String atmosphere;
    private String brightness;
    private String temperature;
    private String ph;
    private String tds;
    private String humidity;
}
