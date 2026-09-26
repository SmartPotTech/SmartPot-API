package app.smartpot.api.readings.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "readings")
@CompoundIndex(name = "crop_measured", def = "{'cropId': 1, 'measuredAt': -1}")
public class Reading {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String cropId;

    @Indexed(expireAfter = "365d")
    private Instant measuredAt;

    private Measures measures;

    private ReadingSource source;
}
