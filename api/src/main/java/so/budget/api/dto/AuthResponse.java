package so.budget.api.dto;

import lombok.Builder;

@Builder
public record AuthResponse(
        String token,
        UserResponse user
) {}
