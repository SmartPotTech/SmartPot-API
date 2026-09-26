package app.smartpot.api.users.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "passwordHash")
@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String name;

    private String lastName;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    private UserRole role;

    private Instant createdAt;

    private Instant updatedAt;
}
