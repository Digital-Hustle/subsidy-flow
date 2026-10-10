package ru.subsidy.package_.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ru.subsidy.package_.upload.UploadLimits;

/**
 * Лимиты приёма из окружения.
 */
@Configuration
public class UploadConfig {

    private static final long MEGABYTE = 1024L * 1024L;

    /**
     * Лимиты.
     *
     * @param maxMb UPLOAD_MAX_MB
     * @param maxPages UPLOAD_MAX_PAGES
     * @return лимиты
     */
    @Bean
    public UploadLimits uploadLimits(@Value("${UPLOAD_MAX_MB:30}") long maxMb,
                                     @Value("${UPLOAD_MAX_PAGES:60}") int maxPages) {
        return new UploadLimits(maxMb * MEGABYTE, maxPages);
    }
}
