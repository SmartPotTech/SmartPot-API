package app.smartpot.api.records.controller;

import app.smartpot.api.exception.ApiResponse;
import app.smartpot.api.records.model.dto.RecordDTO;
import app.smartpot.api.records.model.entity.DateRange;
import app.smartpot.api.records.model.entity.History;
import app.smartpot.api.records.service.RecordService;
import app.smartpot.api.responses.ErrorResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/Records")
public class RecordController {

    private final RecordService serviceHistory;

    @Autowired
    public RecordController(RecordService serviceHistory) {
        this.serviceHistory = serviceHistory;
    }

    /**
     * Obtiene todos los históricos almacenados en el sistema.
     *
     * @return Lista de todos los históricos existentes
     */
    @GetMapping("/All")
    public List<History> getAllHistories() {
        return serviceHistory.getAllHistories();
    }

    /**
     * Crea un nuevo histórico
     *
     * @param newHistory El objeto histórico que contiene los datos del histórico que se debe guardar
     * @return El objeto histórico creado
     */
    @PostMapping("/Create")
    @ResponseStatus(HttpStatus.CREATED)
    public History createHistory(@RequestBody RecordDTO newHistory) throws Exception {
        return serviceHistory.Createhistory(newHistory);
    }

    /**
     * Busca un histórico filtrando por un cultivo.
     *
     * @param id Identificador ObjectId del cultivo
     * @return Los históricos encontrado
     */
    @GetMapping("/crop/{id}")
    public List<History> getByCrop(@PathVariable String id) throws Exception {
        return serviceHistory.getByCrop(id);
    }

    /**
     * Busca un histórico filtrando por un cultivo y por un rango de fechas.
     *
     * @param id     Identificador ObjectId del cultivo
     * @param ranges Rango de fechas
     * @return Los históricos encontrado
     */
    @PostMapping("/crop/between/{id}")
    public List<History> getByCropAndDateRange(@PathVariable String id, @RequestBody DateRange ranges) {
        return serviceHistory.getHistoriesByCropAndDateBetween(id, ranges);
    }

    /**
     * Busca un histórico filtrando por un cultivo.
     *
     * @param id Identificador ObjectId del cultivo
     * @return Los históricos encontrado
     */
    @GetMapping("/user/{id}")
    public ResponseEntity<?> getByUser(@PathVariable String id) {
        try {
            return new ResponseEntity<>(serviceHistory.getByUser(id), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Busca un histórico por su identificador único.
     *
     * @param id Identificador ObjectId del histórico
     * @return El histórico encontrado
     */
    @GetMapping("/{id}")
    public History getHistory(@PathVariable String id) {
        return serviceHistory.getHistoryById(id);
    }


    /**
     * Actualiza un histórico existente.
     *
     * @param id             El ID del histórico a actualizar.
     * @param historyDetails El objeto HistoryDTO que contiene los nuevos datos.
     * @return El objeto History actualizado.
     */
    @PutMapping("/Update/{id}")
    public History updateHistory(@PathVariable String id, @RequestBody RecordDTO historyDetails) {
        return serviceHistory.updatedHistory(serviceHistory.getHistoryById(id), historyDetails);
    }

    /**
     * Elimina un histórico existente por su ID.
     *
     * @param id El ID del histórico a eliminar.
     */
    @DeleteMapping("/Delete/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<ApiResponse> deleteHistory(@PathVariable String id) {
        return serviceHistory.deleteHistory(serviceHistory.getHistoryById(id));
    }
}