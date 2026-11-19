package com.gulkower.resilient.idempotency;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    String keyPrefix() default "idempotency";
    long ttlSeconds() default 86400; // 24 hours
}
