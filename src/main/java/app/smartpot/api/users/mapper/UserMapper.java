package app.smartpot.api.users.mapper;

import app.smartpot.api.users.model.dto.UserResponse;
import app.smartpot.api.users.model.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getLastName(), user.getEmail(),
                user.getRole().name(), user.getCreatedAt());
    }
}
