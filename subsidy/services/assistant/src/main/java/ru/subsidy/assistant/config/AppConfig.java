package ru.subsidy.assistant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Настройки помощника.
 */
@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AppConfig {
}
