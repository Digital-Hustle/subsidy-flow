package ru.subsidy.docproc.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Планировщик воркеров и настройки конвейера.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(DocprocProperties.class)
public class AppConfig {
}
