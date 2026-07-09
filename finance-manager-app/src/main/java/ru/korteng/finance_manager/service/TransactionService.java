package ru.korteng.finance_manager.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.korteng.finance_manager.dto.Category;
import ru.korteng.finance_manager.dto.Transaction;
import ru.korteng.finance_manager.dto.TransactionRequest;
import ru.korteng.finance_manager.dto.TransactionResponse;
import ru.korteng.finance_manager.repository.CategoryRepository;
import ru.korteng.finance_manager.repository.TransactionRepository;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public Transaction createTransaction(TransactionRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        Transaction transaction = new Transaction();
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency().toUpperCase());
        transaction.setCategory(category);
        transaction.setDescription(request.getDescription());
        transaction.setCreatedAt(Instant.now());

        return transactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() ->  new EntityNotFoundException("Transaction not found"));
        return TransactionResponse.fromEntity(transaction);
    }

}
