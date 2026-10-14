package so.budget.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import so.budget.api.entity.TargetType;

import java.time.LocalDate;

@Builder
public record UpdateCategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 255, message = "Tên danh mục không vượt quá 255 ký tự")
        String name,

        @Size(max = 500, message = "Ghi chú không vượt quá 500 ký tự")
        String note,

        Boolean hidden,
        Integer sortOrder,
        TargetType targetType,
        Long targetAmount,
        LocalDate targetDate,
        LocalDate targetCreationMonth,
        Integer targetCadence,
        Integer targetCadenceFrequency,
        Integer targetDay,
        Boolean targetNeedsWholeAmount
) {}
