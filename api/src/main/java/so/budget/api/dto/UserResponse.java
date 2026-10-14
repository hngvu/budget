package so.budget.api.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String subscriptionTier
) {}
