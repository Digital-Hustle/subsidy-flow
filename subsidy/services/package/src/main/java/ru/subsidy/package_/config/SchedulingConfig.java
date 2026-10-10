package ru.subsidy.package_.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Планировщик: relay outbox и heartbeat SSE.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
