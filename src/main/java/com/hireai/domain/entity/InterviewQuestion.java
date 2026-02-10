package com.hireai.domain.entity;

import com.hireai.domain.enums.QuestionCategory;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "interview_questions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InterviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id")
    private Interview interview;

    @Column(columnDefinition = "TEXT")
    private String questionText;

    @Enumerated(EnumType.STRING)
    private QuestionCategory category;

    private String difficulty;

    private Integer orderIndex;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL)
    private List<InterviewResponse> responses;
}
