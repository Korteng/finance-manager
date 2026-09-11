package ru.korteng.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import ru.korteng.notification.entity.Notification;
import ru.korteng.notification.entity.NotificationStatus;
import ru.korteng.notification.model.TransactionEvent;
import ru.korteng.notification.repository.NotificationRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @InjectMocks
    private NotificationService notificationService;

    private TransactionEvent buildEvent() {
        TransactionEvent event = new TransactionEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("TRANSACTION_CREATED");
        event.setUserId(7L);
        event.setOccurredAt(Instant.now());

        TransactionEvent.Payload payload = new TransactionEvent.Payload();
        payload.setAmount(new BigDecimal("500.00"));
        payload.setCategory("Еда");
        payload.setType("EXPENSE");
        event.setPayload(payload);

        return event;
    }

    @Test
    void process_ValidEvent_SavesNotificationWithProcessedStatusAndFormattedMessage() {
        TransactionEvent event = buildEvent();
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        notificationService.process(event);

        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(event.getEventId());
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getType()).isEqualTo("TRANSACTION_CREATED");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PROCESSED);
        assertThat(saved.getMessage()).isEqualTo("Транзакция: EXPENSE, категория: Еда, сумма: 500.00");
    }

    @Test
    void process_DuplicateEvent_SwallowsDataIntegrityViolationWithoutPropagating() {
        TransactionEvent event = buildEvent();
        when(repository.save(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key: event_id"));

        assertDoesNotThrow(() -> notificationService.process(event));

        verify(repository).save(any(Notification.class));
    }
}