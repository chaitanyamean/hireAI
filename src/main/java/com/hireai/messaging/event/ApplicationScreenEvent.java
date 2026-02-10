package com.hireai.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ApplicationScreenEvent {
    private Long applicationId;
    private Long jobId;
    private Long candidateId;
    private Long resumeId;
}
