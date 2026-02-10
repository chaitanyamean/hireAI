package com.hireai.ai.dto;

import java.util.List;

public record MatchExplanation(
        int score,
        String explanation,
        List<String> highlights
) {}
