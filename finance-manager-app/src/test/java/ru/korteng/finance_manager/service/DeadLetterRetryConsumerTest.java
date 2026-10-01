package ru.korteng.finance_manager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import ru.korteng.finance_manager.config.RabbitMQConfig;
import ru.korteng.finance_manager.event.TransactionEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadLetterRetryConsumerTest {

    @Mock
    private TransactionEventPublisher eventPublisher;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private MessageConverter messageConverter;

    private DeadLetterRetryConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DeadLetterRetryConsumer(eventPublisher, rabbitTemplate, messageConverter);
        ReflectionTestUtils.setField(consumer, "deadLetterExchange", "transaction-events.dlx.exchange");
        ReflectionTestUtils.setField(consumer, "parkedRoutingKey", "parked");
        ReflectionTestUtils.setField(consumer, "maxRetryAttempts", 3);
    }

    private TransactionEvent event() {
        TransactionEvent event = new TransactionEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("TRANSACTION_CREATED");
        event.setUserId(1L);
        return event;
    }

    private Message message(int deathCount) {
        MessageProperties properties = new MessageProperties();
        if (deathCount > 0) {
            properties.setHeader("x-death", List.of(
                    Map.of("queue", RabbitMQConfig.PROCESS_QUEUE, "count", (long) deathCount)
            ));
        }
        return new Message(new byte[0], properties);
    }

    @Test
    void onMessage_Success_DoesNotRejectOrPark() {
        Message message = message(0);
        TransactionEvent event = event();
        when(messageConverter.fromMessage(message)).thenReturn(event);

        assertThatCode(() -> consumer.onMessage(message)).doesNotThrowAnyException();

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), eq(event));
    }

    @Test
    void onMessage_FailureBelowMaxAttempts_RejectsWithoutRequeue() {
        Message message = message(1);
        TransactionEvent event = event();
        when(messageConverter.fromMessage(message)).thenReturn(event);
        doThrow(new RuntimeException("kafka still down")).when(eventPublisher).publish(event);

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), eq(event));
    }

    @Test
    void onMessage_FailureAtMaxAttempts_ParksEventInsteadOfRejecting() {
        Message message = message(2); // attempt = deathCount(2) + 1 = 3 == maxRetryAttempts
        TransactionEvent event = event();
        when(messageConverter.fromMessage(message)).thenReturn(event);
        doThrow(new RuntimeException("kafka still down")).when(eventPublisher).publish(event);

        assertThatCode(() -> consumer.onMessage(message)).doesNotThrowAnyException();

        verify(rabbitTemplate).convertAndSend("transaction-events.dlx.exchange", "parked", event);
    }
}
