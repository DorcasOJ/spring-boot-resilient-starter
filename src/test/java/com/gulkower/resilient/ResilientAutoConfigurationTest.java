package com.gulkower.resilient;

import com.gulkower.resilient.idempotency.IdempotencyAspect;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import static org.junit.jupiter.api.Assertions.*;

class ResilientAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(
                    AutoConfigurations.of(ResilientAutoConfiguration.class)
            );

    @Test
    void registerIdempotencyAspectWhenRedisTemplateIsPresent() {
        contextRunner
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(context -> assertThat(context).hasSingleBean(IdempotencyAspect.class));
    }

    @Test
    void backsOffWhenCustomIdempotencyAspectIsDefined() {
        contextRunner
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withUserConfiguration(CustomIdempotencyConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyAspect.class);
                    assertThat(context).hasBean("customIdempotencyAspect");
                });
    }

    @Configuration
    static class CustomIdempotencyConfig {
        @Bean
        IdempotencyAspect customIdempotencyAspect(StringRedisTemplate redisTemplate) {
            return new IdempotencyAspect(redisTemplate);
        }
    }
}