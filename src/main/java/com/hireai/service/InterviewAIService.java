package com.hireai.service;

import com.hireai.ai.dto.AnswerEvaluation;
import com.hireai.ai.dto.InterviewQuestions;
import com.hireai.ai.dto.InterviewSummary;
import com.hireai.domain.entity.Interview;
import com.hireai.domain.entity.InterviewQuestion;
import com.hireai.domain.entity.InterviewResponse;
import com.hireai.domain.entity.Job;
import com.hireai.domain.enums.InterviewType;
import com.hireai.exception.AiProcessingException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewAIService {

    private final ChatClient chatClient;

    @Value("classpath:prompts/interview-generate.st")
    private Resource generatePrompt;

    @Value("classpath:prompts/interview-evaluate.st")
    private Resource evaluatePrompt;

    @Value("classpath:prompts/interview-summary.st")
    private Resource summaryPrompt;

    @CircuitBreaker(name = "aiService", fallbackMethod = "generateQuestionsFallback")
    @Retry(name = "aiService")
    public InterviewQuestions generateQuestions(Job job, InterviewType type) {
        log.info("AI: Generating {} questions for job '{}'", type, job.getTitle());
        try {
            BeanOutputConverter<InterviewQuestions> converter = new BeanOutputConverter<>(InterviewQuestions.class);

            PromptTemplate template = PromptTemplate.builder()
                    .resource(generatePrompt)
                    .build();

            String prompt = template.render(Map.of(
                    "jobTitle", job.getTitle(),
                    "jobDescription", job.getDescription() != null ? job.getDescription() : "Not provided",
                    "requiredSkills", job.getMustHaveSkills() != null ? job.getMustHaveSkills() : "Not specified",
                    "experienceLevel", job.getExperienceLevel() != null ? job.getExperienceLevel().name() : "Not specified",
                    "interviewType", type.name(),
                    "format", converter.getFormat()
            ));

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            InterviewQuestions questions = converter.convert(response);
            log.info("AI: Generated {} questions", questions.questions().size());
            return questions;
        } catch (Exception e) {
            log.error("AI: Failed to generate interview questions", e);
            throw new AiProcessingException("Failed to generate interview questions", e);
        }
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "evaluateAnswerFallback")
    @Retry(name = "aiService")
    public AnswerEvaluation evaluateAnswer(InterviewQuestion question, String answer, Job job) {
        log.info("AI: Evaluating answer for question {}", question.getId());
        try {
            BeanOutputConverter<AnswerEvaluation> converter = new BeanOutputConverter<>(AnswerEvaluation.class);

            PromptTemplate template = PromptTemplate.builder()
                    .resource(evaluatePrompt)
                    .build();

            String prompt = template.render(Map.of(
                    "jobTitle", job.getTitle(),
                    "requiredSkills", job.getMustHaveSkills() != null ? job.getMustHaveSkills() : "Not specified",
                    "question", question.getQuestionText(),
                    "category", question.getCategory() != null ? question.getCategory().name() : "GENERAL",
                    "difficulty", question.getDifficulty() != null ? question.getDifficulty() : "MEDIUM",
                    "answer", answer,
                    "format", converter.getFormat()
            ));

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            AnswerEvaluation eval = converter.convert(response);
            log.info("AI: Answer scored {}/10", eval.score());
            return eval;
        } catch (Exception e) {
            log.error("AI: Failed to evaluate answer", e);
            throw new AiProcessingException("Failed to evaluate answer", e);
        }
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "generateSummaryFallback")
    @Retry(name = "aiService")
    public InterviewSummary generateSummary(Interview interview, Job job) {
        log.info("AI: Generating interview summary for interview {}", interview.getId());
        try {
            BeanOutputConverter<InterviewSummary> converter = new BeanOutputConverter<>(InterviewSummary.class);

            StringBuilder results = new StringBuilder();
            for (InterviewQuestion q : interview.getQuestions()) {
                results.append("Q: ").append(q.getQuestionText())
                        .append(" [").append(q.getCategory()).append(", ").append(q.getDifficulty()).append("]\n");
                if (q.getResponses() != null && !q.getResponses().isEmpty()) {
                    InterviewResponse r = q.getResponses().get(0);
                    results.append("A: ").append(r.getAnswerText()).append("\n");
                    results.append("Score: ").append(r.getAiScore()).append("/10\n");
                    results.append("Feedback: ").append(r.getAiFeedback()).append("\n\n");
                }
            }

            PromptTemplate template = PromptTemplate.builder()
                    .resource(summaryPrompt)
                    .build();

            String prompt = template.render(Map.of(
                    "jobTitle", job.getTitle(),
                    "requiredSkills", job.getMustHaveSkills() != null ? job.getMustHaveSkills() : "Not specified",
                    "interviewResults", results.toString(),
                    "format", converter.getFormat()
            ));

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            InterviewSummary summary = converter.convert(response);
            log.info("AI: Interview summary generated - score={}, recommendation={}", summary.overallScore(), summary.recommendation());
            return summary;
        } catch (Exception e) {
            log.error("AI: Failed to generate interview summary", e);
            throw new AiProcessingException("Failed to generate interview summary", e);
        }
    }

    // --- Fallback methods ---

    private InterviewQuestions generateQuestionsFallback(Job job, InterviewType type, Throwable t) {
        log.warn("AI circuit breaker: generateQuestions fallback triggered: {}", t.getMessage());
        throw new AiProcessingException("AI service unavailable for question generation — please retry later", t);
    }

    private AnswerEvaluation evaluateAnswerFallback(InterviewQuestion question, String answer, Job job, Throwable t) {
        log.warn("AI circuit breaker: evaluateAnswer fallback triggered: {}", t.getMessage());
        return new AnswerEvaluation(0, "AI temporarily unavailable — evaluation pending", List.of(), List.of());
    }

    private InterviewSummary generateSummaryFallback(Interview interview, Job job, Throwable t) {
        log.warn("AI circuit breaker: generateSummary fallback triggered: {}", t.getMessage());
        throw new AiProcessingException("AI service unavailable for interview summary — please retry later", t);
    }
}
