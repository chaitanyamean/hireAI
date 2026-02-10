package com.hireai.domain.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class JobCreateRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private String companyName;

    private String location;

    private String employmentType;

    private String experienceLevel;

    private String mustHaveSkills;

    private String niceToHaveSkills;

    @Min(value = 0, message = "Minimum salary must be non-negative")
    private BigDecimal salaryMin;

    @Min(value = 0, message = "Maximum salary must be non-negative")
    private BigDecimal salaryMax;
}
