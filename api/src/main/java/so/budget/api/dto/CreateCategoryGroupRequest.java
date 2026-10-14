package so.budget.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CreateCategoryGroupRequest(
        @NotBlank(message = "Tên nhóm danh mục không được để trống")
        @Size(max = 255, message = "Tên nhóm không vượt quá 255 ký tự")
        String name
) {}
