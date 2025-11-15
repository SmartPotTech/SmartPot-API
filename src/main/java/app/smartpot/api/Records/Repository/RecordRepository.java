package app.smartpot.api.records.repository;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import app.smartpot.api.records.model.entity.History;

import java.util.Date;
import java.util.List;

@Repository
public interface RecordRepository extends MongoRepository<History, ObjectId> {

    @Query("{ 'crop' : ?0 }")
    List<History> getHistoriesByCrop(ObjectId cropId);

    List<History> getHistoriesByCropAndDateBetween(
            ObjectId cropId,
            @NotNull(message = "La fecha no puede estar vacía")
            @PastOrPresent(message = "La fecha del registro debe ser anterior o igual a hoy")
            Date date,
            @NotNull(message = "La fecha no puede estar vacía")
            @PastOrPresent(message = "La fecha del registro debe ser anterior o igual a hoy")
            Date date2);

}
