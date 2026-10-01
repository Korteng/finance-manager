package ru.korteng.finance_manager.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dead-letter цепочка для событий, которые не удалось опубликовать в Kafka даже после
 * Resilience4j circuit breaker + retry (см. {@link ru.korteng.finance_manager.service.TransactionEventPublisher}).
 * <p>
 * Схема (classic RabbitMQ TTL+DLX delayed-retry):
 * <pre>
 *   publishFallback() --> [process queue] --consumer пробует Kafka снова-->
 *     успех: ack, событие доставлено
 *     фейл, попыток < max: reject(requeue=false) --DLX--> [retry-wait queue]
 *         --TTL истёк--DLX--> обратно в [process queue] (цикл)
 *     фейл, попыток >= max: вручную публикуется в [parked queue], ack
 *         (финальная парковка для ручного разбора/replay, авто-retry прекращён)
 * </pre>
 * x-death заголовок, который RabbitMQ сам проставляет при каждом dead-letter,
 * используется консьюмером ({@code DeadLetterRetryConsumer}) для подсчёта попыток -
 * без отдельного персистентного счётчика.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${app-dead-letter.exchange}")
    private String exchangeName;

    @Value("${app-dead-letter.process-routing-key}")
    private String processRoutingKey;

    @Value("${app-dead-letter.retry-routing-key}")
    private String retryRoutingKey;

    @Value("${app-dead-letter.parked-routing-key}")
    private String parkedRoutingKey;

    @Value("${app-dead-letter.retry-ttl-ms}")
    private long retryTtlMs;

    public static final String PROCESS_QUEUE = "transaction-events.process";
    public static final String RETRY_QUEUE = "transaction-events.retry";
    public static final String PARKED_QUEUE = "transaction-events.parked";

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    /**
     * Сюда консьюмер пробует повторно опубликовать событие в Kafka.
     * При reject(requeue=false) сообщение уходит в retry-очередь через DLX.
     */
    @Bean
    public Queue processQueue() {
        return QueueBuilder.durable(PROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", exchangeName)
                .withArgument("x-dead-letter-routing-key", retryRoutingKey)
                .build();
    }

    /**
     * "Комната ожидания": сообщение лежит здесь retryTtlMs, затем RabbitMQ сам
     * перекладывает его обратно в process-очередь через тот же DLX-механизм.
     */
    @Bean
    public Queue retryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .withArgument("x-message-ttl", retryTtlMs)
                .withArgument("x-dead-letter-exchange", exchangeName)
                .withArgument("x-dead-letter-routing-key", processRoutingKey)
                .build();
    }

    /**
     * Финальная парковка после исчерпания попыток - без TTL/DLX, отсюда автоматического
     * пути назад нет, требуется ручной разбор (или отдельный consumer под replay/алертинг).
     */
    @Bean
    public Queue parkedQueue() {
        return QueueBuilder.durable(PARKED_QUEUE).build();
    }

    @Bean
    public Binding processBinding(Queue processQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(processQueue).to(deadLetterExchange).with(processRoutingKey);
    }

    @Bean
    public Binding retryBinding(Queue retryQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(retryQueue).to(deadLetterExchange).with(retryRoutingKey);
    }

    @Bean
    public Binding parkedBinding(Queue parkedQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(parkedQueue).to(deadLetterExchange).with(parkedRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
