package ru.korteng.finance_manager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import ru.korteng.finance_manager.event.TransactionEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * publish() дергает Resilience4j-аннотации (@CircuitBreaker/@Retry) только через
 * Spring AOP прокси - здесь тестируется сам метод напрямую (голая бизнес-логика),
 * поведение circuit breaker/retry - предмет интеграционного теста с поднятым контекстом.
 */
@ExtendWith(MockitoExtension.class)
class TransactionEventPublisherTest {

    @Mock
    private KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private TransactionEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new TransactionEventPublisher(kafkaTemplate, rabbitTemplate);
        // @Value-поля Spring заполняет только через контейнер - здесь бин создаётся
        // вручную (без контекста), поэтому проставляем их так же, как это сделал бы
        // application.yml, иначе publishFallback() зовёт convertAndSend(null, null, ...).
        ReflectionTestUtils.setField(publisher, "deadLetterExchange", "transaction-events.dlx.exchange");
        ReflectionTestUtils.setField(publisher, "processRoutingKey", "process");
    }

    private TransactionEvent event() {
        TransactionEvent event = new TransactionEvent();
        event.setEventId(UUID.randomUUID());
        event.setEventType("TRANSACTION_CREATED");
        event.setUserId(1L);
        return event;
    }

    @Test
    void publish_SendsEventToKafkaWithEventIdAsKey() {
        TransactionEvent event = event();
        when(kafkaTemplate.send(anyString(), anyString(), any(TransactionEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publish(event);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("transaction-events"), keyCaptor.capture(), eq(event));
        assertThat(keyCaptor.getValue()).isEqualTo(event.getEventId().toString());
    }

    @Test
    void publishFallback_DoesNotThrow_WhenRetriesExhaustedOrCircuitOpen() {
        TransactionEvent event = event();
        RuntimeException cause = new RuntimeException("kafka broker unavailable");

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(publisher, "publishFallback", event, cause))
                .doesNotThrowAnyException();
    }

    @Test
    void publishFallback_RoutesEventToDeadLetterExchange() {
        TransactionEvent event = event();

        ReflectionTestUtils.invokeMethod(publisher, "publishFallback", event, new RuntimeException("boom"));

        verify(rabbitTemplate).convertAndSend("transaction-events.dlx.exchange", "process", event);
    }
}
