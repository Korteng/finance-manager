package ru.korteng.finance_manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Системные часы как Spring-бин: чтобы код, которому нужно "текущее время",
 * зависел от Clock, а не дёргал Instant.now()/LocalDate.now() напрямую.
 * В проде - системные часы в UTC, в тестах подменяется на Clock.fixed(...).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
