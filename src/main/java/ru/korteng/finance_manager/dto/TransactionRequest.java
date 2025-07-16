package ru.korteng.finance_manager.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionRequest {

    private BigDecimal amount;
    private String currency;
    private Long categoryId;
    private String description;
}
