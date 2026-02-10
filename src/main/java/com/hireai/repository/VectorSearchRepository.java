package com.hireai.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class VectorSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Store embedding for a resume.
     */
    public void saveResumeEmbedding(Long resumeId, float[] embedding) {
        String vectorStr = toVectorString(embedding);
        jdbcTemplate.update("UPDATE resumes SET embedding = ?::vector WHERE id = ?", vectorStr, resumeId);
        log.info("Stored embedding for resume {}", resumeId);
    }

    /**
     * Store embedding for a job.
     */
    public void saveJobEmbedding(Long jobId, float[] embedding) {
        String vectorStr = toVectorString(embedding);
        jdbcTemplate.update("UPDATE jobs SET embedding = ?::vector WHERE id = ?", vectorStr, jobId);
        log.info("Stored embedding for job {}", jobId);
    }

    /**
     * Find top matching candidates for a job using cosine similarity.
     * Returns list of maps with: resumeId, candidateId, candidateName, similarity
     */
    public List<Map<String, Object>> findMatchingCandidates(Long jobId, int topK) {
        String sql = """
                SELECT r.id AS resume_id,
                       r.candidate_id,
                       u.full_name AS candidate_name,
                       r.experience_summary,
                       r.skills,
                       1 - (r.embedding <=> j.embedding) AS similarity
                FROM resumes r
                JOIN jobs j ON j.id = ?
                JOIN candidates c ON c.id = r.candidate_id
                JOIN users u ON u.id = c.user_id
                WHERE r.embedding IS NOT NULL
                  AND j.embedding IS NOT NULL
                ORDER BY r.embedding <=> j.embedding
                LIMIT ?
                """;
        return jdbcTemplate.queryForList(sql, jobId, topK);
    }

    /**
     * Find top matching jobs for a candidate using cosine similarity.
     * Uses the candidate's latest resume embedding.
     */
    public List<Map<String, Object>> findMatchingJobs(Long candidateId, int topK) {
        String sql = """
                SELECT j.id AS job_id,
                       j.title,
                       j.must_have_skills,
                       j.experience_level,
                       j.location,
                       1 - (j.embedding <=> r.embedding) AS similarity
                FROM jobs j
                JOIN resumes r ON r.candidate_id = ?
                WHERE j.embedding IS NOT NULL
                  AND r.embedding IS NOT NULL
                  AND j.status = 'ACTIVE'
                ORDER BY j.embedding <=> r.embedding
                LIMIT ?
                """;
        return jdbcTemplate.queryForList(sql, candidateId, topK);
    }

    /**
     * Check if a resume has an embedding stored.
     */
    public boolean hasResumeEmbedding(Long resumeId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM resumes WHERE id = ? AND embedding IS NOT NULL", Integer.class, resumeId);
        return count != null && count > 0;
    }

    /**
     * Check if a job has an embedding stored.
     */
    public boolean hasJobEmbedding(Long jobId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jobs WHERE id = ? AND embedding IS NOT NULL", Integer.class, jobId);
        return count != null && count > 0;
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
