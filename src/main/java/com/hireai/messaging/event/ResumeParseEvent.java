package com.hireai.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ResumeParseEvent {
    private Long resumeId;
    private Long candidateId;
    private String filePath;
}
