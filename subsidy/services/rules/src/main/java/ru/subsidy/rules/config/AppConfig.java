package ru.subsidy.rules.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Планировщик перепроверок и настройки.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RulesProperties.class)
public class AppConfig {
}
