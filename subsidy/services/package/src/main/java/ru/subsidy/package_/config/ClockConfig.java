package ru.subsidy.package_.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Часы инжектируются (RULES: никакого now() без Clock).
 */
@Configuration
public class ClockConfig {

    /**
     * Системные часы UTC.
     *
     * @return часы
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
