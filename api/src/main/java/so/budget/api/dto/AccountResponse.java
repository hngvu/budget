package so.budget.api.dto;

import lombok.Builder;
import so.budget.api.entity.AccountType;

import java.util.UUID;

@Builder
public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        boolean closed,
        String note,
        Integer sortOrder,
        Long balance,
        Long clearedBalance
) {}
