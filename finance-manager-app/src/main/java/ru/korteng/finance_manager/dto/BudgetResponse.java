package ru.korteng.finance_manager.dto;

import lombok.Data;
import ru.korteng.finance_manager.entity.Budget;

import java.math.BigDecimal;
import java.time.YearMonth;

@Data
public class BudgetResponse {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private YearMonth period;
    private BigDecimal limitAmount;
    private BigDecimal spent;
    private boolean exceeded;

    public static BudgetResponse fromEntity(Budget budget, BigDecimal spent) {
        BudgetResponse response = new BudgetResponse();
        response.setId(budget.getId());
        response.setCategoryId(budget.getCategory().getId());
        response.setCategoryName(budget.getCategory().getName());
        response.setPeriod(YearMonth.from(budget.getPeriod()));
        response.setLimitAmount(budget.getLimitAmount());
        response.setSpent(spent);
        response.setExceeded(spent.compareTo(budget.getLimitAmount()) > 0);
        return response;
    }
}
