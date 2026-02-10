package com.hireai.repository;

import com.hireai.domain.entity.Resume;
import com.hireai.domain.enums.ParseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findByCandidateId(Long candidateId);

    List<Resume> findByParseStatus(ParseStatus parseStatus);
}
