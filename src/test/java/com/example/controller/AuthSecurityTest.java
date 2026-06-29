package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Student;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.entity.TeacherAcademyRole;
import com.example.repository.AcademyClassRepository;
import com.example.repository.AcademyRepository;
import com.example.repository.StudentRepository;
import com.example.repository.TeacherAcademyRepository;
import com.example.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired AcademyRepository academyRepository;
    @Autowired AcademyClassRepository academyClassRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired TeacherRepository teacherRepository;
    @Autowired TeacherAcademyRepository teacherAcademyRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void teacher_login_migrates_legacy_plain_pin_to_hash() throws Exception {
        Academy academy = academyRepository.save(Academy.builder().name("보안학원").build());
        Teacher teacher = new Teacher();
        teacher.setName("보안선생");
        teacher.setUsername("security-teacher");
        teacher.setPin("123456");
        teacher = teacherRepository.save(teacher);
        teacherAcademyRepository.save(TeacherAcademy.builder()
                .teacherId(teacher.getId())
                .academyId(academy.getId())
                .role(TeacherAcademyRole.ACADEMY_ADMIN)
                .build());

        mockMvc.perform(post("/api/auth/teacher/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"security-teacher","pin":"123456"}
                                """))
                .andExpect(status().isOk());

        Teacher reloaded = teacherRepository.findById(teacher.getId()).orElseThrow();
        assertThat(reloaded.getPin()).isNull();
        assertThat(reloaded.getPinHash()).isNotBlank();
        assertThat(reloaded.getFailedLoginCount()).isZero();
    }

    @Test
    void legacy_plain_pin_overrides_stale_hash_after_rollback() throws Exception {
        Academy academy = academyRepository.save(Academy.builder().name("롤백 학원").build());
        Teacher teacher = new Teacher();
        teacher.setName("롤백 선생");
        teacher.setUsername("rollback-teacher");
        teacher.setPin("654321");
        teacher.setPinHash(passwordEncoder.encode("123456"));
        teacher = teacherRepository.save(teacher);
        teacherAcademyRepository.save(TeacherAcademy.builder()
                .teacherId(teacher.getId())
                .academyId(academy.getId())
                .role(TeacherAcademyRole.ACADEMY_ADMIN)
                .build());

        mockMvc.perform(post("/api/auth/teacher/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"rollback-teacher","pin":"654321"}
                                """))
                .andExpect(status().isOk());

        Teacher reloaded = teacherRepository.findById(teacher.getId()).orElseThrow();
        assertThat(reloaded.getPin()).isNull();
        assertThat(passwordEncoder.matches("654321", reloaded.getPinHash())).isTrue();
    }

    @Test
    void repeated_student_login_failures_temporarily_lock_account() throws Exception {
        Academy academy = academyRepository.save(Academy.builder().name("잠금학원").build());
        AcademyClass clazz = academyClassRepository.save(AcademyClass.builder()
                .name("잠금반")
                .academy(academy)
                .build());
        Student student = Student.builder()
                .name("잠금학생")
                .grade("고1")
                .school("테스트고")
                .pin("1111")
                .academy(academy)
                .academyClass(clazz)
                .build();
        student = studentRepository.save(student);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/student/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"studentId":%d,"pin":"9999"}
                                    """.formatted(student.getId())))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"pin":"1111"}
                                """.formatted(student.getId())))
                .andExpect(status().isTooManyRequests());

        Student reloaded = studentRepository.findById(student.getId()).orElseThrow();
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(5);
        assertThat(reloaded.getLockedUntil()).isNotNull();
    }
}
