package com.hireai.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom Micrometer metrics for HireAI.
 *
 * OPENTELEMETRY LESSON — Metrics vs Traces:
 * ──────────────────────────────────────────
 * Traces answer "WHAT happened in this specific request?"
 * Metrics answer "HOW MANY / HOW OFTEN / HOW LONG across ALL requests?"
 *
 * You need both:
 *   - Metrics tell you "AI parse is failing 20% of the time" (alert on this)
 *   - Traces tell you "HERE is the specific request that failed and why"
 *
 * Micrometer metric types:
 *
 *   Counter  — monotonically increasing number. Use for: requests, errors, events.
 *              Example: ai_calls_total{operation="resume_parse", status="success"}
 *
 *   Timer    — measures duration + count. Use for: latency of operations.
 *              Automatically gives you: count, sum, max, p50, p95, p99 percentiles.
 *              Example: ai_call_duration_seconds{operation="resume_parse"}
 *
 *   Gauge    — current value that can go up or down. Use for: queue depth, active connections.
 *              Example: rabbitmq_queue_depth{queue="resume.parse"}
 *
 * These metrics are exported to Prometheus via /actuator/prometheus.
 * The OTel Collector also receives them and makes them available in Jaeger.
 *
 * HOW TO USE in a service:
 *   @Autowired Counter aiCallSuccessCounter;
 *   aiCallSuccessCounter.increment();   // after a successful AI call
 *
 *   @Autowired Timer aiCallTimer;
 *   aiCallTimer.record(() -> callOpenAI());  // wraps the call and records duration
 */
@Configuration
public class ObservabilityConfig {

    /**
     * Counts successful AI calls by operation type.
     * In Prometheus: ai_calls_total{status="success"}
     *
     * Tags (called "labels" in Prometheus) let you slice the metric.
     * We use a generic counter here; in the services we use MeterRegistry
     * directly to add the operation tag dynamically.
     */
    @Bean
    public Counter aiCallSuccessCounter(MeterRegistry registry) {
        return Counter.builder("ai.calls")
                .description("Total number of AI API calls")
                .tag("status", "success")
                .register(registry);
    }

    /**
     * Counts failed AI calls (circuit breaker opened, timeout, API error).
     * In Prometheus: ai_calls_total{status="error"}
     *
     * Alert rule example (Prometheus):
     *   rate(ai_calls_total{status="error"}[5m]) / rate(ai_calls_total[5m]) > 0.1
     *   → "AI error rate exceeded 10% in the last 5 minutes"
     */
    @Bean
    public Counter aiCallErrorCounter(MeterRegistry registry) {
        return Counter.builder("ai.calls")
                .description("Total number of AI API calls")
                .tag("status", "error")
                .register(registry);
    }

    /**
     * Measures how long AI calls take.
     * In Prometheus: ai_call_duration_seconds_bucket, _count, _sum
     *
     * Spring Boot auto-configures percentile histograms when you set:
     *   management.metrics.distribution.percentiles-histogram.ai.call.duration=true
     *
     * This gives you p50, p95, p99 latency in Prometheus/Grafana.
     */
    @Bean
    public Timer aiCallTimer(MeterRegistry registry) {
        return Timer.builder("ai.call.duration")
                .description("Duration of AI API calls to OpenAI")
                .publishPercentileHistogram()   // enables p50/p95/p99 in Prometheus
                .register(registry);
    }

    /**
     * Counts resume parse operations by result.
     * In Prometheus: resume_parse_total{result="success|error|fallback"}
     */
    @Bean
    public Counter resumeParseCounter(MeterRegistry registry) {
        return Counter.builder("resume.parse")
                .description("Resume parse operations")
                .tag("result", "success")
                .register(registry);
    }

    /**
     * Counts vector similarity searches.
     * In Prometheus: vector_search_total{type="candidates|jobs"}
     *
     * Useful for understanding how often the matching feature is used
     * and whether results are being served from cache (short duration)
     * or from pgvector (longer duration).
     */
    @Bean
    public Counter vectorSearchCounter(MeterRegistry registry) {
        return Counter.builder("vector.search")
                .description("pgvector cosine similarity searches")
                .tag("type", "candidates")
                .register(registry);
    }
}
