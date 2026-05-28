package com.hireai.service;

import com.hireai.ai.dto.CandidateScore;
import com.hireai.ai.dto.ParsedResume;
import com.hireai.ai.dto.ScreeningResult;
import com.hireai.domain.entity.Job;
import com.hireai.exception.AiProcessingException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI service for resume operations.
 *
 * OPENTELEMETRY LESSON — Manual Instrumentation:
 * ─────────────────────────────────────────────
 * Auto-instrumentation (HTTP, JDBC, Redis) happens for free via the OTel SDK.
 * But for AI calls we want CUSTOM SPANS with rich attributes so we can answer:
 *   - How long does each OpenAI call take?
 *   - What was the input size (prompt chars)?
 *   - What score did the AI return?
 *   - Which job/candidate was being processed?
 *
 * We use Micrometer's Tracer API (not OTel directly) because Spring Boot 3
 * abstracts over OTel via Micrometer Tracing. The bridge dependency we added
 * in pom.xml translates Micrometer spans → OTel spans automatically.
 *
 * Pattern:
 *   Span span = tracer.nextSpan().name("operation-name").start();
 *   try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
 *       span.tag("key", "value");   // add searchable attributes
 *       // ... do work ...
 *   } catch (Exception e) {
 *       span.error(e);              // marks span status = ERROR in Jaeger
 *       throw e;
 *   } finally {
 *       span.end();                 // ALWAYS end — even on exception
 *   }
 *
 * SpanInScope: sets this span as the "current" span in the thread-local context.
 * Any child spans created inside (e.g., HTTP call to OpenAI) will automatically
 * become children of this span in the trace tree.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeAIService {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;

    // Tracer is the entry point for manual span creation.
    // Spring Boot auto-configures this bean when micrometer-tracing-bridge-otel is on the classpath.
    private final Tracer tracer;

    @Value("classpath:prompts/resume-parse.st")
    private Resource resumeParsePrompt;

    @Value("classpath:prompts/resume-score.st")
    private Resource resumeScorePrompt;

    @Value("classpath:prompts/screening-check.st")
    private Resource screeningCheckPrompt;

    @CircuitBreaker(name = "aiService", fallbackMethod = "parseResumeFallback")
    @Retry(name = "aiService")
    public ParsedResume parseResume(String rawText) {
        log.info("AI: Parsing resume text ({} chars)", rawText.length());

        // Create a child span named "ai.resume.parse".
        // The dot-separated naming convention (component.entity.operation) is a common OTel standard.
        // This span will appear nested under the RabbitMQ consumer span in Jaeger.
        Span span = tracer.nextSpan().name("ai.resume.parse").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {

            // Tags (called "attributes" in OTel) are key-value pairs you can filter/search in Jaeger.
            // Use a consistent naming convention: component.attribute_name
            span.tag("ai.model", "gpt-4o-mini");
            span.tag("ai.operation", "resume_parse");
            span.tag("ai.input_chars", String.valueOf(rawText.length()));

            BeanOutputConverter<ParsedResume> converter = new BeanOutputConverter<>(ParsedResume.class);
            PromptTemplate template = PromptTemplate.builder().resource(resumeParsePrompt).build();
            String prompt = template.render(Map.of(
                    "resumeText", rawText,
                    "format", converter.getFormat()
            ));

            String response = chatClient.prompt().user(prompt).call().content();
            ParsedResume parsed = converter.convert(response);

            // Tag the result so you can see it directly in the span without reading logs
            span.tag("ai.result.name", parsed.name() != null ? parsed.name() : "unknown");
            span.tag("ai.result.skills_count", String.valueOf(
                    parsed.skills() != null ? parsed.skills().size() : 0));

            log.info("AI: Resume parsed successfully - name={}, skills={}", parsed.name(), parsed.skills().size());
            return parsed;

        } catch (Exception e) {
            // span.error() sets the span status to ERROR and records the exception.
            // In Jaeger you'll see a red span with the stack trace attached.
            span.error(e);
            log.error("AI: Failed to parse resume", e);
            throw new AiProcessingException("Failed to parse resume with AI", e);
        } finally {
            // CRITICAL: always call end() in finally — if you forget, the span leaks
            // and never appears in Jaeger (it stays "open" forever).
            span.end();
        }
    }

    @CircuitBreaker(name = "embeddingService", fallbackMethod = "generateEmbeddingFallback")
    @Retry(name = "embeddingService")
    public float[] generateEmbedding(String text) {
        log.info("AI: Generating embedding ({} chars)", text.length());

        Span span = tracer.nextSpan().name("ai.embedding.generate").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {

            span.tag("ai.model", "text-embedding-ada-002");
            span.tag("ai.operation", "embedding_generate");
            span.tag("ai.input_chars", String.valueOf(text.length()));

            String truncated = text.length() > 8000 ? text.substring(0, 8000) : text;
            float[] embedding = embeddingModel.embed(truncated);

            // Tag the output dimension — useful to confirm the model returned 1536-dim vectors
            span.tag("ai.result.dimensions", String.valueOf(embedding.length));

            return embedding;

        } catch (Exception e) {
            span.error(e);
            log.error("AI: Failed to generate embedding", e);
            throw new AiProcessingException("Failed to generate embedding", e);
        } finally {
            span.end();
        }
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "scoreCandidateFallback")
    @Retry(name = "aiService")
    public CandidateScore scoreCandidate(ParsedResume resume, Job job) {
        log.info("AI: Scoring candidate '{}' against job '{}'", resume.name(), job.getTitle());

        Span span = tracer.nextSpan().name("ai.candidate.score").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {

            span.tag("ai.model", "gpt-4o-mini");
            span.tag("ai.operation", "candidate_score");
            // Tag business context — makes it easy to find all spans for a specific job in Jaeger
            span.tag("job.title", job.getTitle());
            span.tag("job.id", String.valueOf(job.getId()));
            span.tag("candidate.name", resume.name() != null ? resume.name() : "unknown");

            BeanOutputConverter<CandidateScore> converter = new BeanOutputConverter<>(CandidateScore.class);
            PromptTemplate template = PromptTemplate.builder().resource(resumeScorePrompt).build();

            String skillsStr = resume.skills() != null ? String.join(", ", resume.skills()) : "None";
            String experienceStr = resume.experience() != null
                    ? resume.experience().stream()
                        .map(e -> e.title() + " at " + e.company() + " (" + e.duration() + ")")
                        .reduce((a, b) -> a + "; " + b).orElse("None")
                    : "None";

            String prompt = template.render(Map.of(
                    "candidateSkills", skillsStr,
                    "candidateExperience", experienceStr,
                    "jobTitle", job.getTitle(),
                    "mustHaveSkills", job.getMustHaveSkills() != null ? job.getMustHaveSkills() : "None specified",
                    "niceToHaveSkills", job.getNiceToHaveSkills() != null ? job.getNiceToHaveSkills() : "None specified",
                    "experienceLevel", job.getExperienceLevel() != null ? job.getExperienceLevel().name() : "Not specified",
                    "format", converter.getFormat()
            ));

            String response = chatClient.prompt().user(prompt).call().content();
            CandidateScore score = converter.convert(response);

            // Tag the AI score — you can now query Jaeger for all spans where ai.result.score < 50
            span.tag("ai.result.score", String.valueOf(score.score()));

            log.info("AI: Candidate scored {} for job '{}'", score.score(), job.getTitle());
            return score;

        } catch (Exception e) {
            span.error(e);
            log.error("AI: Failed to score candidate", e);
            throw new AiProcessingException("Failed to score candidate with AI", e);
        } finally {
            span.end();
        }
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "screenCandidateFallback")
    @Retry(name = "aiService")
    public ScreeningResult screenCandidate(ParsedResume resume, Job job) {
        log.info("AI: Screening candidate '{}' for job '{}'", resume.name(), job.getTitle());

        Span span = tracer.nextSpan().name("ai.candidate.screen").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {

            span.tag("ai.model", "gpt-4o-mini");
            span.tag("ai.operation", "candidate_screen");
            span.tag("job.title", job.getTitle());
            span.tag("job.id", String.valueOf(job.getId()));

            BeanOutputConverter<ScreeningResult> converter = new BeanOutputConverter<>(ScreeningResult.class);
            PromptTemplate template = PromptTemplate.builder().resource(screeningCheckPrompt).build();

            String prompt = template.render(Map.of(
                    "resumeSummary", resume.summary() != null ? resume.summary() : "Not available",
                    "candidateSkills", resume.skills() != null ? String.join(", ", resume.skills()) : "None",
                    "jobTitle", job.getTitle(),
                    "jobDescription", job.getDescription() != null ? job.getDescription() : "Not provided",
                    "mustHaveSkills", job.getMustHaveSkills() != null ? job.getMustHaveSkills() : "None specified",
                    "format", converter.getFormat()
            ));

            String response = chatClient.prompt().user(prompt).call().content();
            ScreeningResult result = converter.convert(response);

            // Tag the screening decision — filter Jaeger for all rejected candidates
            span.tag("ai.result.qualified", String.valueOf(result.qualified()));

            return result;

        } catch (Exception e) {
            span.error(e);
            log.error("AI: Failed to screen candidate", e);
            throw new AiProcessingException("Failed to screen candidate with AI", e);
        } finally {
            span.end();
        }
    }

    // --- Fallback methods ---

    private ParsedResume parseResumeFallback(String rawText, Throwable t) {
        log.warn("AI circuit breaker: parseResume fallback triggered: {}", t.getMessage());
        return new ParsedResume("Unknown", null, null, List.of(), List.of(), List.of(),
                "AI temporarily unavailable — queued for retry");
    }

    private float[] generateEmbeddingFallback(String text, Throwable t) {
        log.warn("AI circuit breaker: generateEmbedding fallback triggered: {}", t.getMessage());
        return new float[0];
    }

    private CandidateScore scoreCandidateFallback(ParsedResume resume, Job job, Throwable t) {
        log.warn("AI circuit breaker: scoreCandidate fallback triggered: {}", t.getMessage());
        return new CandidateScore(0, List.of(), List.of("AI temporarily unavailable"),
                "Scoring deferred — AI service unavailable");
    }

    private ScreeningResult screenCandidateFallback(ParsedResume resume, Job job, Throwable t) {
        log.warn("AI circuit breaker: screenCandidate fallback triggered: {}", t.getMessage());
        return new ScreeningResult(false, List.of(), List.of("AI temporarily unavailable"),
                "Screening deferred — AI service unavailable");
    }
}
