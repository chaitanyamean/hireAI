package com.hireai.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InterviewDetailResponse {

    private Long id;
    private Long applicationId;
    private String interviewType;
    private String status;
    private BigDecimal overallScore;
    private String aiRecommendation;
    private List<InterviewQuestionResponse> questions;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
