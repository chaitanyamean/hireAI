package com.hireai.controller;

import com.hireai.domain.dto.request.ApplicationRequest;
import com.hireai.domain.dto.request.StatusUpdateRequest;
import com.hireai.domain.dto.response.ApiResponse;
import com.hireai.domain.dto.response.ApplicationResponse;
import com.hireai.domain.entity.User;
import com.hireai.security.SecurityUtils;
import com.hireai.service.ApplicationService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Tag(name = "Applications", description = "Job application APIs")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final SecurityUtils securityUtils;

    @PostMapping("/apply")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Apply to a job")
    public ResponseEntity<ApiResponse<ApplicationResponse>> apply(
            @Valid @RequestBody ApplicationRequest request) {
        User user = securityUtils.getCurrentUser();
        ApplicationResponse response = applicationService.apply(request.getJobId(), request.getResumeId(), user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Application submitted", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('RECRUITER')")
    @Operation(summary = "List all applications (filterable by status)")
    public ResponseEntity<ApiResponse<Page<ApplicationResponse>>> listApplications(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.listApplications(status, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get application details")
    public ResponseEntity<ApiResponse<ApplicationResponse>> getApplication(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getApplication(id)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    @Operation(summary = "Update application status")
    public ResponseEntity<ApiResponse<ApplicationResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {
        User user = securityUtils.getCurrentUser();
        ApplicationResponse response = applicationService.updateStatus(id, request.getStatus(), user);
        return ResponseEntity.ok(ApiResponse.ok("Status updated", response));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Get my applications")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> getMyApplications() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getMyApplications(user)));
    }

    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasRole('RECRUITER')")
    @Operation(summary = "Get applications for a job")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> getJobApplications(@PathVariable Long jobId) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getJobApplications(jobId)));
    }
}
