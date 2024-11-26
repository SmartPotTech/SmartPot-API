package smartpot.com.api.Models.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import smartpot.com.api.Models.Entity.Crop;
import smartpot.com.api.Models.Entity.History;

@Data
public class CropRecordDTO {
    private String cropId;
    private String status;
    private String type;
    private String date;
    private MeasuresDTO measures;

    public CropRecordDTO(Crop crop, History history) {
        this.cropId = String.valueOf(crop.getId());
        this.status = String.valueOf(crop.getStatus());
        this.type = String.valueOf(crop.getType());
        this.date = String.valueOf(history.getDate());
        this.measures = new MeasuresDTO(history.getMeasures());
    }
}
