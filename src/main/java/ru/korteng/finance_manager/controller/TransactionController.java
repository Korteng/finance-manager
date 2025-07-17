package ru.korteng.finance_manager.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.korteng.finance_manager.dto.Transaction;
import ru.korteng.finance_manager.dto.TransactionRequest;
import ru.korteng.finance_manager.service.TransactionService;

@RestController
@RequestMapping("api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "API для работы с транзакциями")
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(summary = "Create new transaction")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction created"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.createTransaction(request));
    }

    @Operation(summary = "Get transaction by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction found"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<?> getTransaction(
            @Parameter(description = "Transaction ID") @PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }
}
