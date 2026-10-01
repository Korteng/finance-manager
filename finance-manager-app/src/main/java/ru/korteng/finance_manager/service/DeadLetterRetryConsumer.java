package ru.korteng.finance_manager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.korteng.finance_manager.config.RabbitMQConfig;
import ru.korteng.finance_manager.event.TransactionEvent;

import java.util.List;
import java.util.Map;

/**
 * Читает события, которые Resilience4j не смог опубликовать в Kafka напрямую
 * (см. {@link TransactionEventPublisher#publishFallback}), и пробует снова.
 * <p>
 * Успех - ack, событие доставлено. Провал - смотрим на заголовок {@code x-death},
 * который RabbitMQ сам добавляет при каждом dead-letter: если попыток меньше лимита,
 * reject(requeue=false) уводит сообщение в retry-очередь на {@code retry-ttl-ms}
 * (см. {@link RabbitMQConfig}), откуда оно вернётся сюда же. Если лимит исчерпан -
 * событие публикуется в parked-очередь для ручного разбора, цикл авто-retry обрывается.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterRetryConsumer {

    private final TransactionEventPublisher eventPublisher;
    private final RabbitTemplate rabbitTemplate;
    private final MessageConverter messageConverter;

    @Value("${app-dead-letter.exchange}")
    private String deadLetterExchange;

    @Value("${app-dead-letter.parked-routing-key}")
    private String parkedRoutingKey;

    @Value("${app-dead-letter.max-retry-attempts}")
    private int maxRetryAttempts;

    @RabbitListener(queues = RabbitMQConfig.PROCESS_QUEUE)
    public void onMessage(Message message) {
        TransactionEvent event = (TransactionEvent) messageConverter.fromMessage(message);
        int attempt = deathCount(message) + 1;

        try {
            eventPublisher.publish(event);
            log.info("Dead-letter retry succeeded for {} event, eventId={}, attempt={}",
                    event.getEventType(), event.getEventId(), attempt);
        } catch (Exception ex) {
            if (attempt >= maxRetryAttempts) {
                log.error("Dead-letter retry exhausted ({} attempts) for {} event, eventId={} - parking for manual review",
                        maxRetryAttempts, event.getEventType(), event.getEventId());
                rabbitTemplate.convertAndSend(deadLetterExchange, parkedRoutingKey, event);
                return;
            }
            log.warn("Dead-letter retry attempt {}/{} failed for {} event, eventId={}: {}",
                    attempt, maxRetryAttempts, event.getEventType(), event.getEventId(), ex.getMessage());
            throw new AmqpRejectAndDontRequeueException("retry attempt " + attempt + " failed", ex);
        }
    }

    /**
     * Сколько раз сообщение уже прошло через dead-letter цикл process&#8594;retry&#8594;process,
     * по количеству записей в заголовке x-death (RabbitMQ добавляет новую запись на каждый
     * dead-letter того же сообщения через ту же очередь/причину).
     */
    @SuppressWarnings("unchecked")
    private int deathCount(Message message) {
        Object header = message.getMessageProperties().getHeaders().get("x-death");
        if (header instanceof List<?> deaths) {
            return deaths.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .filter(d -> RabbitMQConfig.PROCESS_QUEUE.equals(d.get("queue")))
                    .mapToInt(d -> ((Number) d.getOrDefault("count", 0L)).intValue())
                    .findFirst()
                    .orElse(0);
        }
        return 0;
    }
}
