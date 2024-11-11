package smartpot.com.api.Models.DAO.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import smartpot.com.api.Exception.ApiException;
import smartpot.com.api.Exception.ApiResponse;
import smartpot.com.api.Models.DAO.Repository.RHistory;
import smartpot.com.api.Models.DTO.HistoryDTO;
import smartpot.com.api.Models.Entity.History;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Service
public class SHistory {

    @Autowired
    private RHistory repositoryHistory;
    @Autowired
    private SCrop serviceCrop;

    /**
     * Obtiene todos los históricos almacenados en el sistema.
     * Si no se encuentran históricos, lanza una excepción ApiException.
     *
     * @return Lista de todos los históricos existentes
     */
    public List<History> getAllHistorys() {
        List<History> historys = repositoryHistory.findAll();
        if (historys == null || historys.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontro ninguna History en la db",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return historys;
    }

    public List<History> getByCrop(String cropId) {

        if (!ObjectId.isValid(cropId)) {
            throw new ApiException(new ApiResponse(
                    "The crop id isn't valid",
                    HttpStatus.BAD_REQUEST.value()

            ));
        }

        return repositoryHistory.getHistoriesByCrop(new ObjectId(cropId));


    }

    /**
     * Crea un nuevo histórico.
     * Valida que los datos del histórico sean correctos y luego lo guarda en la base de datos.
     *
     * @param historyDto Datos del nuevo histórico a crear
     * @return El histórico creado
     */
    public History Createhistory(HistoryDTO historyDto) {
        History history = historyDto_tohistory(new History(), historyDto);
        validateHistory(history);
        return repositoryHistory.save(history);
    }

    /**
     * Actualiza un histórico existente.
     * Verifica que el cultivo asociado al histórico exista, luego actualiza los datos del histórico y lo guarda.
     *
     * @param id         Identificador del histórico a actualizar
     * @param historyDto Datos actualizados del histórico
     * @return El histórico actualizado
     */
    public History updatedHistory(String id, HistoryDTO historyDto) {
        serviceCrop.getCropById(historyDto.getCrop());
        History updatedHistory = historyDto_tohistory(getHistoryById(id), historyDto);
        validateHistory(updatedHistory);
        return repositoryHistory.save(updatedHistory);
    }

    /**
     * Convierte un HistoryDTO a un objeto History.
     * Copia los datos del HistoryDTO al objeto History.
     *
     * @param history    Objeto History a actualizar
     * @param historydto Datos del HistoryDTO
     * @return El objeto History actualizado
     */
    private History historyDto_tohistory(History history, HistoryDTO historydto) {
        history.setDate(parseDate(historydto.getDate()));
        history.setMeasures(measurestoMeasures(historydto.getMeasures()));
        history.setCrop(new ObjectId(historydto.getCrop()));
        return history;
    }

    /**
     * Convierte un HistoryDTO.MeasuresDTO a un objeto History.Measures.
     * Copia los datos del HistoryDTO.MeasuresDTO al objeto History.Measures.
     *
     * @param measuresDTO Datos del HistoryDTO.MeasuresDTO
     * @return El objeto History.Measures
     */
    private History.Measures measurestoMeasures(HistoryDTO.MeasuresDTO measuresDTO) {
        History.Measures measures = new History.Measures();
        measures.setPh(measuresDTO.getPh());
        measures.setBrightness(measuresDTO.getBrightness());
        measures.setTds(measuresDTO.getTds());
        measures.setHumidity(measuresDTO.getHumidity());
        measures.setAtmosphere(measuresDTO.getAtmosphere());
        measures.setTemperature(measuresDTO.getTemperature());
        return measures;
    }

    /**
     * Parsea una fecha en formato String a un objeto Date.
     * Si la cadena de fecha está vacía, se devuelve la fecha actual.
     *
     * @param dateString Cadena de fecha en formato "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
     * @return Objeto Date
     */
    private Date parseDate(String dateString) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        try {
            if (dateString.equals(" ")) {
                return new Date();
            }
            return dateFormat.parse(dateString);
        } catch (ParseException e) {
            throw new IllegalArgumentException("La fecha proporcionada no tiene un formato válido.", e);
        }
    }

    /**
     * Valida que los datos del histórico sean correctos.
     * Verifica que la fecha del histórico sea anterior a la fecha actual y que los valores de las métricas estén dentro de los rangos esperados.
     *
     * @param history Objeto History a validar
     */
    private void validateHistory(History history) {
        Date currentDate = new Date();
        if (!(history.getDate().compareTo(currentDate) <= 0)) {
            throw new ApiException(new ApiResponse(
                    "La fecha '" + history.getDate() + "' no es válido.",
                    HttpStatus.BAD_REQUEST.value()));
        }
        History.Measures measures = history.getMeasures();
        // Validar rangos estándar
        if (measures.getAtmosphere() < 0 || measures.getAtmosphere() > 1100) {
            throw new ApiException(new ApiResponse(
                    "La atmósfera debe estar entre 0 y 1100 hPa.",
                    HttpStatus.BAD_REQUEST.value()));
        }
        // Validaciones similares para otros campos de métricas
    }

    /**
     * Obtiene un histórico por su identificador único.
     * Verifica que el ID sea válido y lanza una excepción si no se encuentra el histórico.
     *
     * @param id Identificador del histórico
     * @return El histórico encontrado
     */
    public History getHistoryById(String id) {
        if (!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "El id '" + id + "' no es válido. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return repositoryHistory.findById(new ObjectId(id))
                .orElseThrow(() -> new ApiException(
                        new ApiResponse("El History con id '" + id + "' no fue encontrado.",
                                HttpStatus.NOT_FOUND.value())
                ));
    }

    /**
     * Elimina un histórico existente.
     * Intenta eliminar el histórico por su ID y devuelve una respuesta con un mensaje de éxito o error.
     *
     * @param existingHistory El histórico a eliminar
     * @return Respuesta HTTP con un mensaje de éxito o error
     */
    public ResponseEntity<ApiResponse> deleteHistory(History existingHistory) {
        try {
            repositoryHistory.deleteById(existingHistory.getId());
            return ResponseEntity.status(HttpStatus.OK.value()).body(
                    new ApiResponse("El History con ID '" + existingHistory.getId() + "' fue eliminado.",
                            HttpStatus.OK.value())
            );
        } catch (Exception e) {
            log.error("e: ", e);
            throw new ApiException(
                    new ApiResponse("No se pudo eliminar el History con ID '" + existingHistory.getId() + "'.",
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}
