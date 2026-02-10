package com.hireai.messaging.producer;

import com.hireai.config.RabbitMQConfig;
import com.hireai.messaging.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class HiringEventProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publishResumeParse(ResumeParseEvent event) {
        log.info("Publishing resume parse event: resumeId={}, candidateId={}", event.getResumeId(), event.getCandidateId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.HIRING_EXCHANGE, RabbitMQConfig.RESUME_PARSE_KEY, event);
    }

    public void publishCandidateScore(CandidateScoreEvent event) {
        log.info("Publishing candidate score event: candidateId={}, resumeId={}, jobId={}", event.getCandidateId(), event.getResumeId(), event.getJobId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.HIRING_EXCHANGE, RabbitMQConfig.CANDIDATE_SCORE_KEY, event);
    }

    public void publishApplicationScreen(ApplicationScreenEvent event) {
        log.info("Publishing application screen event: applicationId={}, jobId={}", event.getApplicationId(), event.getJobId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.HIRING_EXCHANGE, RabbitMQConfig.APPLICATION_SCREEN_KEY, event);
    }

    public void publishInterviewEval(InterviewEvalEvent event) {
        log.info("Publishing interview eval event: interviewId={}, questionId={}", event.getInterviewId(), event.getQuestionId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.HIRING_EXCHANGE, RabbitMQConfig.INTERVIEW_EVALUATE_KEY, event);
    }

    public void publishNotification(NotificationEvent event) {
        log.info("Publishing notification event: type={}, recipient={}", event.getType(), event.getRecipientEmail());
        rabbitTemplate.convertAndSend(RabbitMQConfig.HIRING_EXCHANGE, "notification.send", event);
    }
}
