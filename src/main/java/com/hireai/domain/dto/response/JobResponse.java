package com.hireai.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class JobResponse {

    private Long id;
    private String title;
    private String description;
    private String companyName;
    private String location;
    private String employmentType;
    private String experienceLevel;
    private String mustHaveSkills;
    private String niceToHaveSkills;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String status;
    private Long recruiterId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
