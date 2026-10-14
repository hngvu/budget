package so.budget.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import so.budget.api.entity.AccountType;

import java.time.LocalDate;

@Builder
public record CreateAccountRequest(
        @NotBlank(message = "Tên tài khoản không được để trống")
        @Size(max = 255, message = "Tên tài khoản không vượt quá 255 ký tự")
        String name,

        @NotNull(message = "Loại tài khoản không được để trống")
        AccountType type,

        @Size(max = 500, message = "Ghi chú không vượt quá 500 ký tự")
        String note,

        Long initialBalance,

        LocalDate initialBalanceDate
) {}
