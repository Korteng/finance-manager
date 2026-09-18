package ru.korteng.finance_manager.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import ru.korteng.finance_manager.dto.TransactionRequest;
import ru.korteng.finance_manager.entity.Budget;
import ru.korteng.finance_manager.entity.Category;
import ru.korteng.finance_manager.entity.Transaction;
import ru.korteng.finance_manager.event.TransactionEvent;
import ru.korteng.finance_manager.repository.BudgetRepository;
import ru.korteng.finance_manager.repository.CategoryRepository;
import ru.korteng.finance_manager.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CATEGORY_ID = 10L;
    private static final String TOPIC = "transaction-events";

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @Mock
    private BudgetRepository budgetRepository;

    @InjectMocks
    private TransactionService transactionService;

    private Category category() {
        Category category = new Category();
        category.setId(CATEGORY_ID);
        category.setName("Groceries");
        return category;
    }

    private TransactionRequest request(BigDecimal amount) {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(amount);
        request.setCurrency("rub");
        request.setCategoryId(CATEGORY_ID);
        request.setDescription("test");
        return request;
    }

    private Budget budget(BigDecimal limit) {
        Budget budget = new Budget();
        budget.setId(100L);
        budget.setUserId(USER_ID);
        budget.setCategory(category());
        budget.setPeriod(LocalDate.now().withDayOfMonth(1));
        budget.setLimitAmount(limit);
        return budget;
    }

    @Test
    void createTransaction_NoBudgetForCategory_PublishesOnlyTransactionCreated() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(budgetRepository.findByUserAndCategoryAndPeriod(eq(USER_ID), eq(CATEGORY_ID), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        transactionService.createTransaction(request(new BigDecimal("500")), USER_ID);

        verify(kafkaTemplate, times(1)).send(eq(TOPIC), anyString(), any(TransactionEvent.class));
        verify(transactionRepository, never()).sumAmountForCategoryInPeriod(any(), any(), any(), any());
    }

    @Test
    void createTransaction_SpentWithinLimit_PublishesOnlyTransactionCreated() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(budgetRepository.findByUserAndCategoryAndPeriod(eq(USER_ID), eq(CATEGORY_ID), any(LocalDate.class)))
                .thenReturn(Optional.of(budget(new BigDecimal("1000"))));
        when(transactionRepository.sumAmountForCategoryInPeriod(eq(USER_ID), eq(CATEGORY_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("800"));

        transactionService.createTransaction(request(new BigDecimal("500")), USER_ID);

        ArgumentCaptor<TransactionEvent> captor = ArgumentCaptor.forClass(TransactionEvent.class);
        verify(kafkaTemplate, times(1)).send(eq(TOPIC), anyString(), captor.capture());
        assertEquals("TRANSACTION_CREATED", captor.getValue().getEventType());
    }

    @Test
    void createTransaction_SpentExceedsLimit_PublishesTransactionCreatedAndBudgetExceeded() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(budgetRepository.findByUserAndCategoryAndPeriod(eq(USER_ID), eq(CATEGORY_ID), any(LocalDate.class)))
                .thenReturn(Optional.of(budget(new BigDecimal("1000"))));
        when(transactionRepository.sumAmountForCategoryInPeriod(eq(USER_ID), eq(CATEGORY_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("1500"));

        transactionService.createTransaction(request(new BigDecimal("500")), USER_ID);

        ArgumentCaptor<TransactionEvent> captor = ArgumentCaptor.forClass(TransactionEvent.class);
        verify(kafkaTemplate, times(2)).send(eq(TOPIC), anyString(), captor.capture());

        TransactionEvent budgetExceededEvent = captor.getAllValues().stream()
                .filter(e -> "BUDGET_EXCEEDED".equals(e.getEventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("BUDGET_EXCEEDED event was not published"));

        assertEquals(USER_ID, budgetExceededEvent.getUserId());
        assertEquals(0, new BigDecimal("1500").compareTo(budgetExceededEvent.getPayload().getAmount()));
        assertEquals(0, new BigDecimal("1000").compareTo(budgetExceededEvent.getPayload().getLimit()));
        assertEquals("Groceries", budgetExceededEvent.getPayload().getCategory());
    }

    @Test
    void createTransaction_SpentEqualsLimit_DoesNotPublishBudgetExceeded() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(budgetRepository.findByUserAndCategoryAndPeriod(eq(USER_ID), eq(CATEGORY_ID), any(LocalDate.class)))
                .thenReturn(Optional.of(budget(new BigDecimal("1000"))));
        when(transactionRepository.sumAmountForCategoryInPeriod(eq(USER_ID), eq(CATEGORY_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("1000"));

        transactionService.createTransaction(request(new BigDecimal("500")), USER_ID);

        verify(kafkaTemplate, times(1)).send(eq(TOPIC), anyString(), any(TransactionEvent.class));
    }
}
