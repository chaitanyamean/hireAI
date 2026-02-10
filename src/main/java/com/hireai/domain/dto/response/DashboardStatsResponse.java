package com.hireai.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DashboardStatsResponse {

    private long totalJobs;
    private long totalCandidates;
    private long totalApplications;
    private Map<String, Long> pipelineCounts;
}
