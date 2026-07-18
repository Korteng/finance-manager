package ru.korteng.finance_manager.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.korteng.finance_manager.dto.Category;
import ru.korteng.finance_manager.dto.Transaction;
import ru.korteng.finance_manager.dto.TransactionRequest;
import ru.korteng.finance_manager.dto.TransactionResponse;
import ru.korteng.finance_manager.event.TransactionEvent;
import ru.korteng.finance_manager.repository.CategoryRepository;
import ru.korteng.finance_manager.repository.TransactionRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    private static final String TOPIC = "transaction-events";

    @Transactional
    public Transaction createTransaction(TransactionRequest request, Long userId) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        Transaction transaction = new Transaction();
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency().toUpperCase());
        transaction.setCategory(category);
        transaction.setDescription(request.getDescription());
        transaction.setCreatedAt(Instant.now());
        transaction.setUserId(userId);

        Transaction saved = transactionRepository.save(transaction);

        publishEvent(saved);

        return saved;
    }

    private void publishEvent(Transaction transaction) {
        TransactionEvent event = new TransactionEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("TRANSACTION_CREATED");
        event.setUserId(transaction.getUserId());
        event.setOccurredAt(Instant.now());

        TransactionEvent.Payload payload = new TransactionEvent.Payload();
        payload.setAmount(transaction.getAmount());
        payload.setCategory(transaction.getCategory().getName());
        payload.setType("EXPENSE");
        event.setPayload(payload);

        kafkaTemplate.send(TOPIC, event.getEventId().toString(), event);
        log.info("Published TransactionEvent, eventId={}", event.getEventId());
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found"));
        return TransactionResponse.fromEntity(transaction);
    }
}