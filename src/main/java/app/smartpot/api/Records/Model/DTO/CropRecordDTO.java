package app.smartpot.api.records.model.dto;

import lombok.Data;
import app.smartpot.api.crops.model.DTO.CropDTO;
import app.smartpot.api.records.model.entity.History;
import app.smartpot.api.records.model.entity.Measures;

@Data
public class CropRecordDTO {
    private String id;
    private String crop;
    private String status;
    private String type;
    private String date;
    private MeasuresDTO measures;

    public CropRecordDTO(CropDTO crop, History history) {
        this.crop = String.valueOf(crop.getId());
        this.status = String.valueOf(crop.getStatus());
        this.type = String.valueOf(crop.getType());
        this.date = String.valueOf(history.getDate());
        Measures historyMeasures = history.getMeasures();
        this.measures = new MeasuresDTO(historyMeasures.getAtmosphere().toString(), historyMeasures.getBrightness().toString(),
                historyMeasures.getTemperature().toString(), historyMeasures.getPh().toString(), historyMeasures.getTds().toString(),
                historyMeasures.getHumidity().toString());
    }
}
