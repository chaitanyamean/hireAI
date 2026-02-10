package com.hireai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireai.ai.dto.CandidateScore;
import com.hireai.ai.dto.ParsedResume;
import com.hireai.ai.dto.ScreeningResult;
import com.hireai.config.RabbitMQConfig;
import com.hireai.domain.entity.Application;
import com.hireai.domain.entity.Job;
import com.hireai.domain.entity.Resume;
import com.hireai.domain.enums.ApplicationStatus;
import com.hireai.messaging.event.ApplicationScreenEvent;
import com.hireai.messaging.event.NotificationEvent;
import com.hireai.messaging.producer.HiringEventProducer;
import com.hireai.repository.ApplicationRepository;
import com.hireai.repository.JobRepository;
import com.hireai.repository.ResumeRepository;
import com.hireai.repository.UserRepository;
import com.hireai.service.ResumeAIService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationScreenConsumer {

    private final ApplicationRepository applicationRepository;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final ResumeAIService resumeAIService;
    private final ObjectMapper objectMapper;
    private final HiringEventProducer eventProducer;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.APPLICATION_SCREEN_QUEUE, concurrency = "1-3")
    public void handleApplicationScreen(ApplicationScreenEvent event, Channel channel,
                                         @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        log.info("Consuming application screen event: applicationId={}", event.getApplicationId());
        try {
            Application application = applicationRepository.findById(event.getApplicationId())
                    .orElseThrow(() -> new RuntimeException("Application not found: " + event.getApplicationId()));

            Resume resume = resumeRepository.findById(event.getResumeId())
                    .orElseThrow(() -> new RuntimeException("Resume not found: " + event.getResumeId()));

            Job job = jobRepository.findById(event.getJobId())
                    .orElseThrow(() -> new RuntimeException("Job not found: " + event.getJobId()));

            if (resume.getParsedData() == null) {
                log.warn("Resume {} not parsed yet, screening with raw text", resume.getId());
                channel.basicAck(tag, false);
                return;
            }

            ParsedResume parsed = objectMapper.readValue(resume.getParsedData(), ParsedResume.class);

            // AI screening
            ScreeningResult screening = resumeAIService.screenCandidate(parsed, job);

            // AI scoring
            CandidateScore score = resumeAIService.scoreCandidate(parsed, job);
            int matchScore = score.score();

            // Update application
            application.setAiMatchScore(BigDecimal.valueOf(matchScore));

            StringBuilder notes = new StringBuilder();
            notes.append("Score: ").append(matchScore).append("/100\n");
            notes.append("Qualified: ").append(screening.qualified()).append("\n");
            if (screening.redFlags() != null && !screening.redFlags().isEmpty()) {
                notes.append("Red Flags: ").append(String.join(", ", screening.redFlags())).append("\n");
            }
            if (screening.missingRequirements() != null && !screening.missingRequirements().isEmpty()) {
                notes.append("Missing: ").append(String.join(", ", screening.missingRequirements())).append("\n");
            }
            notes.append("Reasoning: ").append(score.reasoning());
            application.setAiScreeningNotes(notes.toString());

            // Auto-update status
            if (matchScore >= 70) {
                application.setStatus(ApplicationStatus.SHORTLISTED);
            } else if (matchScore >= 40) {
                application.setStatus(ApplicationStatus.SCREENING);
            } else {
                application.setStatus(ApplicationStatus.REJECTED);
            }

            applicationRepository.save(application);
            log.info("Application {} screened: score={}, status={}", event.getApplicationId(), matchScore, application.getStatus());

            // Notify recruiter
            eventProducer.publishNotification(NotificationEvent.builder()
                    .recipientEmail(job.getRecruiter().getEmail())
                    .type("APPLICATION_SCREENED")
                    .subject("New application screened for " + job.getTitle())
                    .body("Candidate scored " + matchScore + "/100. Status: " + application.getStatus())
                    .build());

            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("Failed to screen application: applicationId={}", event.getApplicationId(), e);
            channel.basicNack(tag, false, false);
        }
    }
}
