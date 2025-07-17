package ru.korteng.finance_manager.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionRequest {

    @NotNull @Positive
    private BigDecimal amount;

    @NotBlank @Size(min = 3, max = 3)
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3 uppercase letters")
    private String currency;

    @NotNull
    private Long categoryId;

    @Size(max = 500)
    private String description;
}
