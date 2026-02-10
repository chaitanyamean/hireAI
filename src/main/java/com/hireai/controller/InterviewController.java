package com.hireai.controller;

import com.hireai.domain.dto.request.InterviewAnswerRequest;
import com.hireai.domain.dto.request.InterviewStartRequest;
import com.hireai.domain.dto.response.ApiResponse;
import com.hireai.domain.dto.response.InterviewDetailResponse;
import com.hireai.domain.dto.response.InterviewEvalResponse;
import com.hireai.domain.dto.response.InterviewQuestionResponse;
import com.hireai.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/interviews")
@RequiredArgsConstructor
@Tag(name = "Interviews", description = "AI-powered interview APIs")
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping("/start")
    @Operation(summary = "Start an AI interview")
    public ResponseEntity<ApiResponse<InterviewDetailResponse>> startInterview(
            @Valid @RequestBody InterviewStartRequest request) {
        InterviewDetailResponse response = interviewService.startInterview(
                request.getApplicationId(), request.getInterviewType());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Interview started", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get interview details")
    public ResponseEntity<ApiResponse<InterviewDetailResponse>> getInterview(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.getInterview(id)));
    }

    @GetMapping("/{id}/questions")
    @Operation(summary = "Get interview questions")
    public ResponseEntity<ApiResponse<List<InterviewQuestionResponse>>> getQuestions(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.getQuestions(id)));
    }

    @PostMapping("/{id}/answer")
    @Operation(summary = "Submit an answer to a question")
    public ResponseEntity<ApiResponse<InterviewEvalResponse.QuestionScore>> submitAnswer(
            @PathVariable Long id,
            @Valid @RequestBody InterviewAnswerRequest request) {
        InterviewEvalResponse.QuestionScore score = interviewService.submitAnswer(
                id, request.getQuestionId(), request.getAnswerText());
        return ResponseEntity.ok(ApiResponse.ok("Answer evaluated", score));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete interview and get evaluation")
    public ResponseEntity<ApiResponse<InterviewEvalResponse>> completeInterview(@PathVariable Long id) {
        InterviewEvalResponse response = interviewService.completeInterview(id);
        return ResponseEntity.ok(ApiResponse.ok("Interview completed", response));
    }

    @GetMapping("/{id}/result")
    @Operation(summary = "Get interview result")
    public ResponseEntity<ApiResponse<InterviewEvalResponse>> getResult(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.getResult(id)));
    }
}
