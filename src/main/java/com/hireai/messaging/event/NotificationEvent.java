package com.hireai.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationEvent {
    private String recipientEmail;
    private String type;
    private String subject;
    private String body;
}
