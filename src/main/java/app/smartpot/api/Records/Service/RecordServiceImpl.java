package app.smartpot.api.records.service;

import app.smartpot.api.crops.model.dto.CropDTO;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.exception.ApiResponse;
import app.smartpot.api.records.mapper.RecordMapper;
import app.smartpot.api.records.model.dto.CropRecordDTO;
import app.smartpot.api.records.model.dto.MeasuresDTO;
import app.smartpot.api.records.model.dto.RecordDTO;
import app.smartpot.api.records.model.entity.DateRange;
import app.smartpot.api.records.model.entity.History;
import app.smartpot.api.records.repository.RecordRepository;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Data
@Builder
@Service
public class RecordServiceImpl implements RecordService {

    private final RecordRepository repositoryHistory;
    private final CropService serviceCrop;

    @Autowired
    public RecordServiceImpl(RecordRepository repositoryHistory, CropService serviceCrop) {
        this.repositoryHistory = repositoryHistory;
        this.serviceCrop = serviceCrop;
    }

    //Validations

    /**
     * Válida las medidas proporcionadas en el objeto `MeasuresDTO`.
     * Llama a las funciones de validación específicas para cada tipo de medida.
     *
     * @param measures Objeto que contiene las medidas a validar.
     */
    private void ValidationMesuares(MeasuresDTO measures) {
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
            double atmosphere = Double.parseDouble(atmosphereValue);
            if (atmosphere <= 0) {
                throw new ApiException(new ApiResponse("La atmósfera debe ser un valor positivo", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("La atmósfera debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }
    }

    /**
     * Válida el valor del brillo (luz).
     * El brillo debe estar dentro del rango [0, 1000].
     *
     * @param brightnessValue Valor del brillo a validar.
     * @throws ApiException Si el valor no es válido o está fuera de rango.
     */
    private void validateBrightness(String brightnessValue) {
        try {
            double brightness = Double.parseDouble(brightnessValue);
            if (brightness < 0 || brightness > 1000) {
                throw new ApiException(new ApiResponse("El brillo debe estar entre 0 y 1000", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("El brillo debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }
    }

    /**
     * Válida el valor de la temperatura.
     * La temperatura debe estar dentro del rango [-40 °C, 80 °C].
     *
     * @param temperatureValue Valor de la temperatura a validar.
     * @throws ApiException Si el valor no es válido o está fuera de rango.
     */
    private void validateTemperature(String temperatureValue) {
        try {
            double temperature = Double.parseDouble(temperatureValue);
            if (temperature < -40 || temperature > 80) {
                throw new ApiException(new ApiResponse("La temperatura debe estar entre -40°C y 80°C", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("La temperatura debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }
    }

    /**
     * Válida el valor del pH.
     * El pH debe estar dentro del rango [0, 14].
     *
     * @param phValue Valor del pH a validar.
     * @throws ApiException Si el valor no es válido o está fuera de rango.
     */
    private void validatePh(String phValue) {
        try {
            double ph = Double.parseDouble(phValue);
            if (ph < 0 || ph > 14) {
                throw new ApiException(new ApiResponse("El pH debe estar entre 0 y 14", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("El pH debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }
    }

    /**
     * Válida el valor de TDS (Total Dissolved Solids).
     * El TDS debe estar dentro del rango [0, 1000 ppm].
     *
     * @param tdsValue Valor de TDS a validar.
     * @throws ApiException Si el valor no es válido o está fuera de rango.
     */
    private void validateTds(String tdsValue) {
        try {
            double tds = Double.parseDouble(tdsValue);
            if (tds < 0 || tds > 1000) {
                throw new ApiException(new ApiResponse("El TDS debe estar entre 0 y 1000 ppm", HttpStatus.BAD_REQUEST.value()));
            }
        } catch (NumberFormatException e) {
            throw new ApiException(new ApiResponse("El TDS debe ser un número válido", HttpStatus.BAD_REQUEST.value()));
        }
    }

    /**
     * Válida el valor de la humedad.
     * La humedad debe estar dentro del rango [0%, 100%].
     *
     * @param humidityValue Valor de la humedad a validar.
     * @throws ApiException Si el valor no es válido o está fuera de rango.
     */
    private void validateHumidity(String humidityValue) {
        try {
            double humidity = Double.parseDouble(humidityValue);
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
    @Override
    public List<History> getAllHistories() {
        List<History> records = repositoryHistory.findAll();
        if (records.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "No se encontró ningún registro en el historial",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        return records;
    }

    /**
     * Obtiene un histórico por su identificador único.
     * Verifica que el ID sea válido y lanza una excepción si no se encuentra el histórico.
     *
     * @param id Identificador del histórico
     * @return El histórico encontrado
     */
    @Override
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
     * Obtiene la lista de históricos asociada a un cultivo específico.
     * Se busca el cultivo por su ID, luego se recuperan los históricos relacionados con ese cultivo.
     *
     * @param cropId El ID del cultivo cuyo historial se desea recuperar.
     * @return Una lista de objetos {@link History} que representan los históricos asociados al cultivo.
     * @throws ApiException Si el cultivo con el ID proporcionado no se encuentra o si ocurre un error en la consulta.
     */
    @Override
    public List<History> getByCrop(String cropId) throws Exception {
        return repositoryHistory.getHistoriesByCrop(new ObjectId(serviceCrop.getCropById(cropId).getId()));
    }

    /**
     * Obtiene la lista de históricos asociada a un cultivo específico y a un rango de fechas.
     * Se busca el cultivo por su ID, luego se recuperan los históricos relacionados con ese cultivo
     * si están dentro de las fechas.
     *
     * @param cropId El ID del cultivo cuyo historial se desea recuperar.
     * @return Una lista de objetos {@link History} que representan los históricos asociados al cultivo.
     * @throws ApiException Si el cultivo con el ID proporcionado no se encuentra o si ocurre un error en la consulta.
     */
    @Override
    public List<History> getHistoriesByCropAndDateBetween(String cropId, DateRange ranges) {
        if (!ObjectId.isValid(cropId)) {
            throw new ApiException(new ApiResponse(
                    "El id '" + cropId + "' no es válido. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        if (ranges.getEndDate() == null || ranges.getStartDate() == null) {
            throw new ApiException(new ApiResponse(
                    "Los rangos de fechas no están definidos",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }

        // TODO: Validar correctamente las fechas

        return repositoryHistory.getHistoriesByCropAndDateBetween(
                new ObjectId(cropId),
                ranges.getStartDate(),
                ranges.getEndDate()
        );
    }

    /**
     * Obtiene los registros históricos de cultivo asociados a un usuario específico.
     * Verifica que el ID del usuario sea válido y lanza una excepción si no se encuentra el usuario o si
     * alguno de los cultivos del usuario no tiene registros históricos asociados.
     *
     * @param id El identificador del usuario cuyo historial de cultivos se quiere obtener.
     * @return Una lista de objetos {@link CropRecordDTO} que contienen información de los cultivos y sus registros históricos.
     * @throws ApiException Si el ID del usuario no es válido (formato incorrecto) o si no se encuentran cultivos asociados al usuario.
     */
    @Override
    public List<CropRecordDTO> getByUser(String id) throws Exception {
        List<CropRecordDTO> records = new ArrayList<>();
        List<CropDTO> crops = serviceCrop.getCropsByUser(id);
        // Verificar si el usuario tiene cultivos
        if (crops.isEmpty()) {
            throw new ApiException(new ApiResponse(
                    "El usuario con id '" + id + "' no tiene cultivos registrados.",
                    HttpStatus.NOT_FOUND.value()
            ));
        }
        for (CropDTO crop : crops) {
            List<History> histories = repositoryHistory.getHistoriesByCrop(new ObjectId(crop.getId()));

            for (History history : histories) {
                records.add(new CropRecordDTO(crop, history));
            }
        }
        return records;
    }

    /**
     * Crea un nuevo histórico.
     * Válida que los datos del histórico sean correctos y luego lo guarda en la base de datos.
     *
     * @param recordDTO Datos del nuevo histórico a crear
     * @return El histórico creado
     */
    @Override
    public History Createhistory(RecordDTO recordDTO) throws Exception {
        ValidationMesuares(recordDTO.getMeasures());
        serviceCrop.getCropById(recordDTO.getCrop());
        History history = RecordMapper.INSTANCE.toEntity(recordDTO);
        return repositoryHistory.save(history);
    }

    /**
     * Actualiza un histórico existente.
     * Verifica que el cultivo asociado al histórico exista, luego actualiza los datos del histórico y lo guarda.
     *
     * @param existingHistory Historial para actualizar
     * @param updateHistory   Datos a actualizar en el historial
     * @return El histórico actualizado
     */
    @Override
    public History updatedHistory(History existingHistory, RecordDTO updateHistory) {
        if (updateHistory.getMeasures() != null && updateHistory.getCrop() != null) {
            existingHistory = RecordMapper.INSTANCE.toEntity(updateHistory);
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
     * Elimina un histórico existente.
     * Intenta eliminar el histórico por su ID y devuelve una respuesta con un mensaje de éxito o error.
     *
     * @param existingHistory El histórico a eliminar
     * @return Respuesta HTTP con un mensaje de éxito o error
     */
    @Override
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
