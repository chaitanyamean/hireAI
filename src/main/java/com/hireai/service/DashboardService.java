package com.hireai.service;

import com.hireai.domain.dto.response.ApplicationResponse;
import com.hireai.domain.dto.response.CandidateDashboardResponse;
import com.hireai.domain.dto.response.DashboardStatsResponse;
import com.hireai.domain.dto.response.RecruiterDashboardResponse;
import com.hireai.domain.entity.Application;
import com.hireai.domain.entity.Candidate;
import com.hireai.domain.entity.User;
import com.hireai.domain.enums.ApplicationStatus;
import com.hireai.exception.ResourceNotFoundException;
import com.hireai.repository.ApplicationRepository;
import com.hireai.repository.CandidateRepository;
import com.hireai.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final ApplicationRepository applicationRepository;

    @Cacheable(value = "dashboardStats")
    public DashboardStatsResponse getStats() {
        log.info("Computing dashboard stats (cache miss)");

        long totalJobs = jobRepository.count();
        long totalCandidates = candidateRepository.count();
        long totalApplications = applicationRepository.count();

        Map<String, Long> pipeline = new LinkedHashMap<>();
        pipeline.put("APPLIED", applicationRepository.countByStatus(ApplicationStatus.APPLIED));
        pipeline.put("SCREENING", applicationRepository.countByStatus(ApplicationStatus.SCREENING));
        pipeline.put("SHORTLISTED", applicationRepository.countByStatus(ApplicationStatus.SHORTLISTED));
        pipeline.put("INTERVIEW", applicationRepository.countByStatus(ApplicationStatus.INTERVIEW));
        pipeline.put("OFFERED", applicationRepository.countByStatus(ApplicationStatus.OFFERED));
        pipeline.put("REJECTED", applicationRepository.countByStatus(ApplicationStatus.REJECTED));

        return DashboardStatsResponse.builder()
                .totalJobs(totalJobs)
                .totalCandidates(totalCandidates)
                .totalApplications(totalApplications)
                .pipelineCounts(pipeline)
                .build();
    }

    @Transactional(readOnly = true)
    public RecruiterDashboardResponse getRecruiterDashboard() {
        long totalJobs = jobRepository.count();
        long totalCandidates = candidateRepository.count();
        long totalApplications = applicationRepository.count();

        Map<String, Long> pipeline = new LinkedHashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            pipeline.put(status.name(), applicationRepository.countByStatus(status));
        }

        List<ApplicationResponse> recentApplications = applicationRepository
                .findAllOrderByCreatedAtDesc(PageRequest.of(0, 10))
                .map(this::toResponse)
                .getContent();

        return RecruiterDashboardResponse.builder()
                .totalJobs(totalJobs)
                .totalApplications(totalApplications)
                .totalCandidates(totalCandidates)
                .pipelineCounts(pipeline)
                .recentApplications(recentApplications)
                .build();
    }

    @Transactional(readOnly = true)
    public CandidateDashboardResponse getCandidateDashboard(User user) {
        Candidate candidate = candidateRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        List<Application> applications = applicationRepository
                .findByCandidateIdOrderByCreatedAtDesc(candidate.getId());

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            statusCounts.put(status.name(),
                    applicationRepository.countByCandidateIdAndStatus(candidate.getId(), status));
        }

        List<ApplicationResponse> appResponses = applications.stream()
                .map(this::toResponse)
                .toList();

        return CandidateDashboardResponse.builder()
                .totalApplications(applications.size())
                .statusCounts(statusCounts)
                .applications(appResponses)
                .build();
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
