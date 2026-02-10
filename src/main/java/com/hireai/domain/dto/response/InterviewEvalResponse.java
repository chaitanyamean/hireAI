package com.hireai.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InterviewEvalResponse {

    private Long interviewId;
    private BigDecimal overallScore;
    private String recommendation;
    private List<QuestionScore> questionScores;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class QuestionScore {
        private Long questionId;
        private BigDecimal score;
        private String feedback;
    }
}
