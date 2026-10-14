package so.budget.api.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PlanResponse(
        UUID id,
        String name,
        String currencyCode,
        Short decimalDigits,
        Instant createdAt,
        Instant updatedAt
) {}
