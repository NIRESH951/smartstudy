package com.smartstudy.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a single UTC {@link Clock}. Services never call {@code Instant.now()} directly
 * for business decisions, which makes date-boundary logic deterministic in tests.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
