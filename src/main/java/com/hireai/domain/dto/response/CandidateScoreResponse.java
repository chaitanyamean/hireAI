package com.hireai.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CandidateScoreResponse {

    private BigDecimal score;
    private String reasoning;
    private List<String> strengths;
    private List<String> weaknesses;
}
