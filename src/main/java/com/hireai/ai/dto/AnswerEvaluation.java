package com.hireai.ai.dto;

import java.util.List;

public record AnswerEvaluation(
        int score,
        String feedback,
        List<String> strengths,
        List<String> improvements
) {}
