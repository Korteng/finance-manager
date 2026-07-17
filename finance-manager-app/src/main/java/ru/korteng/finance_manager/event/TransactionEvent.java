package ru.korteng.finance_manager.event;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class TransactionEvent {
    private UUID eventId;
    private String eventType;
    private Long userId;
    private Payload payload;
    private Instant occurredAt;

    @Getter
    @Setter
    public static class Payload {
        private BigDecimal amount;
        private String category;
        private String type;
    }
}