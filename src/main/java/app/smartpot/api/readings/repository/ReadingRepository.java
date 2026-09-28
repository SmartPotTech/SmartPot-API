package app.smartpot.api.readings.repository;

import app.smartpot.api.readings.model.entity.Reading;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReadingRepository extends MongoRepository<Reading, String> {

    Optional<Reading> findFirstByCropIdOrderByMeasuredAtDesc(String cropId);

    List<Reading> findByCropIdOrderByMeasuredAtDesc(String cropId, Pageable pageable);

    List<Reading> findByCropIdAndMeasuredAtBetweenOrderByMeasuredAtDesc(String cropId, Instant from, Instant to,
                                                                        Pageable pageable);

    void deleteByCropId(String cropId);
}
