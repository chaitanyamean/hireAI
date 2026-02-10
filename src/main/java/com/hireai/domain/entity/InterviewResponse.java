package com.hireai.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview_responses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InterviewResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private InterviewQuestion question;

    @Column(columnDefinition = "TEXT")
    private String answerText;

    private BigDecimal aiScore;

    @Column(columnDefinition = "TEXT")
    private String aiFeedback;

    private LocalDateTime answeredAt;
}
