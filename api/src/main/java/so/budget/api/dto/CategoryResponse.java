package so.budget.api.dto;

import lombok.Builder;
import so.budget.api.entity.TargetType;

import java.time.LocalDate;
import java.util.UUID;

@Builder
public record CategoryResponse(
        UUID id,
        UUID categoryGroupId,
        String name,
        boolean hidden,
        String note,
        Integer sortOrder,
        String systemKind,
        TargetType targetType,
        Long targetAmount,
        LocalDate targetDate,
        LocalDate targetCreationMonth,
        Integer targetCadence,
        Integer targetCadenceFrequency,
        Integer targetDay,
        Boolean targetNeedsWholeAmount
) {}
