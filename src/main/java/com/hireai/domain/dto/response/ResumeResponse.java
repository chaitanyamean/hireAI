package com.hireai.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ResumeResponse {

    private Long id;
    private String fileName;
    private String parseStatus;
    private String parsedData;
    private String skills;
    private BigDecimal aiScore;
    private LocalDateTime createdAt;
}
