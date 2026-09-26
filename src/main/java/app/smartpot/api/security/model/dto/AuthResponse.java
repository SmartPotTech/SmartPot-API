package app.smartpot.api.security.model.dto;

import app.smartpot.api.users.model.dto.UserResponse;

import java.time.Instant;

public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
}
