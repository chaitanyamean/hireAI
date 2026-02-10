package com.hireai.service;

import com.hireai.domain.dto.request.JobCreateRequest;
import com.hireai.domain.dto.request.JobUpdateRequest;
import com.hireai.domain.dto.response.JobResponse;
import com.hireai.domain.entity.Job;
import com.hireai.domain.entity.User;
import com.hireai.domain.enums.EmploymentType;
import com.hireai.domain.enums.ExperienceLevel;
import com.hireai.domain.enums.JobStatus;
import com.hireai.exception.ResourceNotFoundException;
import com.hireai.exception.UnauthorizedException;
import com.hireai.repository.JobRepository;
import com.hireai.repository.VectorSearchRepository;
import com.hireai.service.ResumeAIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepository;
    private final VectorSearchRepository vectorSearchRepository;
    private final ResumeAIService resumeAIService;

    @Transactional
    public JobResponse createJob(JobCreateRequest request, User recruiter) {
        Job job = Job.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .companyName(request.getCompanyName())
                .location(request.getLocation())
                .employmentType(request.getEmploymentType() != null
                        ? EmploymentType.valueOf(request.getEmploymentType()) : null)
                .experienceLevel(request.getExperienceLevel() != null
                        ? ExperienceLevel.valueOf(request.getExperienceLevel()) : null)
                .mustHaveSkills(request.getMustHaveSkills())
                .niceToHaveSkills(request.getNiceToHaveSkills())
                .salaryMin(request.getSalaryMin())
                .salaryMax(request.getSalaryMax())
                .status(JobStatus.DRAFT)
                .recruiter(recruiter)
                .build();

        Job saved = jobRepository.save(job);
        generateJobEmbeddingAsync(saved);
        return toResponse(saved);
    }

    public Page<JobResponse> getJobs(JobStatus status, String keyword, Pageable pageable) {
        return jobRepository.searchJobs(status, keyword, pageable)
                .map(this::toResponse);
    }

    @Cacheable(value = "jobById", key = "#id")
    public JobResponse getJobById(Long id) {
        return toResponse(findJobOrThrow(id));
    }

    @Transactional
    @CacheEvict(value = "jobById", key = "#id")
    public JobResponse updateJob(Long id, JobUpdateRequest request, User recruiter) {
        Job job = findJobOrThrow(id);
        verifyOwnership(job, recruiter);

        if (request.getTitle() != null) job.setTitle(request.getTitle());
        if (request.getDescription() != null) job.setDescription(request.getDescription());
        if (request.getCompanyName() != null) job.setCompanyName(request.getCompanyName());
        if (request.getLocation() != null) job.setLocation(request.getLocation());
        if (request.getEmploymentType() != null) job.setEmploymentType(EmploymentType.valueOf(request.getEmploymentType()));
        if (request.getExperienceLevel() != null) job.setExperienceLevel(ExperienceLevel.valueOf(request.getExperienceLevel()));
        if (request.getMustHaveSkills() != null) job.setMustHaveSkills(request.getMustHaveSkills());
        if (request.getNiceToHaveSkills() != null) job.setNiceToHaveSkills(request.getNiceToHaveSkills());
        if (request.getSalaryMin() != null) job.setSalaryMin(request.getSalaryMin());
        if (request.getSalaryMax() != null) job.setSalaryMax(request.getSalaryMax());
        if (request.getStatus() != null) job.setStatus(JobStatus.valueOf(request.getStatus()));

        Job saved = jobRepository.save(job);
        generateJobEmbeddingAsync(saved);
        return toResponse(saved);
    }

    @Transactional
    public void closeJob(Long id, User recruiter) {
        Job job = findJobOrThrow(id);
        verifyOwnership(job, recruiter);
        job.setStatus(JobStatus.CLOSED);
        jobRepository.save(job);
    }

    private void generateJobEmbeddingAsync(Job job) {
        try {
            String text = job.getTitle() + " " +
                    (job.getDescription() != null ? job.getDescription() : "") + " " +
                    (job.getMustHaveSkills() != null ? job.getMustHaveSkills() : "") + " " +
                    (job.getNiceToHaveSkills() != null ? job.getNiceToHaveSkills() : "");
            float[] embedding = resumeAIService.generateEmbedding(text);
            vectorSearchRepository.saveJobEmbedding(job.getId(), embedding);
            log.info("Job embedding generated: jobId={}", job.getId());
        } catch (Exception e) {
            log.warn("Failed to generate embedding for job {}: {}", job.getId(), e.getMessage());
        }
    }

    private Job findJobOrThrow(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job", id));
    }

    private void verifyOwnership(Job job, User recruiter) {
        if (!job.getRecruiter().getId().equals(recruiter.getId())) {
            throw new UnauthorizedException("You don't own this job posting");
        }
    }

    private JobResponse toResponse(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .description(job.getDescription())
                .companyName(job.getCompanyName())
                .location(job.getLocation())
                .employmentType(job.getEmploymentType() != null ? job.getEmploymentType().name() : null)
                .experienceLevel(job.getExperienceLevel() != null ? job.getExperienceLevel().name() : null)
                .mustHaveSkills(job.getMustHaveSkills())
                .niceToHaveSkills(job.getNiceToHaveSkills())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .status(job.getStatus().name())
                .recruiterId(job.getRecruiter().getId())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
