package com.hireai.ai.dto;

import java.util.List;

public record ScreeningResult(
        boolean qualified,
        List<String> redFlags,
        List<String> missingRequirements,
        String notes
) {}
