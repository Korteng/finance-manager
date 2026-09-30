package ru.korteng.finance_manager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Системные часы как Spring-бин: чтобы код, которому нужно "текущее время",
 * зависел от Clock, а не дёргал Instant.now()/LocalDate.now() напрямую.
 * <p>
 * Часовой пояс НЕ UTC намеренно: "период бюджета"/"месяц" должны совпадать с тем,
 * что пользователь видит на часах - иначе транзакция, созданная в 01:46 по Москве
 * 1 октября, по UTC (22:46 30 сентября) улетает в "сентябрьский" период, и бюджет
 * за октябрь её не видит (реальный баг, пойманный на стыке суток 30.09→01.10.2026).
 * В тестах подменяется на Clock.fixed(...).
 */
@Configuration
public class ClockConfig {

    @Value("${app.timezone:Europe/Moscow}")
    private String timezone;

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(timezone));
    }
}
