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
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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

/**
 * AI service for interview operations.
 *
 * OPENTELEMETRY LESSON — Span Events:
 * ────────────────────────────────────
 * Beyond tags, spans can also carry EVENTS — timestamped log entries attached
 * to a span. Use span.event("message") to record significant moments within
 * a long-running operation. In Jaeger you'll see these as timeline markers
 * inside the span, which is great for multi-step AI operations.
 *
 * Example: in generateSummary(), we add an event after building the results
 * string so you can see exactly when the prompt was assembled vs when OpenAI
 * responded.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewAIService {

    private final ChatClient chatClient;
    private final Tracer tracer;

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

        Span span = tracer.nextSpan().name("ai.interview.generate_questions").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {

            span.tag("ai.model", "gpt-4o-mini");
            span.tag("ai.operation", "generate_questions");
            span.tag("job.title", job.getTitle());
            span.tag("job.id", String.valueOf(job.getId()));
            // Tag the interview type — lets you compare TECHNICAL vs BEHAVIORAL latency in Jaeger
            span.tag("interview.type", type.name());

            BeanOutputConverter<InterviewQuestions> converter = new BeanOutputConverter<>(InterviewQuestions.class);
            PromptTemplate template = PromptTemplate.builder().resource(generatePrompt).build();

            String prompt = template.render(Map.of(
                    "jobTitle", job.getTitle(),
                    "jobDescription", job.getDescription() != null ? job.getDescription() : "Not provided",
                    "requiredSkills", job.getMustHaveSkills() != null ? job.getMustHaveSkills() : "Not specified",
                    "experienceLevel", job.getExperienceLevel() != null ? job.getExperienceLevel().name() : "Not specified",
                    "interviewType", type.name(),
                    "format", converter.getFormat()
            ));

            // Span event: marks the exact moment we sent the request to OpenAI.
            // In Jaeger's timeline view, you'll see this as a vertical marker on the span bar.
            span.event("openai.request.sent");

            String response = chatClient.prompt().user(prompt).call().content();

            span.event("openai.response.received");

            InterviewQuestions questions = converter.convert(response);
            span.tag("ai.result.questions_count", String.valueOf(questions.questions().size()));

            log.info("AI: Generated {} questions", questions.questions().size());
            return questions;

        } catch (Exception e) {
            span.error(e);
            log.error("AI: Failed to generate interview questions", e);
            throw new AiProcessingException("Failed to generate interview questions", e);
        } finally {
            span.end();
        }
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "evaluateAnswerFallback")
    @Retry(name = "aiService")
    public AnswerEvaluation evaluateAnswer(InterviewQuestion question, String answer, Job job) {
        log.info("AI: Evaluating answer for question {}", question.getId());

        Span span = tracer.nextSpan().name("ai.interview.evaluate_answer").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {

            span.tag("ai.model", "gpt-4o-mini");
            span.tag("ai.operation", "evaluate_answer");
            span.tag("job.title", job.getTitle());
            // Tag the question metadata — useful for analyzing which question categories score poorly
            span.tag("question.id", String.valueOf(question.getId()));
            span.tag("question.category", question.getCategory() != null ? question.getCategory().name() : "GENERAL");
            span.tag("question.difficulty", question.getDifficulty() != null ? question.getDifficulty() : "MEDIUM");
            span.tag("answer.chars", String.valueOf(answer.length()));

            BeanOutputConverter<AnswerEvaluation> converter = new BeanOutputConverter<>(AnswerEvaluation.class);
            PromptTemplate template = PromptTemplate.builder().resource(evaluatePrompt).build();

            String prompt = template.render(Map.of(
                    "jobTitle", job.getTitle(),
                    "requiredSkills", job.getMustHaveSkills() != null ? job.getMustHaveSkills() : "Not specified",
                    "question", question.getQuestionText(),
                    "category", question.getCategory() != null ? question.getCategory().name() : "GENERAL",
                    "difficulty", question.getDifficulty() != null ? question.getDifficulty() : "MEDIUM",
                    "answer", answer,
                    "format", converter.getFormat()
            ));

            span.event("openai.request.sent");
            String response = chatClient.prompt().user(prompt).call().content();
            span.event("openai.response.received");

            AnswerEvaluation eval = converter.convert(response);
            // Tag the score — you can now build a Jaeger query to find all low-scoring answers
            span.tag("ai.result.score", String.valueOf(eval.score()));

            log.info("AI: Answer scored {}/10", eval.score());
            return eval;

        } catch (Exception e) {
            span.error(e);
            log.error("AI: Failed to evaluate answer", e);
            throw new AiProcessingException("Failed to evaluate answer", e);
        } finally {
            span.end();
        }
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "generateSummaryFallback")
    @Retry(name = "aiService")
    public InterviewSummary generateSummary(Interview interview, Job job) {
        log.info("AI: Generating interview summary for interview {}", interview.getId());

        Span span = tracer.nextSpan().name("ai.interview.generate_summary").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {

            span.tag("ai.model", "gpt-4o-mini");
            span.tag("ai.operation", "generate_summary");
            span.tag("interview.id", String.valueOf(interview.getId()));
            span.tag("job.title", job.getTitle());

            // Build the results string and tag how many Q&A pairs were included
            StringBuilder results = new StringBuilder();
            int answeredCount = 0;
            for (InterviewQuestion q : interview.getQuestions()) {
                results.append("Q: ").append(q.getQuestionText())
                        .append(" [").append(q.getCategory()).append(", ").append(q.getDifficulty()).append("]\n");
                if (q.getResponses() != null && !q.getResponses().isEmpty()) {
                    InterviewResponse r = q.getResponses().get(0);
                    results.append("A: ").append(r.getAnswerText()).append("\n");
                    results.append("Score: ").append(r.getAiScore()).append("/10\n");
                    results.append("Feedback: ").append(r.getAiFeedback()).append("\n\n");
                    answeredCount++;
                }
            }

            span.tag("interview.questions_total", String.valueOf(interview.getQuestions().size()));
            span.tag("interview.questions_answered", String.valueOf(answeredCount));

            BeanOutputConverter<InterviewSummary> converter = new BeanOutputConverter<>(InterviewSummary.class);
            PromptTemplate template = PromptTemplate.builder().resource(summaryPrompt).build();

            String prompt = template.render(Map.of(
                    "jobTitle", job.getTitle(),
                    "requiredSkills", job.getMustHaveSkills() != null ? job.getMustHaveSkills() : "Not specified",
                    "interviewResults", results.toString(),
                    "format", converter.getFormat()
            ));

            span.event("openai.request.sent");
            String response = chatClient.prompt().user(prompt).call().content();
            span.event("openai.response.received");

            InterviewSummary summary = converter.convert(response);
            span.tag("ai.result.overall_score", String.valueOf(summary.overallScore()));
            span.tag("ai.result.recommendation", summary.recommendation() != null ? summary.recommendation() : "none");

            log.info("AI: Interview summary generated - score={}, recommendation={}", summary.overallScore(), summary.recommendation());
            return summary;

        } catch (Exception e) {
            span.error(e);
            log.error("AI: Failed to generate interview summary", e);
            throw new AiProcessingException("Failed to generate interview summary", e);
        } finally {
            span.end();
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
