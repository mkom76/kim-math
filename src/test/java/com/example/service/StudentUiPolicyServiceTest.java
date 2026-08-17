package com.example.service;

import com.example.entity.Academy;
import com.example.entity.Student;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudentUiPolicyServiceTest {

    private final StudentUiPolicyService service = new StudentUiPolicyService("2, 4");

    @Test
    void academies_two_and_four_default_to_v2() {
        assertThat(service.defaultModeForAcademy(2L)).isEqualTo(StudentUiPolicyService.V2);
        assertThat(service.defaultModeForAcademy(4L)).isEqualTo(StudentUiPolicyService.V2);
    }

    @Test
    void other_academies_default_to_legacy() {
        assertThat(service.defaultModeForAcademy(1L)).isEqualTo(StudentUiPolicyService.LEGACY);
        assertThat(service.defaultModeForAcademy(null)).isEqualTo(StudentUiPolicyService.LEGACY);
    }

    @Test
    void student_academy_controls_the_default_mode() {
        Student student = Student.builder()
                .academy(Academy.builder().id(4L).name("V2 학원").build())
                .build();

        assertThat(service.defaultModeForStudent(student)).isEqualTo(StudentUiPolicyService.V2);
    }
}
