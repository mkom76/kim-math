package com.example.service;

import java.util.Arrays;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class QuestionNumberNormalizer {
    private QuestionNumberNormalizer() {}

    public static String normalize(String input, int questionCount) {
        if (input == null || input.isBlank()) {
            return null;
        }
        TreeSet<Integer> sorted = new TreeSet<>();
        for (String token : input.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            int n;
            try {
                n = Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("문항 번호는 숫자여야 합니다: " + trimmed);
            }
            if (n < 1 || n > questionCount) {
                throw new IllegalArgumentException("문항 번호는 1~" + questionCount + " 범위여야 합니다: " + n);
            }
            sorted.add(n);
        }
        if (sorted.isEmpty()) return null;
        return sorted.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
