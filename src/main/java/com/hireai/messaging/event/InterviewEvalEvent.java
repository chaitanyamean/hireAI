package com.hireai.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InterviewEvalEvent {
    private Long interviewId;
    private Long questionId;
    private String answerText;
}
