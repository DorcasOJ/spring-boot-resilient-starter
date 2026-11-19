package com.gulkower.resilient.telemetry;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpenTelemetryTracingCustomizer {
    String keyPrefix() default "idempotency:";
    long ttlSeconds() default 86400; // 24 hours
}
