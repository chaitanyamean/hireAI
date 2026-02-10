package com.hireai.service;

import com.hireai.domain.dto.response.ResumeResponse;
import com.hireai.domain.entity.Candidate;
import com.hireai.domain.entity.Resume;
import com.hireai.domain.entity.User;
import com.hireai.domain.enums.ParseStatus;
import com.hireai.exception.ResourceNotFoundException;
import com.hireai.messaging.event.ResumeParseEvent;
import com.hireai.messaging.producer.HiringEventProducer;
import com.hireai.repository.CandidateRepository;
import com.hireai.repository.ResumeRepository;
import com.hireai.util.FileStorageUtil;
import com.hireai.util.TextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final CandidateRepository candidateRepository;
    private final FileStorageUtil fileStorageUtil;
    private final TextExtractor textExtractor;
    private final HiringEventProducer eventProducer;

    @Transactional
    public ResumeResponse uploadResume(MultipartFile file, User user) {
        Candidate candidate = candidateRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        String filePath = fileStorageUtil.storeFile(file);
        String rawText = textExtractor.extract(filePath);

        Resume resume = Resume.builder()
                .candidate(candidate)
                .fileName(file.getOriginalFilename())
                .filePath(filePath)
                .rawText(rawText)
                .parseStatus(ParseStatus.PENDING)
                .build();

        Resume saved = resumeRepository.save(resume);
        log.info("Resume uploaded: id={}, candidate={}, file={}", saved.getId(), candidate.getId(), file.getOriginalFilename());

        // Publish async parse event
        eventProducer.publishResumeParse(ResumeParseEvent.builder()
                .resumeId(saved.getId())
                .candidateId(candidate.getId())
                .filePath(filePath)
                .build());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ResumeResponse getResumeById(Long id) {
        return toResponse(findResumeOrThrow(id));
    }

    @Transactional(readOnly = true)
    public String getResumeStatus(Long id) {
        return findResumeOrThrow(id).getParseStatus().name();
    }

    @Transactional(readOnly = true)
    public List<ResumeResponse> getMyResumes(User user) {
        Candidate candidate = candidateRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        return resumeRepository.findByCandidateId(candidate.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    private Resume findResumeOrThrow(Long id) {
        return resumeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resume", id));
    }

    private ResumeResponse toResponse(Resume resume) {
        return ResumeResponse.builder()
                .id(resume.getId())
                .fileName(resume.getFileName())
                .parseStatus(resume.getParseStatus().name())
                .parsedData(resume.getParsedData())
                .skills(resume.getSkills())
                .aiScore(resume.getAiScore())
                .createdAt(resume.getCreatedAt())
                .build();
    }
}
