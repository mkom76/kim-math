package com.example.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionNumberNormalizerTest {

    @Test
    void deduplicates_and_sorts_with_whitespace() {
        assertThat(QuestionNumberNormalizer.normalize("3, 7, 7, 12", 20)).isEqualTo("3,7,12");
    }

    @Test
    void sorts_unsorted_input() {
        assertThat(QuestionNumberNormalizer.normalize("  3,1,2 ", 10)).isEqualTo("1,2,3");
    }

    @Test
    void treats_null_and_empty_as_null() {
        assertThat(QuestionNumberNormalizer.normalize(null, 10)).isNull();
        assertThat(QuestionNumberNormalizer.normalize("", 10)).isNull();
        assertThat(QuestionNumberNormalizer.normalize("   ", 10)).isNull();
    }

    @Test
    void rejects_number_above_question_count() {
        assertThatThrownBy(() -> QuestionNumberNormalizer.normalize("15", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_zero_or_negative() {
        assertThatThrownBy(() -> QuestionNumberNormalizer.normalize("0", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuestionNumberNormalizer.normalize("-1", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_non_numeric() {
        assertThatThrownBy(() -> QuestionNumberNormalizer.normalize("abc", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuestionNumberNormalizer.normalize("1,a,3", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
