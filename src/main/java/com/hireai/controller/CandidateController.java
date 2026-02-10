package com.hireai.controller;

import com.hireai.domain.dto.request.CandidateUpdateRequest;
import com.hireai.domain.dto.response.ApiResponse;
import com.hireai.domain.dto.response.CandidateResponse;
import com.hireai.security.SecurityUtils;
import com.hireai.service.CandidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/candidates")
@RequiredArgsConstructor
@Tag(name = "Candidates", description = "Candidate profile APIs")
public class CandidateController {

    private final CandidateService candidateService;
    private final SecurityUtils securityUtils;

    @GetMapping("/me")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Get own candidate profile")
    public ResponseEntity<ApiResponse<CandidateResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.ok(candidateService.getProfile(securityUtils.getCurrentUser())));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Update own candidate profile")
    public ResponseEntity<ApiResponse<CandidateResponse>> updateProfile(
            @Valid @RequestBody CandidateUpdateRequest request) {
        CandidateResponse response = candidateService.updateProfile(request, securityUtils.getCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("Profile updated successfully", response));
    }
}
