package smartpot.com.api.Controllers;

import smartpot.com.api.Models.DAO.Repository.RHistory;
import smartpot.com.api.Models.Entity.History;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/Historial")
public class HistoryController {

    @Autowired
    private RHistory repositoryHistory;

    @GetMapping
    public List<History> getAllHistories() {
        return repositoryHistory.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<History> getHistory(@PathVariable String id) {
        return repositoryHistory.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Historial con id " + id + " no encontrado"));
    }

    @PostMapping
    public History createHistory(@RequestBody History newHistory) {
        newHistory.setDate(new Date());
        return repositoryHistory.save(newHistory);
    }

    @PutMapping("/{id}")
    public ResponseEntity<History> updateHistory(@PathVariable String id, @RequestBody History historyDetails) {
        return repositoryHistory.findById(id)
                .map(history -> {
                    history.setDate(historyDetails.getDate());
                    history.setMeasures(historyDetails.getMeasures());
                    history.setCrop(historyDetails.getCrop());
                    History updatedHistory = repositoryHistory.save(history);
                    return ResponseEntity.ok(updatedHistory);
                })
                .orElseThrow(() -> new RuntimeException("Historial con id " + id + " no encontrado"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHistory(@PathVariable String id) {
        return repositoryHistory.findById(id)
                .map(history -> {
                    repositoryHistory.delete(history);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElseThrow(() -> new RuntimeException("Historial con id " + id + " no encontrado"));
    }
}