package com.hireai.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ResumeAnalysisResponse {

    private String name;
    private List<String> skills;
    private List<ExperienceEntry> experience;
    private List<EducationEntry> education;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ExperienceEntry {
        private String title;
        private String company;
        private String duration;
        private String description;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EducationEntry {
        private String degree;
        private String institution;
        private String year;
    }
}
