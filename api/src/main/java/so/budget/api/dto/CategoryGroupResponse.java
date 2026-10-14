package so.budget.api.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record CategoryGroupResponse(
        UUID id,
        String name,
        boolean hidden,
        Integer sortOrder,
        List<CategoryResponse> categories
) {}
