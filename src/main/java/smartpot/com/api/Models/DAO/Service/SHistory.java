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

    /**
     * Valida las medidas proporcionadas en el objeto `MeasuresDTO`.
     * Llama a las funciones de validación específicas para cada tipo de medida.
     *
     * @param measures Objeto que contiene las medidas a validar.
     */
    private void ValidationMesuares(HistoryDTO.MeasuresDTO measures) {
        validateAtmosphere(measures.getAtmosphere());
        validateBrightness(measures.getBrightness());
        validateTemperature(measures.getTemperature());
        validatePh(measures.getPh());
        validateTds(measures.getTds());
        validateHumidity(measures.getHumidity());
    }

    /**
     * Valida el valor de la atmósfera (temperatura).
     * La temperatura debe ser mayor que -40°C y menor o igual a 80°C.
     *
     * @param atmosphereValue Valor de la atmósfera (temperatura) a validar.
     * @throws ApiException Si el valor no es válido o está fuera de rango.
     */
    private void validateAtmosphere(String atmosphereValue) {
        try {
            Double atmosphere = Double.parseDouble(atmosphereValue);
            if (atmosphere <= 0) {
                throw new ApiException(new ApiResponse("La atmósfera debe ser un valor positivo", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("La atmósfera debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }
    }

    /**
     * Valida el valor del brillo (luz).
     * El brillo debe estar dentro del rango [0, 1000].
     *
     * @param brightnessValue Valor del brillo a validar.
     * @throws ApiException Si el valor no es válido o está fuera de rango.
     */
    private void validateBrightness(String brightnessValue) {
        try {
            Double brightness = Double.parseDouble(brightnessValue);
            if (brightness < 0 || brightness > 1000) {
                throw new ApiException(new ApiResponse("El brillo debe estar entre 0 y 1000", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("El brillo debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }
    }

    /**
     * Valida el valor de la temperatura.
     * La temperatura debe estar dentro del rango [-40°C, 80°C].
     *
     * @param temperatureValue Valor de la temperatura a validar.
     * @throws ApiException Si el valor no es válido o está fuera de rango.
     */
    private void validateTemperature(String temperatureValue) {
        try {
            Double temperature = Double.parseDouble(temperatureValue);
            if (temperature < -40 || temperature > 80) {
                throw new ApiException(new ApiResponse("La temperatura debe estar entre -40°C y 80°C", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("La temperatura debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }
    }

    /**
     * Valida el valor del pH.
     * El pH debe estar dentro del rango [0, 14].
     *
     * @param phValue Valor del pH a validar.
     * @throws ApiException Si el valor no es válido o está fuera de rango.
     */
    private void validatePh(String phValue) {
        try {
            Double ph = Double.parseDouble(phValue);
            if (ph < 0 || ph > 14) {
                throw new ApiException(new ApiResponse("El pH debe estar entre 0 y 14", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("El pH debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }
    }

    /**
     * Valida el valor de TDS (Total Dissolved Solids).
     * El TDS debe estar dentro del rango [0, 1000 ppm].
     *
     * @param tdsValue Valor de TDS a validar.
     * @throws ApiException Si el valor no es válido o está fuera de rango.
     */
    private void validateTds(String tdsValue) {
        try {
            Double tds = Double.parseDouble(tdsValue);
            if (tds < 0 || tds > 1000) {
                throw new ApiException(new ApiResponse("El TDS debe estar entre 0 y 1000 ppm", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("El TDS debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }
    }

    /**
     * Valida el valor de la humedad.
     * La humedad debe estar dentro del rango [0%, 100%].
     *
     * @param humidityValue Valor de la humedad a validar.
     * @throws ApiException Si el valor no es válido o está fuera de rango.
     */
    private void validateHumidity(String humidityValue) {
        try {
            Double humidity = Double.parseDouble(humidityValue);
            if (humidity < 0 || humidity > 100) {
                throw new ApiException(new ApiResponse("La humedad debe estar entre 0% y 100%", HttpStatus.BAD_REQUEST.value()));
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
