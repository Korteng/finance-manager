package ru.korteng.finance_manager.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.utility.TestcontainersConfiguration;
import ru.korteng.finance_manager.dto.Transaction;
import ru.korteng.finance_manager.dto.TransactionRequest;
import ru.korteng.finance_manager.service.TransactionService;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(TestcontainersConfiguration.class)
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void createTransaction_ValidRequest_Returns201() throws Exception {
        Transaction mockTransaction = new Transaction();
        mockTransaction.setId(1L);  // Обязательно должен быть ID!
        mockTransaction.setAmount(new BigDecimal("500.00"));
        mockTransaction.setCurrency("RUB");
        when(transactionService.createTransaction(any(TransactionRequest.class)))
                .thenReturn(mockTransaction);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                    "amount": 500.0,
                    "currency": "RUB",
                    "categoryId": 1,
                    "description": "Тестовая транзакция"
                }
                """))
                .andExpect(status().isCreated());

        verify(transactionService).createTransaction(any(TransactionRequest.class));
    }

    @Test
    void createTransaction_InvalidAmount_Returns422() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "amount": -100.0,
                        "currency": "RUB",
                        "categoryId": 1,
                        "description": "Невалидная транзакция"
                    }
                    """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createTransaction_MissingCurrency_Returns422() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "amount": 500.0,
                        "categoryId": 1,
                        "description": "Кофе"
                    }
                    """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void whenInvalidAmount_thenReturnsCustomErrorResponse() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"amount\": -1 }"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }
}
