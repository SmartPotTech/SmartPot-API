package app.smartpot.api.readings.repository;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Promedios de una variable por cultivo e intervalo, calculados en MongoDB con $dateTrunc
 * para comparar varios cultivos sin traer cada lectura.
 */
@Repository
public class ReadingSeriesRepository {

    private final MongoTemplate mongoTemplate;

    public ReadingSeriesRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Bucket> averages(Collection<String> cropIds, String metric, Instant from, int bucketMinutes) {
        if (cropIds.isEmpty()) {
            return List.of();
        }
        String field = "measures." + metric;
        List<ObjectId> ids = cropIds.stream().map(ObjectId::new).toList();
        List<Document> pipeline = List.of(
                new Document("$match", new Document("cropId", new Document("$in", ids))
                        .append("measuredAt", new Document("$gte", Date.from(from)))
                        .append(field, new Document("$type", "number"))),
                new Document("$group", new Document("_id", new Document("cropId", "$cropId")
                        .append("time", new Document("$dateTrunc", new Document("date", "$measuredAt")
                                .append("unit", "minute")
                                .append("binSize", bucketMinutes))))
                        .append("value", new Document("$avg", "$" + field))),
                new Document("$sort", new Document("_id.time", 1)));

        List<Bucket> buckets = new ArrayList<>();
        for (Document row : mongoTemplate.getCollection("readings").aggregate(pipeline)) {
            Document id = row.get("_id", Document.class);
            Number value = row.get("value", Number.class);
            buckets.add(new Bucket(id.getObjectId("cropId").toHexString(), id.getDate("time").toInstant(),
                    value.doubleValue()));
        }
        return buckets;
    }

    public record Bucket(String cropId, Instant time, double value) {
    }
}
