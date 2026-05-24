package com.example.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopicNormalizerTest {

    @Test
    void parses_with_slash_separator() {
        assertThat(TopicNormalizer.parse("함수/일차함수/그래프"))
                .containsExactly("함수", "일차함수", "그래프");
    }

    @Test
    void parses_with_gt_separator() {
        assertThat(TopicNormalizer.parse("함수 > 일차함수 > 그래프"))
                .containsExactly("함수", "일차함수", "그래프");
    }

    @Test
    void parses_with_canonical_separator() {
        assertThat(TopicNormalizer.parse("함수 › 일차함수"))
                .containsExactly("함수", "일차함수");
    }

    @Test
    void parses_mixed_separators() {
        assertThat(TopicNormalizer.parse("함수 / 일차함수 › 그래프 > 절편"))
                .containsExactly("함수", "일차함수", "그래프", "절편");
    }

    @Test
    void trims_each_segment() {
        assertThat(TopicNormalizer.parse("  함수  /  일차함수  "))
                .containsExactly("함수", "일차함수");
    }

    @Test
    void single_level_passes_through() {
        assertThat(TopicNormalizer.parse("일차함수")).containsExactly("일차함수");
    }

    @Test
    void empty_and_blank_input_returns_empty() {
        assertThat(TopicNormalizer.parse(null)).isEmpty();
        assertThat(TopicNormalizer.parse("")).isEmpty();
        assertThat(TopicNormalizer.parse("   ")).isEmpty();
    }

    @Test
    void drops_blank_intermediate_segments() {
        // double slash / leading slash → blanks ignored
        assertThat(TopicNormalizer.parse("//함수///일차함수///"))
                .containsExactly("함수", "일차함수");
    }

    @Test
    void caps_at_max_levels() {
        assertThat(TopicNormalizer.parse("a/b/c/d/e/f/g"))
                .hasSize(TopicNormalizer.MAX_LEVELS)
                .containsExactly("a", "b", "c", "d", "e");
    }

    @Test
    void truncates_long_segment_to_max_chars() {
        String longSeg = "x".repeat(TopicNormalizer.MAX_LEVEL_CHARS + 50);
        String[] parts = TopicNormalizer.parse(longSeg);
        assertThat(parts).hasSize(1);
        assertThat(parts[0]).hasSize(TopicNormalizer.MAX_LEVEL_CHARS);
    }

    @Test
    void format_joins_with_canonical_separator() {
        assertThat(TopicNormalizer.format(new String[]{"함수", "일차함수"}))
                .isEqualTo("함수 › 일차함수");
    }

    @Test
    void format_returns_null_for_empty() {
        assertThat(TopicNormalizer.format(new String[0])).isNull();
        assertThat(TopicNormalizer.format(null)).isNull();
    }

    @Test
    void normalize_roundtrips_mixed_to_canonical() {
        assertThat(TopicNormalizer.normalize("함수/일차함수 > 그래프"))
                .isEqualTo("함수 › 일차함수 › 그래프");
    }

    @Test
    void level_at_returns_segment_or_null() {
        String[] parts = TopicNormalizer.parse("a/b/c");
        assertThat(TopicNormalizer.levelAt(parts, 1)).isEqualTo("a");
        assertThat(TopicNormalizer.levelAt(parts, 3)).isEqualTo("c");
        assertThat(TopicNormalizer.levelAt(parts, 4)).isNull();
        assertThat(TopicNormalizer.levelAt(parts, 0)).isNull();
    }
}
