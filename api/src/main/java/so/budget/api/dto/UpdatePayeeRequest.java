package so.budget.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdatePayeeRequest(
        @NotBlank(message = "Tên payee không được để trống")
        @Size(max = 255, message = "Tên payee không vượt quá 255 ký tự")
        String name
) {}
