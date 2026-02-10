package com.hireai.domain.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class JobUpdateRequest {

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
}
