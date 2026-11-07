package com.example.dto;

import java.util.List;
import java.util.Map;

public record SaveAnswersRequest(Map<Integer, String> answers, List<QuestionSnapshot> expectedQuestions) {
}
