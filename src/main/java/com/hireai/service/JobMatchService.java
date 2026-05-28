package com.hireai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireai.ai.dto.MatchExplanation;
import com.hireai.ai.dto.ParsedResume;
import com.hireai.domain.dto.response.MatchResultResponse;
import com.hireai.domain.entity.Application;
import com.hireai.domain.entity.Job;
import com.hireai.domain.entity.Resume;
import com.hireai.exception.AiProcessingException;
import com.hireai.exception.ResourceNotFoundException;
import com.hireai.repository.ApplicationRepository;
import com.hireai.repository.JobRepository;
import com.hireai.repository.VectorSearchRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Job matching service — vector search + AI explanation.
 *
 * OPENTELEMETRY LESSON — Tracing Across Cache + DB:
 * ──────────────────────────────────────────────────
 * The @Cacheable methods are interesting to trace because:
 *   - On a CACHE HIT:  the span will be very short (< 1ms) — Redis returned the result
 *   - On a CACHE MISS: the span will be longer — it hit pgvector for cosine similarity
 *
 * You can see this difference directly in Jaeger's timeline without any extra code,
 * because the JDBC auto-instrumentation creates child spans for every SQL query.
 * A cache hit trace will have NO child DB spans; a cache miss will show the pgvector query.
 *
 * We add a custom span around the vector search to tag the result count and job ID,
 * making it easy to compare search performance across different jobs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobMatchService {

    private final VectorSearchRepository vectorSearchRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    @Value("classpath:prompts/match-explain.st")
    private Resource matchExplainPrompt;

    @Cacheable(value = "topCandidates", key = "#jobId + '-' + #limit")
    @Transactional(readOnly = true)
    public List<MatchResultResponse> getTopCandidatesForJob(Long jobId, int limit) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", jobId));

        if (!vectorSearchRepository.hasJobEmbedding(jobId)) {
            throw new AiProcessingException("Job embedding not yet generated. Please wait and try again.");
        }

        // Custom span for the vector search operation.
        // The JDBC auto-instrumentation will create a child span for the actual SQL,
        // so in Jaeger you'll see: vector.search.candidates → SQL (pgvector cosine query)
        Span span = tracer.nextSpan().name("vector.search.candidates").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {

            span.tag("job.id", String.valueOf(jobId));
            span.tag("job.title", job.getTitle());
            span.tag("search.limit", String.valueOf(limit));

            List<Map<String, Object>> results = vectorSearchRepository.findMatchingCandidates(jobId, limit);

            span.tag("search.results_count", String.valueOf(results.size()));
            log.info("Vector search found {} matching candidates for job {}", results.size(), jobId);

            return results.stream().map(row -> {
                BigDecimal similarity = toBigDecimal(row.get("similarity"));
                return MatchResultResponse.builder()
                        .candidateId(toLong(row.get("candidate_id")))
                        .jobId(jobId)
                        .similarityScore(similarity.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP))
                        .aiExplanation("Skills: " + row.get("skills") + " | " + row.get("experience_summary"))
                        .build();
            }).toList();

        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Cacheable(value = "recommendedJobs", key = "#candidateId + '-' + #limit")
    @Transactional(readOnly = true)
    public List<MatchResultResponse> getRecommendedJobsForCandidate(Long candidateId, int limit) {
        Span span = tracer.nextSpan().name("vector.search.jobs").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {

            span.tag("candidate.id", String.valueOf(candidateId));
            span.tag("search.limit", String.valueOf(limit));

            List<Map<String, Object>> results = vectorSearchRepository.findMatchingJobs(candidateId, limit);

            span.tag("search.results_count", String.valueOf(results.size()));
            log.info("Vector search found {} matching jobs for candidate {}", results.size(), candidateId);

            return results.stream().map(row -> {
                BigDecimal similarity = toBigDecimal(row.get("similarity"));
                return MatchResultResponse.builder()
                        .candidateId(candidateId)
                        .jobId(toLong(row.get("job_id")))
                        .similarityScore(similarity.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP))
                        .aiExplanation(row.get("title") + " | Skills: " + row.get("must_have_skills"))
                        .build();
            }).toList();

        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "getMatchExplanationFallback")
    @Retry(name = "aiService")
    @Transactional(readOnly = true)
    public MatchResultResponse getMatchExplanation(Long applicationId) {
        Span span = tracer.nextSpan().name("ai.match.explain").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {

            span.tag("ai.model", "gpt-4o-mini");
            span.tag("ai.operation", "match_explain");
            span.tag("application.id", String.valueOf(applicationId));

            Application application = applicationRepository.findById(applicationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));

            Job job = application.getJob();
            Resume resume = application.getResume();

            span.tag("job.title", job.getTitle());
            span.tag("job.id", String.valueOf(job.getId()));

            if (resume == null || resume.getParsedData() == null) {
                throw new AiProcessingException("Resume not yet parsed for this application");
            }

            ParsedResume parsed = objectMapper.readValue(resume.getParsedData(), ParsedResume.class);

            BeanOutputConverter<MatchExplanation> converter = new BeanOutputConverter<>(MatchExplanation.class);

            String skillsStr = parsed.skills() != null ? String.join(", ", parsed.skills()) : "None";
            String experienceStr = parsed.experience() != null
                    ? parsed.experience().stream()
                        .map(e -> e.title() + " at " + e.company() + " (" + e.duration() + ")")
                        .reduce((a, b) -> a + "; " + b).orElse("None")
                    : "None";

            PromptTemplate template = PromptTemplate.builder().resource(matchExplainPrompt).build();
            String prompt = template.render(Map.of(
                    "jobTitle", job.getTitle(),
                    "jobDescription", job.getDescription() != null ? job.getDescription() : "Not provided",
                    "mustHaveSkills", job.getMustHaveSkills() != null ? job.getMustHaveSkills() : "None",
                    "niceToHaveSkills", job.getNiceToHaveSkills() != null ? job.getNiceToHaveSkills() : "None",
                    "experienceLevel", job.getExperienceLevel() != null ? job.getExperienceLevel().name() : "Not specified",
                    "candidateSkills", skillsStr,
                    "candidateExperience", experienceStr,
                    "candidateSummary", parsed.summary() != null ? parsed.summary() : "Not available",
                    "format", converter.getFormat()
            ));

            span.event("openai.request.sent");
            String response = chatClient.prompt().user(prompt).call().content();
            span.event("openai.response.received");

            MatchExplanation explanation = converter.convert(response);
            span.tag("ai.result.score", String.valueOf(explanation.score()));

            log.info("Match explanation generated for application {}: score={}", applicationId, explanation.score());

            return MatchResultResponse.builder()
                    .candidateId(application.getCandidate().getId())
                    .jobId(job.getId())
                    .similarityScore(BigDecimal.valueOf(explanation.score()))
                    .aiExplanation(explanation.explanation()
                            + (explanation.highlights() != null
                                ? "\n\nHighlights: " + String.join(", ", explanation.highlights())
                                : ""))
                    .build();

        } catch (AiProcessingException e) {
            span.error(e);
            throw e;
        } catch (Exception e) {
            span.error(e);
            log.error("Failed to generate match explanation for application {}", applicationId, e);
            throw new AiProcessingException("Failed to generate match explanation", e);
        } finally {
            span.end();
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Double d) return BigDecimal.valueOf(d);
        if (value instanceof Float f) return BigDecimal.valueOf(f);
        return BigDecimal.ZERO;
    }

    private Long toLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        return 0L;
    }

    // --- Fallback methods ---

    private MatchResultResponse getMatchExplanationFallback(Long applicationId, Throwable t) {
        log.warn("AI circuit breaker: getMatchExplanation fallback triggered for application {}: {}", applicationId, t.getMessage());
        return MatchResultResponse.builder()
                .candidateId(0L)
                .jobId(0L)
                .similarityScore(BigDecimal.ZERO)
                .aiExplanation("AI service temporarily unavailable — match explanation pending")
                .build();
    }
}
