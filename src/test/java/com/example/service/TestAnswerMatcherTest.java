package com.example.service;

import com.example.entity.TestQuestion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAnswerMatcherTest {

    @Test
    void multiple_answers_match_as_an_exact_set_regardless_of_order() {
        TestQuestion question = TestQuestion.builder()
                .answer("1,3")
                .multipleAnswers(true)
                .build();

        assertThat(TestAnswerMatcher.matches(question, "3, 1")).isTrue();
        assertThat(TestAnswerMatcher.matches(question, "1")).isFalse();
        assertThat(TestAnswerMatcher.matches(question, "1,3,5")).isFalse();
    }

    @Test
    void single_answers_keep_the_existing_exact_match_rule() {
        TestQuestion question = TestQuestion.builder()
                .answer("x=2")
                .multipleAnswers(false)
                .build();

        assertThat(TestAnswerMatcher.matches(question, "x=2")).isTrue();
        assertThat(TestAnswerMatcher.matches(question, "x = 2")).isFalse();
    }

    @Test
    void a_missing_correct_answer_never_matches() {
        TestQuestion question = TestQuestion.builder()
                .answer(null)
                .multipleAnswers(false)
                .build();

        assertThat(TestAnswerMatcher.matches(question, null)).isFalse();
    }

    @Test
    void canonicalize_deduplicates_and_orders_multiple_answers() {
        assertThat(TestAnswerMatcher.canonicalize("3, 1,3", true)).isEqualTo("1,3");
    }
}
