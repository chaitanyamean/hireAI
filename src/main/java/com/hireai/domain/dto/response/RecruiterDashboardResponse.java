package com.hireai.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RecruiterDashboardResponse {

    private long totalJobs;
    private long totalApplications;
    private long totalCandidates;
    private Map<String, Long> pipelineCounts;
    private List<ApplicationResponse> recentApplications;
}
