package com.hireai.repository;

import com.hireai.domain.entity.InterviewResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewResponseRepository extends JpaRepository<InterviewResponse, Long> {

    List<InterviewResponse> findByQuestionId(Long questionId);
}
