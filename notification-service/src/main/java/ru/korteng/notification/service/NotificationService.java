package ru.korteng.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ru.korteng.notification.entity.Notification;
import ru.korteng.notification.entity.NotificationStatus;
import ru.korteng.notification.model.TransactionEvent;
import ru.korteng.notification.repository.NotificationRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;

    public void process(TransactionEvent event) {
        Notification notification = new Notification();
        notification.setEventId(event.getEventId());
        notification.setUserId(event.getUserId());
        notification.setType(event.getEventType());
        notification.setMessage(buildMessage(event));
        notification.setStatus(NotificationStatus.PROCESSED);

        try {
            repository.save(notification);
        } catch (DataIntegrityViolationException e) {
            // eventId уже обработан ранее (unique constraint) — пропускаем дубль
            log.info("Duplicate event skipped, eventId={}", event.getEventId());
        }
    }

    private String buildMessage(TransactionEvent event) {
        var payload = event.getPayload();
        return "Транзакция: %s, категория: %s, сумма: %s"
                .formatted(payload.getType(), payload.getCategory(), payload.getAmount());
    }
}