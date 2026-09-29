package ru.korteng.finance_manager.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import ru.korteng.finance_manager.dto.BudgetRequest;
import ru.korteng.finance_manager.dto.BudgetResponse;
import ru.korteng.finance_manager.service.BudgetService;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping
    public ResponseEntity<BudgetResponse> setBudget(@Valid @RequestBody BudgetRequest request) {
        Long userId =  extractUserId();
        return ResponseEntity.ok(budgetService.setBudget(request, userId));
    }

    @GetMapping
    public ResponseEntity<List<BudgetResponse>> listBudgets(@RequestParam(required = false) YearMonth period) {
        Long userId = extractUserId();
        YearMonth target = period != null ? period : YearMonth.now();
        return ResponseEntity.ok(budgetService.listBudgets(userId, target));
    }

    private Long extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return (Long) auth.getDetails();
    }
}
