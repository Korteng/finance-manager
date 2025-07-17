package ru.korteng.finance_manager.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class TransactionResponse {

    private Long id;

    private BigDecimal amount;

    private String currency;

    private String description;

    private Instant createdAt;

    private CategoryInfo category;

    @Data
    public static class CategoryInfo {

        private Long id;

        private String name;

        private Long parent_id;
    }

    public static TransactionResponse fromEntity(Transaction transaction) {

        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setAmount(transaction.getAmount());
        response.setCurrency(transaction.getCurrency());
        response.setDescription(transaction.getDescription());
        response.setCreatedAt(transaction.getCreatedAt());

        if (transaction.getCategory() != null) {
            CategoryInfo categoryInfo = new CategoryInfo();
            categoryInfo.setId(transaction.getCategory().getId());
            categoryInfo.setName(transaction.getCategory().getName());
            if (transaction.getCategory().getParent() != null) {
                categoryInfo.setParent_id(transaction.getCategory().getParent().getId());
            }
            response.setCategory(categoryInfo);
        }

        return response;
    }

}
