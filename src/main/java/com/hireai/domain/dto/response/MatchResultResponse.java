package com.hireai.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MatchResultResponse {

    private Long candidateId;
    private Long jobId;
    private BigDecimal similarityScore;
    private String aiExplanation;
}
