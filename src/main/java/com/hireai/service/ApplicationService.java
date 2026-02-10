package com.hireai.service;

import com.hireai.domain.dto.response.ApplicationResponse;
import com.hireai.domain.entity.Application;
import com.hireai.domain.entity.Candidate;
import com.hireai.domain.entity.Job;
import com.hireai.domain.entity.Resume;
import com.hireai.domain.entity.User;
import com.hireai.domain.enums.ApplicationStatus;
import com.hireai.exception.DuplicateResourceException;
import com.hireai.exception.ResourceNotFoundException;
import com.hireai.exception.UnauthorizedException;
import com.hireai.messaging.event.ApplicationScreenEvent;
import com.hireai.messaging.producer.HiringEventProducer;
import com.hireai.repository.ApplicationRepository;
import com.hireai.repository.CandidateRepository;
import com.hireai.repository.JobRepository;
import com.hireai.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final ResumeRepository resumeRepository;
    private final HiringEventProducer eventProducer;

    @Transactional
    @CacheEvict(value = "dashboardStats", allEntries = true)
    public ApplicationResponse apply(Long jobId, Long resumeId, User user) {
        Candidate candidate = candidateRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", jobId));

        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume", resumeId));

        if (applicationRepository.existsByJobIdAndCandidateId(jobId, candidate.getId())) {
            throw new DuplicateResourceException("You have already applied to this job");
        }

        Application application = Application.builder()
                .job(job)
                .candidate(candidate)
                .resume(resume)
                .status(ApplicationStatus.APPLIED)
                .build();

        Application saved = applicationRepository.save(application);
        log.info("Application created: id={}, jobId={}, candidateId={}", saved.getId(), jobId, candidate.getId());

        // Publish async screening event
        eventProducer.publishApplicationScreen(ApplicationScreenEvent.builder()
                .applicationId(saved.getId())
                .jobId(jobId)
                .candidateId(candidate.getId())
                .resumeId(resumeId)
                .build());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplication(Long id) {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application", id));
        return toResponse(application);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> listApplications(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            ApplicationStatus appStatus = ApplicationStatus.valueOf(status.toUpperCase());
            return applicationRepository.findByStatusOrderByCreatedAtDesc(appStatus, pageable)
                    .map(this::toResponse);
        }
        return applicationRepository.findAllOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getMyApplications(User user) {
        Candidate candidate = candidateRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));
        return applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidate.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getJobApplications(Long jobId) {
        return applicationRepository.findByJobId(jobId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(value = "dashboardStats", allEntries = true)
    public ApplicationResponse updateStatus(Long id, String newStatus, User recruiter) {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application", id));

        // Verify the recruiter owns the job
        Job job = application.getJob();
        if (!job.getRecruiter().getId().equals(recruiter.getId())) {
            throw new UnauthorizedException("You don't own the job for this application");
        }

        ApplicationStatus status = ApplicationStatus.valueOf(newStatus.toUpperCase());
        application.setStatus(status);
        Application saved = applicationRepository.save(application);
        log.info("Application status updated: id={}, newStatus={}", id, status);
        return toResponse(saved);
    }

    private ApplicationResponse toResponse(Application app) {
        return ApplicationResponse.builder()
                .id(app.getId())
                .jobId(app.getJob().getId())
                .candidateId(app.getCandidate().getId())
                .resumeId(app.getResume() != null ? app.getResume().getId() : null)
                .status(app.getStatus().name())
                .aiMatchScore(app.getAiMatchScore())
                .aiScreeningNotes(app.getAiScreeningNotes())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
