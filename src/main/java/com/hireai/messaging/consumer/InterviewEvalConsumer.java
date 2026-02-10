package com.hireai.messaging.consumer;

import com.hireai.ai.dto.AnswerEvaluation;
import com.hireai.config.RabbitMQConfig;
import com.hireai.domain.entity.Interview;
import com.hireai.domain.entity.InterviewQuestion;
import com.hireai.domain.entity.InterviewResponse;
import com.hireai.domain.entity.Job;
import com.hireai.messaging.event.InterviewEvalEvent;
import com.hireai.repository.InterviewQuestionRepository;
import com.hireai.repository.InterviewRepository;
import com.hireai.repository.InterviewResponseRepository;
import com.hireai.service.InterviewAIService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterviewEvalConsumer {

    private final InterviewRepository interviewRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewResponseRepository interviewResponseRepository;
    private final InterviewAIService interviewAIService;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.INTERVIEW_EVALUATE_QUEUE, concurrency = "1-3")
    public void handleInterviewEval(InterviewEvalEvent event, Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        log.info("Consuming interview eval event: interviewId={}, questionId={}", event.getInterviewId(), event.getQuestionId());
        try {
            Interview interview = interviewRepository.findById(event.getInterviewId())
                    .orElseThrow(() -> new RuntimeException("Interview not found: " + event.getInterviewId()));

            InterviewQuestion question = interviewQuestionRepository.findById(event.getQuestionId())
                    .orElseThrow(() -> new RuntimeException("Question not found: " + event.getQuestionId()));

            Job job = interview.getApplication().getJob();

            // Find the response that was saved (without AI eval yet)
            List<InterviewResponse> responses = interviewResponseRepository.findByQuestionId(event.getQuestionId());
            InterviewResponse response = responses.stream()
                    .filter(r -> r.getAiScore() == null)
                    .findFirst()
                    .orElse(null);

            if (response == null) {
                log.warn("No unevaluated response found for questionId={}, skipping", event.getQuestionId());
                channel.basicAck(tag, false);
                return;
            }

            // AI evaluation
            AnswerEvaluation eval = interviewAIService.evaluateAnswer(question, response.getAnswerText(), job);
            response.setAiScore(BigDecimal.valueOf(eval.score()));
            response.setAiFeedback(eval.feedback());
            interviewResponseRepository.save(response);

            log.info("Answer evaluated async: interviewId={}, questionId={}, score={}",
                    event.getInterviewId(), event.getQuestionId(), eval.score());

            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("Failed to evaluate interview answer: interviewId={}, questionId={}",
                    event.getInterviewId(), event.getQuestionId(), e);
            channel.basicNack(tag, false, false);
        }
    }
}
