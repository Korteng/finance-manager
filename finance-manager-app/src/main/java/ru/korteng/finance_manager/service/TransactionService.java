package ru.korteng.finance_manager.service;

import ru.korteng.finance_manager.entity.Budget;
import ru.korteng.finance_manager.repository.BudgetRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.korteng.finance_manager.entity.Category;
import ru.korteng.finance_manager.entity.Transaction;
import ru.korteng.finance_manager.dto.TransactionRequest;
import ru.korteng.finance_manager.dto.TransactionResponse;
import ru.korteng.finance_manager.event.TransactionEvent;
import ru.korteng.finance_manager.repository.CategoryRepository;
import ru.korteng.finance_manager.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionEventPublisher eventPublisher;
    private final BudgetRepository budgetRepository;
    private final Clock clock;

    @Transactional
    public Transaction createTransaction(TransactionRequest request, Long userId) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        Transaction transaction = new Transaction();
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency().toUpperCase());
        transaction.setCategory(category);
        transaction.setDescription(request.getDescription());
        transaction.setCreatedAt(Instant.now(clock));
        transaction.setUserId(userId);

        Transaction saved = transactionRepository.save(transaction);

        publishTransactionCreated(saved);
        checkBudget(saved);

        return saved;
    }

    private void publishTransactionCreated(Transaction transaction) {
        TransactionEvent event = new TransactionEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("TRANSACTION_CREATED");
        event.setUserId(transaction.getUserId());
        event.setOccurredAt(Instant.now(clock));

        TransactionEvent.Payload payload = new TransactionEvent.Payload();
        payload.setAmount(transaction.getAmount());
        payload.setCategory(transaction.getCategory().getName());
        payload.setType("EXPENSE");
        event.setPayload(payload);

        eventPublisher.publish(event);
    }

    private void publishBudgetExceeded(Transaction transaction, BigDecimal spent, BigDecimal limit) {
        TransactionEvent event = new TransactionEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("BUDGET_EXCEEDED");
        event.setUserId(transaction.getUserId());
        event.setOccurredAt(Instant.now(clock));

        TransactionEvent.Payload payload = new TransactionEvent.Payload();
        payload.setAmount(spent);
        payload.setCategory(transaction.getCategory().getName());
        payload.setType("BUDGET_EXCEEDED");
        payload.setLimit(limit);
        event.setPayload(payload);

        eventPublisher.publish(event);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long id, Long userId) {
        Transaction transaction = transactionRepository.findById(id)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found"));
        return TransactionResponse.fromEntity(transaction);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listTransactions(Long userId, Long categoryId, Instant from, Instant to) {
        return transactionRepository.findAllForUser(userId, categoryId, from, to)
                .stream()
                .map(TransactionResponse::fromEntity)
                .toList();
    }

    private void checkBudget(Transaction transaction) {
        LocalDate period = YearMonth.now(clock).atDay(1);
        Long categoryId = transaction.getCategory().getId();
        Long userId = transaction.getUserId();

        Optional<Budget> budgetOpt = budgetRepository.findByUserAndCategoryAndPeriod(userId, categoryId, period);
        if (budgetOpt.isEmpty()) {
            return;
        }
        Budget budget = budgetOpt.get();

        Instant from = period.atStartOfDay(clock.getZone()).toInstant();
        Instant to = period.plusMonths(1).atStartOfDay(clock.getZone()).toInstant();
        BigDecimal spent = transactionRepository.sumAmountForCategoryInPeriod(userId, categoryId, from, to);

        if (spent.compareTo(budget.getLimitAmount()) > 0) {
            publishBudgetExceeded(transaction, spent, budget.getLimitAmount());
        }
    }
}