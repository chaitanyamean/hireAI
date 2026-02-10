package com.hireai.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InterviewQuestionResponse {

    private Long id;
    private String questionText;
    private String category;
    private String difficulty;
    private Integer orderIndex;
}
