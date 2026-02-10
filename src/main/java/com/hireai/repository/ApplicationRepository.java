package com.hireai.repository;

import com.hireai.domain.entity.Application;
import com.hireai.domain.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByJobId(Long jobId);

    List<Application> findByCandidateId(Long candidateId);

    List<Application> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);

    boolean existsByJobIdAndCandidateId(Long jobId, Long candidateId);

    @Query("SELECT COUNT(a) FROM Application a WHERE a.status = :status")
    long countByStatus(@Param("status") ApplicationStatus status);

    @Query("SELECT COUNT(a) FROM Application a WHERE a.candidate.id = :candidateId AND a.status = :status")
    long countByCandidateIdAndStatus(@Param("candidateId") Long candidateId, @Param("status") ApplicationStatus status);

    Page<Application> findByStatus(ApplicationStatus status, Pageable pageable);

    @Query("SELECT a FROM Application a ORDER BY a.createdAt DESC")
    Page<Application> findAllOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT a FROM Application a WHERE a.status = :status ORDER BY a.createdAt DESC")
    Page<Application> findByStatusOrderByCreatedAtDesc(@Param("status") ApplicationStatus status, Pageable pageable);
}
