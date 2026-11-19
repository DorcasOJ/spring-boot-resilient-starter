package com.gulkower.resilient;

import com.gulkower.resilient.idempotency.IdempotencyAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
public class ResilientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyAspect idempotencyAspect(StringRedisTemplate redisTemplate) {
        return new IdempotencyAspect(redisTemplate);
    }
}
