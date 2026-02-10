package com.hireai.repository;

import com.hireai.domain.entity.Job;
import com.hireai.domain.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByStatus(JobStatus status);

    List<Job> findByRecruiterId(Long recruiterId);

    List<Job> findByStatusAndRecruiterId(JobStatus status, Long recruiterId);

    @Query("SELECT j FROM Job j WHERE " +
           "(:status IS NULL OR j.status = :status) AND " +
           "(:keyword IS NULL OR LOWER(CAST(j.title AS String)) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%')) " +
           "OR LOWER(CAST(j.description AS String)) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%')))")
    Page<Job> searchJobs(@Param("status") JobStatus status,
                         @Param("keyword") String keyword,
                         Pageable pageable);
}
