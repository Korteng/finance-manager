package ru.korteng.notification.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.korteng.notification.model.TransactionEvent;
import ru.korteng.notification.service.NotificationService;

@Component
@RequiredArgsConstructor
public class TransactionEventListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = "transaction-events", groupId = "notification-service")
    public void handle(TransactionEvent event) {
        notificationService.process(event);
    }
}
