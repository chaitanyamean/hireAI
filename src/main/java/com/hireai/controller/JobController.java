package com.hireai.controller;

import com.hireai.domain.dto.request.JobCreateRequest;
import com.hireai.domain.dto.request.JobUpdateRequest;
import com.hireai.domain.dto.response.ApiResponse;
import com.hireai.domain.dto.response.JobResponse;
import com.hireai.domain.enums.JobStatus;
import com.hireai.security.SecurityUtils;
import com.hireai.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "Job management APIs")
public class JobController {

    private final JobService jobService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @Operation(summary = "Create a new job posting")
    public ResponseEntity<ApiResponse<JobResponse>> createJob(@Valid @RequestBody JobCreateRequest request) {
        JobResponse response = jobService.createJob(request, securityUtils.getCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Job created successfully", response));
    }

    @GetMapping
    @Operation(summary = "List jobs with optional filters")
    public ResponseEntity<ApiResponse<Page<JobResponse>>> getJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.getJobs(status, keyword, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get job by ID")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.getJobById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @Operation(summary = "Update a job posting")
    public ResponseEntity<ApiResponse<JobResponse>> updateJob(
            @PathVariable Long id,
            @Valid @RequestBody JobUpdateRequest request) {
        JobResponse response = jobService.updateJob(id, request, securityUtils.getCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("Job updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @Operation(summary = "Close a job posting")
    public ResponseEntity<ApiResponse<Void>> closeJob(@PathVariable Long id) {
        jobService.closeJob(id, securityUtils.getCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("Job closed successfully", null));
    }
}
