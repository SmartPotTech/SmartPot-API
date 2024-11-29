package smartpot.com.api.Models.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import smartpot.com.api.Models.Entity.History;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MeasuresDTO {
    private String atmosphere;
    private String brightness;
    private String temperature;
    private String ph;
    private String tds;
    private String humidity;

    public MeasuresDTO(History.Measures measures) {
        this.atmosphere = measures.getAtmosphere().toString();
        this.brightness = measures.getBrightness().toString();
        this.temperature = measures.getTemperature().toString();
        this.ph = measures.getPh().toString();
        this.tds = measures.getTds().toString();
        this.humidity = measures.getHumidity().toString();
    }
}
