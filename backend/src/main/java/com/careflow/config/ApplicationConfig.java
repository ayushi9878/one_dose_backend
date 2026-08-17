package com.careflow.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(CareFlowProperties.class)
public class ApplicationConfig {

    /**
     * Injected wherever the current date matters so time-dependent workflow rules
     * stay deterministic under test.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
