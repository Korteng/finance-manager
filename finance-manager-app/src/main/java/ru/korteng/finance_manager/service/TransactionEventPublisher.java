package ru.korteng.finance_manager.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.korteng.finance_manager.event.TransactionEvent;

/**
 * Публикация событий в Kafka, обёрнутая в circuit breaker + retry.
 * <p>
 * Вынесено отдельным бином намеренно: Resilience4j-аннотации построены на Spring AOP
 * (динамический прокси), который перехватывает только вызовы ИЗВНЕ бина. Self-invocation
 * (метод класса вызывает другой метод того же класса через {@code this.method()}) прокси
 * не проходит — аннотация на приватном/внутреннем методе просто не сработает.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventPublisher {

    private static final String TOPIC = "transaction-events";

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app-dead-letter.exchange}")
    private String deadLetterExchange;

    @Value("${app-dead-letter.process-routing-key}")
    private String processRoutingKey;

    @CircuitBreaker(name = "kafkaPublisher", fallbackMethod = "publishFallback")
    @Retry(name = "kafkaPublisher")
    public void publish(TransactionEvent event) {
        kafkaTemplate.send(TOPIC, event.getEventId().toString(), event);
        log.info("Published {} event, eventId={}", event.getEventType(), event.getEventId());
    }

    /**
     * Срабатывает, когда все retry-попытки исчерпаны ИЛИ circuit breaker открыт (fail-fast,
     * без похода в Kafka вообще). Событие не теряется: уходит в RabbitMQ dead-letter
     * retry-цепочку (см. {@link ru.korteng.finance_manager.config.RabbitMQConfig}) -
     * TTL-based повторные попытки, после исчерпания лимита событие паркуется для
     * ручного разбора/replay. Это отдельный уровень надёжности от Resilience4j
     * (тот отвечает за быстрый локальный retry+circuit breaker, этот - за то, чтобы
     * событие вообще не потерялось, если тот уровень не справился).
     */
    private void publishFallback(TransactionEvent event, Throwable t) {
        log.error("Failed to publish {} event after retries/circuit open, eventId={}: {} - routing to dead-letter retry queue",
                event.getEventType(), event.getEventId(), t.getMessage(), t);
        rabbitTemplate.convertAndSend(deadLetterExchange, processRoutingKey, event);
    }
}
