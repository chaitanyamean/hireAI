package com.hireai.controller;

import com.hireai.domain.dto.response.ApiResponse;
import com.hireai.domain.dto.response.MatchResultResponse;
import com.hireai.service.JobMatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/match")
@RequiredArgsConstructor
@Tag(name = "Matching", description = "AI-powered job matching APIs")
public class MatchController {

    private final JobMatchService jobMatchService;

    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasRole('RECRUITER')")
    @Operation(summary = "Get top matching candidates for a job")
    public ResponseEntity<ApiResponse<List<MatchResultResponse>>> getTopCandidates(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(jobMatchService.getTopCandidatesForJob(jobId, limit)));
    }

    @GetMapping("/candidate/{candidateId}")
    @Operation(summary = "Get recommended jobs for a candidate")
    public ResponseEntity<ApiResponse<List<MatchResultResponse>>> getRecommendedJobs(
            @PathVariable Long candidateId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(jobMatchService.getRecommendedJobsForCandidate(candidateId, limit)));
    }

    @GetMapping("/explain/{applicationId}")
    @Operation(summary = "Get AI match explanation for an application")
    public ResponseEntity<ApiResponse<MatchResultResponse>> getMatchExplanation(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(ApiResponse.ok(jobMatchService.getMatchExplanation(applicationId)));
    }
}
