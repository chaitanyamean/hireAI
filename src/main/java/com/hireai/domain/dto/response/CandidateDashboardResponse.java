package com.hireai.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CandidateDashboardResponse {

    private long totalApplications;
    private Map<String, Long> statusCounts;
    private List<ApplicationResponse> applications;
}
