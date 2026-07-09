package ru.korteng.finance_manager.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.korteng.finance_manager.dto.Transaction;
import ru.korteng.finance_manager.dto.TransactionRequest;
import ru.korteng.finance_manager.dto.TransactionResponse;
import ru.korteng.finance_manager.exception.ErrorResponse;
import ru.korteng.finance_manager.service.TransactionService;

import java.net.URI;

@RestController
@RequestMapping("api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "API для работы с транзакциями")
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
        summary = "Create new transaction",
        responses = {
            @ApiResponse(
                responseCode = "201",
                description = "Transaction created successfully",
                content = @Content(
                    schema = @Schema(implementation = Transaction.class)
                )
            ),
            @ApiResponse(
                responseCode = "422",
                description = "Validation error",
                content = @Content(
                    schema = @Schema(implementation = ErrorResponse.class)
                )
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Category not found",
                content = @Content(
                    schema = @Schema(implementation = ErrorResponse.class)
                )
            )
        }
    )
    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@Valid @RequestBody TransactionRequest request) {
        Transaction createdTransaction = transactionService.createTransaction(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdTransaction.getId())
                .toUri();
        return ResponseEntity.created(location).body(createdTransaction);
    }

    @Operation(
        summary = "Get transaction by ID",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Transaction found",
                content = @Content(schema = @Schema(implementation = TransactionResponse.class)
                )
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Transaction not found",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)
                )
            ),
            @ApiResponse(
                responseCode = "422",
                description = "Validation error")
        }
    )
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransactionById(
        @Parameter(description = "ID транзакции", example = "123")
        @PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }
}
