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
import com.hireai.repository.ResumeRepository;
import com.hireai.repository.VectorSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobMatchService {

    private final VectorSearchRepository vectorSearchRepository;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final ApplicationRepository applicationRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

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

        List<Map<String, Object>> results = vectorSearchRepository.findMatchingCandidates(jobId, limit);
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
    }

    @Cacheable(value = "recommendedJobs", key = "#candidateId + '-' + #limit")
    @Transactional(readOnly = true)
    public List<MatchResultResponse> getRecommendedJobsForCandidate(Long candidateId, int limit) {
        List<Map<String, Object>> results = vectorSearchRepository.findMatchingJobs(candidateId, limit);
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
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "getMatchExplanationFallback")
    @Retry(name = "aiService")
    @Transactional(readOnly = true)
    public MatchResultResponse getMatchExplanation(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));

        Job job = application.getJob();
        Resume resume = application.getResume();

        if (resume == null || resume.getParsedData() == null) {
            throw new AiProcessingException("Resume not yet parsed for this application");
        }

        try {
            ParsedResume parsed = objectMapper.readValue(resume.getParsedData(), ParsedResume.class);

            BeanOutputConverter<MatchExplanation> converter = new BeanOutputConverter<>(MatchExplanation.class);

            String skillsStr = parsed.skills() != null ? String.join(", ", parsed.skills()) : "None";
            String experienceStr = parsed.experience() != null
                    ? parsed.experience().stream()
                        .map(e -> e.title() + " at " + e.company() + " (" + e.duration() + ")")
                        .reduce((a, b) -> a + "; " + b).orElse("None")
                    : "None";

            PromptTemplate template = PromptTemplate.builder()
                    .resource(matchExplainPrompt)
                    .build();

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

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            MatchExplanation explanation = converter.convert(response);
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
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate match explanation for application {}", applicationId, e);
            throw new AiProcessingException("Failed to generate match explanation", e);
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
