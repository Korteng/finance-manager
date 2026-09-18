package ru.korteng.finance_manager.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.korteng.finance_manager.entity.Budget;
import ru.korteng.finance_manager.dto.BudgetRequest;
import ru.korteng.finance_manager.dto.BudgetResponse;
import ru.korteng.finance_manager.entity.Category;
import ru.korteng.finance_manager.repository.BudgetRepository;
import ru.korteng.finance_manager.repository.CategoryRepository;
import ru.korteng.finance_manager.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public BudgetResponse setBudget(BudgetRequest request, Long userId) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        LocalDate period = request.getPeriod().atDay(1);

        Budget budget = budgetRepository.findByUserAndCategoryAndPeriod(userId, category.getId(), period)
                .orElseGet(Budget::new);

        budget.setUserId(userId);
        budget.setCategory(category);
        budget.setPeriod(period);
        budget.setLimitAmount(request.getLimitAmount());

        Budget saved = budgetRepository.save(budget);
        BigDecimal spent = spentForPeriod(userId, category.getId(), period);
        return BudgetResponse.fromEntity(saved, spent);
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> listBudgets(Long userId, YearMonth period) {
        LocalDate periodStart = period.atDay(1);
        return budgetRepository.findAllByUserAndPeriod(userId, periodStart)
                .stream()
                .map(b -> BudgetResponse.fromEntity(b, spentForPeriod(userId, b.getCategory().getId(), periodStart)))
                .toList();
    }

    private BigDecimal spentForPeriod(Long userId, Long categoryId, LocalDate periodStart) {
        Instant from = periodStart.atStartOfDay().atZone(ZoneOffset.UTC).toInstant();
        Instant to = periodStart.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return transactionRepository.sumAmountForCategoryInPeriod(userId, categoryId, from, to);
    }
}
