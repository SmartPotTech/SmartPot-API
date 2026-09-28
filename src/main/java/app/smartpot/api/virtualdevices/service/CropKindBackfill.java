package app.smartpot.api.virtualdevices.service;

import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.CropForm;
import app.smartpot.api.crops.model.entity.CropKind;
import app.smartpot.api.crops.model.entity.Placement;
import app.smartpot.api.virtualdevices.model.entity.VirtualDevice;
import app.smartpot.api.virtualdevices.model.entity.VirtualLocation;
import app.smartpot.api.virtualdevices.model.entity.VirtualMode;
import app.smartpot.api.virtualdevices.repository.VirtualDeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Completa el tipo, la forma y el lugar de los cultivos creados antes de que existieran: los que ya tenían una
 * simulación pasan a ser virtuales, el resto son reales y los que no tienen forma quedan como maceta. La ubicación
 * que tenía la simulación pasa a ser la del cultivo (al aire libre y a pleno sol si seguía el clima, como se
 * simulaba entonces). Es idempotente y corre al arrancar.
 */
@Slf4j
@Component
public class CropKindBackfill {

    private final MongoTemplate mongoTemplate;
    private final VirtualDeviceRepository repository;

    public CropKindBackfill(MongoTemplate mongoTemplate, VirtualDeviceRepository repository) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        try {
            List<String> simulated = repository.findAll().stream().map(VirtualDevice::getCropId).toList();
            long virtual = simulated.isEmpty() ? 0 : mongoTemplate.updateMulti(
                    Query.query(Criteria.where("kind").exists(false).and("id").in(simulated)),
                    Update.update("kind", CropKind.VIRTUAL.name()), Crop.class).getModifiedCount();
            long real = mongoTemplate.updateMulti(Query.query(Criteria.where("kind").exists(false)),
                    Update.update("kind", CropKind.REAL.name()), Crop.class).getModifiedCount();
            long forms = mongoTemplate.updateMulti(Query.query(Criteria.where("form").exists(false)),
                    Update.update("form", CropForm.POT.name()), Crop.class).getModifiedCount();
            long placed = placeSimulated();
            if (virtual + real + forms + placed > 0) {
                log.info("Cultivos anteriores completados: {} virtuales, {} reales, {} como maceta y {} con lugar",
                        virtual, real, forms, placed);
            }
        } catch (RuntimeException ex) {
            log.warn("No se pudo completar el tipo de los cultivos anteriores: {}", ex.getMessage());
        }
    }

    private long placeSimulated() {
        long placed = 0;
        for (VirtualDevice config : repository.findAll()) {
            VirtualLocation location = config.getLocation();
            if (location == null) {
                continue;
            }
            boolean weather = config.getMode() == VirtualMode.WEATHER;
            Placement placement = new Placement(weather ? Placement.Setting.OUTDOOR : null,
                    weather ? Placement.Exposure.FULL_SUN : null,
                    new Placement.Location(location.name(), location.latitude(), location.longitude()));
            placed += mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(config.getCropId()).and("placement").exists(false)),
                    Update.update("placement", placement), Crop.class).getModifiedCount();
        }
        return placed;
    }
}
