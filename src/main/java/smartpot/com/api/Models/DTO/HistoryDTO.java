package smartpot.com.api.Models.DTO;

import lombok.Data;

@Data
public class HistoryDTO {
    private String id;
    private String date;
    private MeasuresDTO measures;
    private String crop;

    @Data
    public static class MeasuresDTO {
        private Double atmosphere;
        private Double brightness;
        private Double temperature;
        private Double ph;
        private Double tds;
        private Double humidity;
    }
}
