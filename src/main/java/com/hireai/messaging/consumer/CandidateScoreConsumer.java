package com.hireai.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireai.ai.dto.CandidateScore;
import com.hireai.ai.dto.ParsedResume;
import com.hireai.config.RabbitMQConfig;
import com.hireai.domain.entity.Job;
import com.hireai.domain.entity.Resume;
import com.hireai.domain.enums.JobStatus;
import com.hireai.messaging.event.CandidateScoreEvent;
import com.hireai.repository.JobRepository;
import com.hireai.repository.ResumeRepository;
import com.hireai.service.ResumeAIService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CandidateScoreConsumer {

    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final ResumeAIService resumeAIService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    @RabbitListener(queues = RabbitMQConfig.CANDIDATE_SCORE_QUEUE, concurrency = "1-3")
    public void handleCandidateScore(CandidateScoreEvent event, Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        log.info("Consuming candidate score event: candidateId={}, resumeId={}, jobId={}",
                event.getCandidateId(), event.getResumeId(), event.getJobId());
        try {
            Resume resume = resumeRepository.findById(event.getResumeId())
                    .orElseThrow(() -> new RuntimeException("Resume not found: " + event.getResumeId()));

            if (resume.getParsedData() == null) {
                log.warn("Resume {} not yet parsed, skipping scoring", event.getResumeId());
                channel.basicAck(tag, false);
                return;
            }

            ParsedResume parsed = objectMapper.readValue(resume.getParsedData(), ParsedResume.class);

            if (event.getJobId() != null) {
                // Score against specific job
                Job job = jobRepository.findById(event.getJobId())
                        .orElseThrow(() -> new RuntimeException("Job not found: " + event.getJobId()));
                CandidateScore score = resumeAIService.scoreCandidate(parsed, job);
                resume.setAiScore(BigDecimal.valueOf(score.score()));
                resumeRepository.save(resume);

                // Cache the score
                String cacheKey = "score:candidate:" + event.getCandidateId() + ":job:" + event.getJobId();
                redisTemplate.opsForValue().set(cacheKey, String.valueOf(score.score()), Duration.ofHours(24));
                log.info("Candidate {} scored {} for job {}", event.getCandidateId(), score.score(), event.getJobId());
            } else {
                // Score against top active jobs
                List<Job> activeJobs = jobRepository.findByStatus(JobStatus.ACTIVE);
                List<Job> topJobs = activeJobs.stream().limit(5).toList();

                int bestScore = 0;
                for (Job job : topJobs) {
                    CandidateScore score = resumeAIService.scoreCandidate(parsed, job);
                    String cacheKey = "score:candidate:" + event.getCandidateId() + ":job:" + job.getId();
                    redisTemplate.opsForValue().set(cacheKey, String.valueOf(score.score()), Duration.ofHours(24));
                    if (score.score() > bestScore) bestScore = score.score();
                    log.info("Candidate {} scored {} for job {} ('{}')",
                            event.getCandidateId(), score.score(), job.getId(), job.getTitle());
                }

                resume.setAiScore(BigDecimal.valueOf(bestScore));
                resumeRepository.save(resume);
            }

            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("Failed to process candidate score event: candidateId={}", event.getCandidateId(), e);
            channel.basicNack(tag, false, false);
        }
    }
}
