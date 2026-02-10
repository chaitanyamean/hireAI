package com.hireai.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ApplicationResponse {

    private Long id;
    private Long jobId;
    private Long candidateId;
    private Long resumeId;
    private String status;
    private BigDecimal aiMatchScore;
    private String aiScreeningNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
