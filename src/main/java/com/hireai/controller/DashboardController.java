package com.hireai.controller;

import com.hireai.domain.dto.response.ApiResponse;
import com.hireai.domain.dto.response.CandidateDashboardResponse;
import com.hireai.domain.dto.response.DashboardStatsResponse;
import com.hireai.domain.dto.response.RecruiterDashboardResponse;
import com.hireai.domain.entity.User;
import com.hireai.security.SecurityUtils;
import com.hireai.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard statistics APIs")
public class DashboardController {

    private final DashboardService dashboardService;
    private final SecurityUtils securityUtils;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('RECRUITER')")
    @Operation(summary = "Get dashboard statistics (cached)")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getStats()));
    }

    @GetMapping("/recruiter")
    @PreAuthorize("hasRole('RECRUITER')")
    @Operation(summary = "Get recruiter pipeline dashboard")
    public ResponseEntity<ApiResponse<RecruiterDashboardResponse>> getRecruiterDashboard() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getRecruiterDashboard()));
    }

    @GetMapping("/candidate")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Get candidate application status dashboard")
    public ResponseEntity<ApiResponse<CandidateDashboardResponse>> getCandidateDashboard() {
        User user = securityUtils.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getCandidateDashboard(user)));
    }
}
