package smartpot.com.api.Records.Model.DAO.Service;

import org.springframework.http.ResponseEntity;
import smartpot.com.api.Exception.ApiResponse;
import smartpot.com.api.Records.Model.DTO.CropRecordDTO;
import smartpot.com.api.Records.Model.DTO.RecordDTO;
import smartpot.com.api.Records.Model.Entity.DateRange;
import smartpot.com.api.Records.Model.Entity.History;

import java.util.List;

public interface SHistoryI {
    List<History> getAllHistories();

    History getHistoryById(String id);

    List<History> getByCrop(String cropId) throws Exception;

    List<History> getHistoriesByCropAndDateBetween(String cropId, DateRange ranges);

    List<CropRecordDTO> getByUser(String id) throws Exception;

    History Createhistory(RecordDTO recordDTO) throws Exception;

    History updatedHistory(History existingHistory, RecordDTO updateHistory);

    ResponseEntity<ApiResponse> deleteHistory(History existingHistory);
}
