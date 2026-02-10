package com.hireai.ai.dto;

import java.util.List;

public record ParsedResume(
        String name,
        String email,
        String phone,
        List<String> skills,
        List<Experience> experience,
        List<Education> education,
        String summary
) {
    public record Experience(String company, String title, String duration, String description) {}
    public record Education(String institution, String degree, String year) {}
}
