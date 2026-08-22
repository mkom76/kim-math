package com.example.service;

import com.example.entity.TestQuestion;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class TestAnswerMatcher {
    private TestAnswerMatcher() {
    }

    public static boolean matches(TestQuestion question, String studentAnswer) {
        if (question.getAnswer() == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(question.getMultipleAnswers())) {
            return Objects.equals(question.getAnswer(), studentAnswer);
        }

        Set<String> correctAnswers = toAnswerSet(question.getAnswer());
        Set<String> studentAnswers = toAnswerSet(studentAnswer);
        return !correctAnswers.isEmpty() && correctAnswers.equals(studentAnswers);
    }

    public static String canonicalize(String answer, boolean multipleAnswers) {
        if (!multipleAnswers || answer == null) {
            return answer;
        }
        return String.join(",", toAnswerSet(answer));
    }

    private static Set<String> toAnswerSet(String answer) {
        if (answer == null || answer.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(answer.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
