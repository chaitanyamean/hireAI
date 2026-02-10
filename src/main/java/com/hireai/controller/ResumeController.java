package com.hireai.controller;

import com.hireai.domain.dto.response.ApiResponse;
import com.hireai.domain.dto.response.ResumeResponse;
import com.hireai.security.SecurityUtils;
import com.hireai.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
@Tag(name = "Resumes", description = "Resume upload & management APIs")
public class ResumeController {

    private final ResumeService resumeService;
    private final SecurityUtils securityUtils;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Upload a resume (PDF or DOCX)")
    public ResponseEntity<ApiResponse<ResumeResponse>> uploadResume(@RequestParam("file") MultipartFile file) {
        ResumeResponse response = resumeService.uploadResume(file, securityUtils.getCurrentUser());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Resume uploaded successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get resume details by ID")
    public ResponseEntity<ApiResponse<ResumeResponse>> getResume(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(resumeService.getResumeById(id)));
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Check resume parse status")
    public ResponseEntity<ApiResponse<Map<String, String>>> getResumeStatus(@PathVariable Long id) {
        String status = resumeService.getResumeStatus(id);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", status)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Get current candidate's resumes")
    public ResponseEntity<ApiResponse<List<ResumeResponse>>> getMyResumes() {
        return ResponseEntity.ok(ApiResponse.ok(resumeService.getMyResumes(securityUtils.getCurrentUser())));
    }
}
