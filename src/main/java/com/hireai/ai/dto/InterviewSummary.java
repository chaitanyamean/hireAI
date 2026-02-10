package com.hireai.ai.dto;

import java.util.List;

public record InterviewSummary(
        int overallScore,
        String recommendation,
        String summary,
        List<String> keyStrengths,
        List<String> areasOfConcern
) {}
