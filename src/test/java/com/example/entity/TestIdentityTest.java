package com.example.entity;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.assertThat;

class TestIdentityTest {
    @Test void identity_does_not_walk_bidirectional_associations_or_change_when_fields_change() {
        var exam = com.example.entity.Test.builder().id(10L).title("가상 시험").build();
        var lesson = Lesson.builder().test(exam).build();
        var clazz = AcademyClass.builder().build();
        exam.setLesson(lesson); exam.setAcademyClass(clazz); clazz.getTests().add(exam);
        var set = new HashSet<com.example.entity.Test>(); set.add(exam);
        exam.setTitle("가상 시험 수정");
        assertThat(set.contains(exam)).isTrue();
        var same = com.example.entity.Test.builder().id(10L).build();
        assertThat(exam.equals(same)).isTrue();
        assertThat(exam.hashCode()).isEqualTo(same.hashCode());
        assertThat(exam.equals(com.example.entity.Test.builder().id(11L).build())).isFalse();
    }

    @Test void new_entities_are_distinct_and_hash_is_stable_after_assigning_id() {
        var first = new com.example.entity.Test(); var second = new com.example.entity.Test();
        assertThat(first.equals(second)).isFalse();
        assertThat(first.equals(first)).isTrue();
        int hash = first.hashCode(); first.setId(1L);
        assertThat(first.hashCode()).isEqualTo(hash);
    }
}
