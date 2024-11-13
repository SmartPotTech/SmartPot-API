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
import smartpot.com.api.Models.Entity.Crop;
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

    //Validations

    private void ValidationMesuares(HistoryDTO.MeasuresDTO measures) {
        // Validación y conversión de "atmosphere"
        try {
            Double atmosphere = Double.parseDouble(measures.getAtmosphere());
            if (atmosphere <= 0) {
                throw new ApiException(new ApiResponse("La atmósfera debe ser un valor positivo", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("La atmósfera debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }

        // Validación y conversión de "brightness"
        try {
            Double brightness = Double.parseDouble(measures.getBrightness());
            if (brightness <= 0) {
                throw new ApiException(new ApiResponse("El brillo debe ser un valor positivo", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("El brillo debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }

        // Validación y conversión de "temperature"
        try {
            Double temperature = Double.parseDouble(measures.getTemperature());
            if (temperature <= 0) {
                throw new ApiException(new ApiResponse("La temperatura debe ser un valor positivo", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("La temperatura debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }

        // Validación y conversión de "ph"
        try {
            Double ph = Double.parseDouble(measures.getPh());
            if (ph <= 0) {
                throw new ApiException(new ApiResponse("El pH debe ser un valor positivo", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("El pH debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }

        // Validación y conversión de "tds"
        try {
            Double tds = Double.parseDouble(measures.getTds());
            if (tds <= 0) {
                throw new ApiException(new ApiResponse("El TDS debe ser un valor positivo", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("El TDS debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }

        // Validación y conversión de "humidity"
        try {
            Double humidity = Double.parseDouble(measures.getHumidity());
            if (humidity <= 0) {
                throw new ApiException(new ApiResponse("La humedad debe ser un valor positivo", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("La humedad debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }
    }


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
                    "No se encontro ningun registro en el historial",
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
     * @param historyDTO Datos del nuevo histórico a crear
     * @return El histórico creado
     */
    public History Createhistory(HistoryDTO historyDTO) {
        ValidationMesuares(historyDTO.getMeasures());
        Crop crop = serviceCrop.getCropById(historyDTO.getCrop());
        History history = new History(historyDTO);
        return repositoryHistory.save(history);
    }

    /**
     * Actualiza un histórico existente.
     * Verifica que el cultivo asociado al histórico exista, luego actualiza los datos del histórico y lo guarda.
     *
     * @param existingHistory Historial para actualizar
     * @param updateHistory Datos a actualizar  en el historial
     * @return El histórico actualizado
     */
    public History updatedHistory(History existingHistory, HistoryDTO updateHistory) {
        if (updateHistory.getMeasures()!=null){
            HistoryDTO.MeasuresDTO measures = updateHistory.getMeasures();
            ValidationMesuares(measures);
            existingHistory.setMeasures(new History.Measures(measures));
        }

        if(updateHistory.getCrop()!=null){
            String cropId = updateHistory.getCrop();
            Crop crop = serviceCrop.getCropById(cropId);
            existingHistory.setCrop(crop.getId());
        }
        try {
            return repositoryHistory.save(existingHistory);
        } catch (Exception e) {
            log.error("e: ", e);
            throw new ApiException(
                    new ApiResponse("No se pudo actualizar el registro con ID '" + existingHistory.getId() + "'.",
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
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
