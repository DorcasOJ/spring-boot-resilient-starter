# ADR-001: Asymmetric Failure Policies (Fail-Closed vs. Fail-Open) and Unified Observability

* **Status:** Accepted
* **Date:** 2026-11-19
* **Author:** Dorcas OJ (`@dorcasoj`)
* **Context Reference:** *Designing Data-Intensive Applications* (Chapter 1: Faults vs. Failures, Tail Latency)

---

## 1. Context & Problem Statement

`spring-boot-resilient-starter` relies on Redis as a shared distributed state store for two distinct cross-cutting concerns:
1. **Idempotency enforcement (`@Idempotent`):** Ensuring state-mutating operations (such as payment charges, ledger transfers, and webhook ingestion) execute at most once despite client retries.
2. **Distributed rate limiting (`@RateLimit`):** Protecting downstream services and database connection pools from traffic spikes or noisy neighbors.

In a distributed environment, Redis is subject to transient network partitions, failover events, and tail-latency spikes (`p95`/`p99`). Treating all Redis failures uniformly creates two unacceptable risks:
* If `@Idempotent` bypasses Redis during an outage, concurrent retries can result in duplicate financial charges or corrupted state.
* If `@RateLimit` blocks all requests during an outage, a localized Redis fault cascades into a total API outage for legitimate users.

Additionally, consuming microservices require visibility into the latency overhead introduced by Redis round-trips without forcing developers to instrument metrics and distributed tracing separately.

---

## 2. Decision

We adopt **asymmetric failure handling policies** tailored to the consistency vs. availability requirements of each capability, paired with **Spring Boot's unified `ObservationRegistry` API**:

### A. `@Idempotent` — Strictly Fail-Closed
* **Behavior on Redis fault/timeout:** Abort request processing immediately before invoking the target method and return `HTTP 503 Service Unavailable` with a `Retry-After` header.
* **Rationale:** Financial and transactional correctness takes strict precedence over availability. Failing closed prevents duplicate execution while signaling upstream clients that the request was not processed and may be safely retried once infrastructure recovers.

### B. `@RateLimit` — Default Fail-Open (Configurable)
* **Behavior on Redis fault/timeout:** Default to `FailureMode.FAIL_OPEN`, logging a warning and allowing the request to proceed (optionally falling back to a local in-memory Bucket4j instance) rather than rejecting valid traffic.
* **Override:** Expose `fallback = FailureMode.FAIL_CLOSED` on the `@RateLimit` annotation for sensitive endpoints (e.g., authentication or OTP generation) where brute-force protection outweighs availability.
* **Rationale:** Prevents a non-critical infrastructure fault in the rate-limiting tier from causing a complete system failure.

### C. Tight Command Timeouts & Unified Observability
* **Timeout budget:** Recommend a strict Redis command timeout (`100ms–200ms`) in consuming services so hung connections fail fast rather than exhausting servlet worker threads and inflating `p99` tail latency.
* **Single-point instrumentation:** Wrap aspect and interceptor execution in Micrometer's `ObservationRegistry` (`resilient.idempotency` and `resilient.ratelimit`). This emits both **aggregated Micrometer timer histograms/counters** (for Prometheus `p50`/`p95`/`p99` dashboards) and **OpenTelemetry trace spans** (for per-request causal debugging) from a single instrumentation block.

---

## 3. Consequences

### Positive
* **Zero duplicate mutations:** Guarantees transactional safety during partial infrastructure failures.
* **Fault isolation:** A degraded Redis cluster will not take down read endpoints or standard API traffic protected only by `@RateLimit`.
* **Low observability friction:** Consuming microservices automatically gain both metrics and distributed traces without extra boilerplate.

### Negative / Trade-offs
* **Reduced write availability during Redis outages:** Mutating endpoints annotated with `@Idempotent` become temporarily unavailable until Redis recovers or fails over.
* **Temporary rate-limit relaxation:** During a Redis partition under `FAIL_OPEN`, global rate limits temporarily degrade to per-pod or unthrottled traffic until connectivity is restored.