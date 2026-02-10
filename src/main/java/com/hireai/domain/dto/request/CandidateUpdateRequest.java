package com.hireai.domain.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CandidateUpdateRequest {

    private String phone;
    private String linkedinUrl;
    private Integer yearsOfExperience;
    private String currentTitle;
}
