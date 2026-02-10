package com.hireai.service;

import com.hireai.ai.dto.InterviewQuestions;
import com.hireai.ai.dto.InterviewSummary;
import com.hireai.domain.dto.response.InterviewDetailResponse;
import com.hireai.domain.dto.response.InterviewEvalResponse;
import com.hireai.domain.dto.response.InterviewQuestionResponse;
import com.hireai.domain.entity.Application;
import com.hireai.domain.entity.Interview;
import com.hireai.domain.entity.InterviewQuestion;
import com.hireai.domain.entity.InterviewResponse;
import com.hireai.domain.entity.Job;
import com.hireai.domain.enums.InterviewStatus;
import com.hireai.domain.enums.InterviewType;
import com.hireai.domain.enums.QuestionCategory;
import com.hireai.exception.ResourceNotFoundException;
import com.hireai.messaging.event.InterviewEvalEvent;
import com.hireai.messaging.producer.HiringEventProducer;
import com.hireai.repository.ApplicationRepository;
import com.hireai.repository.InterviewQuestionRepository;
import com.hireai.repository.InterviewRepository;
import com.hireai.repository.InterviewResponseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewResponseRepository interviewResponseRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewAIService interviewAIService;
    private final HiringEventProducer eventProducer;

    @Transactional
    public InterviewDetailResponse startInterview(Long applicationId, String interviewType) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));

        Job job = application.getJob();
        InterviewType type = InterviewType.valueOf(interviewType.toUpperCase());

        // Create interview
        Interview interview = Interview.builder()
                .application(application)
                .interviewType(type)
                .status(InterviewStatus.IN_PROGRESS)
                .questions(new ArrayList<>())
                .build();
        interview = interviewRepository.save(interview);

        // Generate AI questions
        InterviewQuestions aiQuestions = interviewAIService.generateQuestions(job, type);

        List<InterviewQuestion> savedQuestions = new ArrayList<>();
        for (int i = 0; i < aiQuestions.questions().size(); i++) {
            InterviewQuestions.Question q = aiQuestions.questions().get(i);
            InterviewQuestion question = InterviewQuestion.builder()
                    .interview(interview)
                    .questionText(q.questionText())
                    .category(parseCategory(q.category()))
                    .difficulty(q.difficulty())
                    .orderIndex(i + 1)
                    .build();
            savedQuestions.add(interviewQuestionRepository.save(question));
        }

        interview.setQuestions(savedQuestions);
        log.info("Interview started: id={}, applicationId={}, questions={}", interview.getId(), applicationId, savedQuestions.size());

        return toDetailResponse(interview);
    }

    @Transactional
    public InterviewEvalResponse.QuestionScore submitAnswer(Long interviewId, Long questionId, String answerText) {
        findInterviewOrThrow(interviewId);
        InterviewQuestion question = interviewQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("InterviewQuestion", questionId));

        // Save the answer immediately
        InterviewResponse response = InterviewResponse.builder()
                .question(question)
                .answerText(answerText)
                .answeredAt(LocalDateTime.now())
                .build();
        interviewResponseRepository.save(response);
        log.info("Answer submitted: interviewId={}, questionId={}, evaluating async...", interviewId, questionId);

        // Publish async evaluation event
        eventProducer.publishInterviewEval(InterviewEvalEvent.builder()
                .interviewId(interviewId)
                .questionId(questionId)
                .answerText(answerText)
                .build());

        return InterviewEvalResponse.QuestionScore.builder()
                .questionId(questionId)
                .score(null)
                .feedback("Answer submitted. AI evaluation in progress...")
                .build();
    }

    @Transactional
    public InterviewEvalResponse completeInterview(Long interviewId) {
        Interview interview = findInterviewOrThrow(interviewId);
        Job job = interview.getApplication().getJob();

        // Reload questions with responses
        List<InterviewQuestion> questions = interviewQuestionRepository.findByInterviewIdOrderByOrderIndex(interviewId);
        interview.setQuestions(questions);

        // Load responses for each question
        for (InterviewQuestion q : questions) {
            List<InterviewResponse> responses = interviewResponseRepository.findByQuestionId(q.getId());
            q.setResponses(responses);
        }

        // Generate AI summary
        InterviewSummary summary = interviewAIService.generateSummary(interview, job);

        interview.setStatus(InterviewStatus.COMPLETED);
        interview.setOverallScore(BigDecimal.valueOf(summary.overallScore()));
        interview.setAiRecommendation(summary.recommendation() + ": " + summary.summary());
        interview.setCompletedAt(LocalDateTime.now());
        interviewRepository.save(interview);

        // Build response
        List<InterviewEvalResponse.QuestionScore> questionScores = questions.stream()
                .filter(q -> q.getResponses() != null && !q.getResponses().isEmpty())
                .map(q -> {
                    InterviewResponse r = q.getResponses().get(0);
                    return InterviewEvalResponse.QuestionScore.builder()
                            .questionId(q.getId())
                            .score(r.getAiScore())
                            .feedback(r.getAiFeedback())
                            .build();
                })
                .toList();

        log.info("Interview completed: id={}, score={}, recommendation={}", interviewId, summary.overallScore(), summary.recommendation());

        return InterviewEvalResponse.builder()
                .interviewId(interviewId)
                .overallScore(BigDecimal.valueOf(summary.overallScore()))
                .recommendation(summary.recommendation())
                .questionScores(questionScores)
                .build();
    }

    @Transactional(readOnly = true)
    public InterviewDetailResponse getInterview(Long interviewId) {
        Interview interview = findInterviewOrThrow(interviewId);
        List<InterviewQuestion> questions = interviewQuestionRepository.findByInterviewIdOrderByOrderIndex(interviewId);
        interview.setQuestions(questions);
        return toDetailResponse(interview);
    }

    @Transactional(readOnly = true)
    public List<InterviewQuestionResponse> getQuestions(Long interviewId) {
        return interviewQuestionRepository.findByInterviewIdOrderByOrderIndex(interviewId).stream()
                .map(this::toQuestionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InterviewEvalResponse getResult(Long interviewId) {
        Interview interview = findInterviewOrThrow(interviewId);
        List<InterviewQuestion> questions = interviewQuestionRepository.findByInterviewIdOrderByOrderIndex(interviewId);

        List<InterviewEvalResponse.QuestionScore> questionScores = new ArrayList<>();
        for (InterviewQuestion q : questions) {
            List<InterviewResponse> responses = interviewResponseRepository.findByQuestionId(q.getId());
            if (!responses.isEmpty()) {
                InterviewResponse r = responses.get(0);
                questionScores.add(InterviewEvalResponse.QuestionScore.builder()
                        .questionId(q.getId())
                        .score(r.getAiScore())
                        .feedback(r.getAiFeedback())
                        .build());
            }
        }

        return InterviewEvalResponse.builder()
                .interviewId(interviewId)
                .overallScore(interview.getOverallScore())
                .recommendation(interview.getAiRecommendation())
                .questionScores(questionScores)
                .build();
    }

    private Interview findInterviewOrThrow(Long id) {
        return interviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview", id));
    }

    private QuestionCategory parseCategory(String category) {
        try {
            return QuestionCategory.valueOf(category.toUpperCase());
        } catch (Exception e) {
            return QuestionCategory.TECHNICAL;
        }
    }

    private InterviewDetailResponse toDetailResponse(Interview interview) {
        List<InterviewQuestionResponse> questions = interview.getQuestions() != null
                ? interview.getQuestions().stream().map(this::toQuestionResponse).toList()
                : List.of();

        return InterviewDetailResponse.builder()
                .id(interview.getId())
                .applicationId(interview.getApplication().getId())
                .interviewType(interview.getInterviewType().name())
                .status(interview.getStatus().name())
                .overallScore(interview.getOverallScore())
                .aiRecommendation(interview.getAiRecommendation())
                .questions(questions)
                .createdAt(interview.getCreatedAt())
                .completedAt(interview.getCompletedAt())
                .build();
    }

    private InterviewQuestionResponse toQuestionResponse(InterviewQuestion q) {
        return InterviewQuestionResponse.builder()
                .id(q.getId())
                .questionText(q.getQuestionText())
                .category(q.getCategory() != null ? q.getCategory().name() : null)
                .difficulty(q.getDifficulty())
                .orderIndex(q.getOrderIndex())
                .build();
    }
}
