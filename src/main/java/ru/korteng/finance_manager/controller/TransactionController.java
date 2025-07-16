package ru.korteng.finance_manager.controller;

import jakarta.validation.Valid;
import org.hibernate.Transaction;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.korteng.finance_manager.dto.TransactionRequest;
import ru.korteng.finance_manager.service.TransactionService;

@RestController
@RequestMapping("api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<?> createTransaction(@RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.createTransaction(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }
}
