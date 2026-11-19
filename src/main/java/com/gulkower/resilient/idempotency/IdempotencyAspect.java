package com.gulkower.resilient.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Aspect
public class IdempotencyAspect {
    private static final String IDEMPOTENCY_HEADER ="Idempotency-Key";
    private final StringRedisTemplate redisTemplate;

    public IdempotencyAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
//       Extract idempotency key from request headers
        String requestId = resolveRequestId(joinPoint);
        String redisKey = idempotent.keyPrefix() + requestId;

        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "LOCKED", Duration.ofSeconds(idempotent.ttlSeconds()));
        if (Boolean.FALSE.equals(isNew)) {
            throw new IllegalStateException("Duplicate request detected: " + requestId);
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            redisTemplate.delete(redisKey);
            throw e;
        }

    }

    private String resolveRequestId(ProceedingJoinPoint joinPoint) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("@Idempotent can only be used within an active HTTP request context");
        }
        HttpServletRequest request = attributes.getRequest();
        String key = request.getHeader(IDEMPOTENCY_HEADER);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Missing required header: " + IDEMPOTENCY_HEADER);
        }
        return key.trim();
//        return "default-key";// Implementation to parse HttpServletRequest header or method parameter annotated with @RequestHeader
    }
}
