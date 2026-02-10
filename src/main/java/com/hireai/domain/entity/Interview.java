package com.hireai.domain.entity;

import com.hireai.domain.enums.InterviewStatus;
import com.hireai.domain.enums.InterviewType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "interviews")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @Enumerated(EnumType.STRING)
    private InterviewType interviewType;

    @Enumerated(EnumType.STRING)
    private InterviewStatus status;

    private BigDecimal overallScore;

    @Column(columnDefinition = "TEXT")
    private String aiRecommendation;

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL)
    private List<InterviewQuestion> questions;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
