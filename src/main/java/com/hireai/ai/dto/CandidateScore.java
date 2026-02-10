package com.hireai.ai.dto;

import java.util.List;

public record CandidateScore(
        int score,
        List<String> strengths,
        List<String> weaknesses,
        String reasoning
) {}
