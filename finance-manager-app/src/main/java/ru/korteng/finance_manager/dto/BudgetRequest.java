package ru.korteng.finance_manager.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.YearMonth;

@Data
public class BudgetRequest {

    @NotNull
    private Long categoryId;

    @NotNull
    private YearMonth period;

    @NotNull
    @Positive
    private BigDecimal limitAmount;
}
