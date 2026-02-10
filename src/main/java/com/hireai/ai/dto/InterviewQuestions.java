package com.hireai.ai.dto;

import java.util.List;

public record InterviewQuestions(
        List<Question> questions
) {
    public record Question(String questionText, String category, String difficulty) {}
}
