package com.hireai.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CandidateResponse {

    private Long id;
    private Long userId;
    private String fullName;
    private String email;
    private String phone;
    private String linkedinUrl;
    private Integer yearsOfExperience;
    private String currentTitle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
