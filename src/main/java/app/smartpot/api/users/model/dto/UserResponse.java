package app.smartpot.api.users.model.dto;

import java.time.Instant;

public record UserResponse(
        String id,
        String name,
        String lastName,
        String email,
        String role,
        Instant createdAt
) {
}
