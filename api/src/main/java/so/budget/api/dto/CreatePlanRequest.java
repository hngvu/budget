package so.budget.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CreatePlanRequest(
        @NotBlank(message = "Tên ngân sách không được để trống")
        @Size(max = 255, message = "Tên ngân sách không vượt quá 255 ký tự")
        String name,

        @NotBlank(message = "Mã tiền tệ không được để trống")
        @Size(min = 3, max = 3, message = "Mã tiền tệ phải có đúng 3 ký tự (VD: VND, USD)")
        String currencyCode,

        @NotNull(message = "Số chữ số thập phân không được để trống")
        @Min(value = 0, message = "Số chữ số thập phân tối thiểu là 0")
        @Max(value = 3, message = "Số chữ số thập phân tối đa là 3")
        Short decimalDigits
) {}
