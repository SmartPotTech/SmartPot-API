package smartpot.com.api.Controllers;

import org.springframework.http.HttpStatus;
import smartpot.com.api.Models.DAO.Repository.RHistory;
import smartpot.com.api.Models.DAO.Service.SHistory;
import smartpot.com.api.Models.DTO.HistoryDTO;
import smartpot.com.api.Models.Entity.History;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smartpot.com.api.Validation.Exception.ApiResponse;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/Historial")
public class HistoryController {
    @Autowired
    private SHistory serviceHistory;

    /**
     * Obtiene todos los históricos almacenados en el sistema.
     *
     * @return Lista de todos los históricos existentes
     */
    @GetMapping
    public List<History> getAllHistories() {
        return serviceHistory.getAllHistorys();
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
     * Crea un nuevo histórico
     *
     * @param newHistory El objeto histórico que contiene los datos del histórico que se debe guardar
     * @return El objeto histórico creado
     */
    @PostMapping("/Create")
    @ResponseStatus(HttpStatus.CREATED)
    public History createHistory(@RequestBody HistoryDTO newHistory) {
        return serviceHistory.Createhistory(newHistory);
    }

    /**
     * Actualiza un histórico existente.
     *
     * @param id El ID del histórico a actualizar.
     * @param historyDetails El objeto HistoryDTO que contiene los nuevos datos.
     * @return El objeto History actualizado.
     */
    @PutMapping("/Update/{id}")
    public History updateHistory(@PathVariable String id, @RequestBody HistoryDTO historyDetails) {
        return serviceHistory.updatedHistory(id, historyDetails);
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