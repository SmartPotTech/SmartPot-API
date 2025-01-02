package smartpot.com.api.Mail.Model.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "correos")
public class EmailDetails implements Serializable {
    @Id
    @Field("_id")
    private ObjectId id;

    @Field("recipient")
    private String recipient;

    @Field("msgBody")
    private String msgBody;

    @Field("subject")
    private String subject;

    @Field("attachment")
    private String attachment;
}
