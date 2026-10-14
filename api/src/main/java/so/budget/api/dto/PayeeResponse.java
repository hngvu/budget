package so.budget.api.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record PayeeResponse(
        UUID id,
        String name
) {}
