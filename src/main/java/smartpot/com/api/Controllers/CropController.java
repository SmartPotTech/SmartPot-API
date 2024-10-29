package smartpot.com.api.Controllers;

import smartpot.com.api.Models.DAO.RCrop;
import smartpot.com.api.Models.Entity.Crop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/Cultivos")
public class CropController {

    @Autowired
    private RCrop repositoryCrop;

    @GetMapping
    public List<Crop> getAllCrops() {
        return repositoryCrop.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Crop> getCrop(@PathVariable String id) {
        return repositoryCrop.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Cultivo con id " + id + " no encontrado"));
    }

    @PostMapping
    public Crop createCrop(@RequestBody Crop newCrop) {
        return repositoryCrop.save(newCrop);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Crop> updateCrop(@PathVariable String id, @RequestBody Crop cropDetails) {
        return repositoryCrop.findById(id)
                .map(crop -> {
                    crop.setStatus(cropDetails.getStatus());
                    crop.setType(cropDetails.getType());
                    crop.setUser(cropDetails.getUser());
                    crop.setCultivation(cropDetails.getCultivation());
                    Crop updatedCrop = repositoryCrop.save(crop);
                    return ResponseEntity.ok(updatedCrop);
                })
                .orElseThrow(() -> new RuntimeException("Cultivo con id " + id + " no encontrado"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCrop(@PathVariable String id) {
        return repositoryCrop.findById(id)
                .map(crop -> {
                    repositoryCrop.delete(crop);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElseThrow(() -> new RuntimeException("Cultivo con id " + id + " no encontrado"));
    }
}
