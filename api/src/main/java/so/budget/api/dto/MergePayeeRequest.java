package so.budget.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record MergePayeeRequest(
        @NotNull(message = "Target payee ID không được để trống")
        UUID targetPayeeId,

        @NotEmpty(message = "Danh sách source payee IDs không được để trống")
        List<UUID> sourcePayeeIds
) {}
