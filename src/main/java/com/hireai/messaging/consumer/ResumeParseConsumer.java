package com.hireai.messaging.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireai.ai.dto.ParsedResume;
import com.hireai.config.RabbitMQConfig;
import com.hireai.domain.entity.Resume;
import com.hireai.domain.enums.ParseStatus;
import com.hireai.messaging.event.CandidateScoreEvent;
import com.hireai.messaging.event.ResumeParseEvent;
import com.hireai.messaging.producer.HiringEventProducer;
import com.hireai.repository.ResumeRepository;
import com.hireai.repository.VectorSearchRepository;
import com.hireai.service.ResumeAIService;
import com.hireai.util.TextExtractor;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.cache.CacheManager;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResumeParseConsumer {

    private final ResumeRepository resumeRepository;
    private final VectorSearchRepository vectorSearchRepository;
    private final ResumeAIService resumeAIService;
    private final TextExtractor textExtractor;
    private final ObjectMapper objectMapper;
    private final HiringEventProducer eventProducer;
    private final CacheManager cacheManager;

    @RabbitListener(queues = RabbitMQConfig.RESUME_PARSE_QUEUE, concurrency = "2-5")
    public void handleResumeParse(ResumeParseEvent event, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        log.info("Consuming resume parse event: resumeId={}", event.getResumeId());
        try {
            Resume resume = resumeRepository.findById(event.getResumeId())
                    .orElseThrow(() -> new RuntimeException("Resume not found: " + event.getResumeId()));

            // Update status to PROCESSING
            resume.setParseStatus(ParseStatus.PROCESSING);
            resumeRepository.save(resume);

            // Extract text if not already present
            if (resume.getRawText() == null || resume.getRawText().isBlank()) {
                String rawText = textExtractor.extract(resume.getFilePath());
                resume.setRawText(rawText);
            }

            // AI parse
            ParsedResume parsed = resumeAIService.parseResume(resume.getRawText());
            resume.setParsedData(objectMapper.writeValueAsString(parsed));
            resume.setSkills(objectMapper.writeValueAsString(parsed.skills()));
            resume.setExperienceSummary(parsed.summary());

            // Generate and store embedding
            float[] embedding = resumeAIService.generateEmbedding(resume.getRawText());
            vectorSearchRepository.saveResumeEmbedding(resume.getId(), embedding);

            resume.setParseStatus(ParseStatus.COMPLETED);
            resumeRepository.save(resume);
            log.info("Resume parsed successfully: resumeId={}, skills={}", resume.getId(),
                    parsed.skills() != null ? parsed.skills().size() : 0);

            // Evict match caches since embeddings changed
            evictMatchCaches();

            // Trigger candidate scoring
            eventProducer.publishCandidateScore(CandidateScoreEvent.builder()
                    .candidateId(event.getCandidateId())
                    .resumeId(event.getResumeId())
                    .build());

            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("Failed to process resume parse event: resumeId={}", event.getResumeId(), e);
            try {
                Resume resume = resumeRepository.findById(event.getResumeId()).orElse(null);
                if (resume != null) {
                    resume.setParseStatus(ParseStatus.FAILED);
                    resumeRepository.save(resume);
                }
            } catch (Exception ex) {
                log.error("Failed to update resume status to FAILED", ex);
            }
            channel.basicNack(tag, false, false); // Send to DLQ
        }
    }

    private void evictMatchCaches() {
        try {
            var topCandidates = cacheManager.getCache("topCandidates");
            if (topCandidates != null) topCandidates.clear();
            var recommendedJobs = cacheManager.getCache("recommendedJobs");
            if (recommendedJobs != null) recommendedJobs.clear();
            log.debug("Evicted match caches after resume embedding update");
        } catch (Exception e) {
            log.warn("Failed to evict match caches: {}", e.getMessage());
        }
    }
}
