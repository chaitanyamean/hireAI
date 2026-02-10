package com.hireai.service;

import com.hireai.ai.dto.CandidateScore;
import com.hireai.ai.dto.ParsedResume;
import com.hireai.ai.dto.ScreeningResult;
import com.hireai.domain.entity.Job;
import com.hireai.exception.AiProcessingException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeAIService {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;

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
        try {
            BeanOutputConverter<ParsedResume> converter = new BeanOutputConverter<>(ParsedResume.class);

            PromptTemplate template = PromptTemplate.builder()
                    .resource(resumeParsePrompt)
                    .build();

            String prompt = template.render(Map.of(
                    "resumeText", rawText,
                    "format", converter.getFormat()
            ));

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            ParsedResume parsed = converter.convert(response);
            log.info("AI: Resume parsed successfully - name={}, skills={}", parsed.name(), parsed.skills().size());
            return parsed;
        } catch (Exception e) {
            log.error("AI: Failed to parse resume", e);
            throw new AiProcessingException("Failed to parse resume with AI", e);
        }
    }

    @CircuitBreaker(name = "embeddingService", fallbackMethod = "generateEmbeddingFallback")
    @Retry(name = "embeddingService")
    public float[] generateEmbedding(String text) {
        log.info("AI: Generating embedding ({} chars)", text.length());
        try {
            String truncated = text.length() > 8000 ? text.substring(0, 8000) : text;
            return embeddingModel.embed(truncated);
        } catch (Exception e) {
            log.error("AI: Failed to generate embedding", e);
            throw new AiProcessingException("Failed to generate embedding", e);
        }
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "scoreCandidateFallback")
    @Retry(name = "aiService")
    public CandidateScore scoreCandidate(ParsedResume resume, Job job) {
        log.info("AI: Scoring candidate '{}' against job '{}'", resume.name(), job.getTitle());
        try {
            BeanOutputConverter<CandidateScore> converter = new BeanOutputConverter<>(CandidateScore.class);

            PromptTemplate template = PromptTemplate.builder()
                    .resource(resumeScorePrompt)
                    .build();

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

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            CandidateScore score = converter.convert(response);
            log.info("AI: Candidate scored {} for job '{}'", score.score(), job.getTitle());
            return score;
        } catch (Exception e) {
            log.error("AI: Failed to score candidate", e);
            throw new AiProcessingException("Failed to score candidate with AI", e);
        }
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "screenCandidateFallback")
    @Retry(name = "aiService")
    public ScreeningResult screenCandidate(ParsedResume resume, Job job) {
        log.info("AI: Screening candidate '{}' for job '{}'", resume.name(), job.getTitle());
        try {
            BeanOutputConverter<ScreeningResult> converter = new BeanOutputConverter<>(ScreeningResult.class);

            PromptTemplate template = PromptTemplate.builder()
                    .resource(screeningCheckPrompt)
                    .build();

            String prompt = template.render(Map.of(
                    "resumeSummary", resume.summary() != null ? resume.summary() : "Not available",
                    "candidateSkills", resume.skills() != null ? String.join(", ", resume.skills()) : "None",
                    "jobTitle", job.getTitle(),
                    "jobDescription", job.getDescription() != null ? job.getDescription() : "Not provided",
                    "mustHaveSkills", job.getMustHaveSkills() != null ? job.getMustHaveSkills() : "None specified",
                    "format", converter.getFormat()
            ));

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return converter.convert(response);
        } catch (Exception e) {
            log.error("AI: Failed to screen candidate", e);
            throw new AiProcessingException("Failed to screen candidate with AI", e);
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
