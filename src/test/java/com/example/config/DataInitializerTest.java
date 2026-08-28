package com.example.config;

import com.example.dto.StudentClassMembershipDto;
import com.example.entity.Student;
import com.example.entity.StudentClassEnrollmentStatus;
import com.example.repository.StudentClassEnrollmentRepository;
import com.example.repository.StudentHomeworkRepository;
import com.example.repository.StudentRepository;
import com.example.service.StudentClassEnrollmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles({"test", "local"})
@Transactional
class DataInitializerTest {

    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentClassEnrollmentRepository enrollmentRepository;
    @Autowired private StudentClassEnrollmentService enrollmentService;
    @Autowired private StudentHomeworkRepository studentHomeworkRepository;

    @Test
    void local_seed_contains_complete_multi_class_scenarios() {
        List<Student> students = studentRepository.findAll();

        assertThat(students).hasSize(30);
        assertThat(enrollmentRepository.count()).isEqualTo(32);
        assertThat(students)
                .allMatch(student -> enrollmentRepository.existsByStudentId(student.getId()));

        Student multiClassStudent = findStudent(students, "김민준");
        List<StudentClassMembershipDto> memberships = enrollmentService.getMemberships(multiClassStudent.getId());

        assertThat(memberships)
                .extracting(
                        StudentClassMembershipDto::getClassName,
                        StudentClassMembershipDto::getStatus,
                        StudentClassMembershipDto::isSelectable,
                        StudentClassMembershipDto::isReadOnly)
                .containsExactlyInAnyOrder(
                        tuple("고1 수학 기본반", StudentClassEnrollmentStatus.ACTIVE, true, false),
                        tuple("고1 수학 심화반", StudentClassEnrollmentStatus.ACTIVE, true, false),
                        tuple("고2 수학 기본반", StudentClassEnrollmentStatus.COMPLETED, false, true));
        assertThat(enrollmentService.getDefaultMembership(multiClassStudent.getId()).getClassName())
                .isEqualTo("고1 수학 기본반");
        assertThat(studentHomeworkRepository.findByStudentId(multiClassStudent.getId()))
                .extracting(studentHomework -> studentHomework.getHomework().getAcademyClass().getName())
                .contains("고1 수학 기본반", "고1 수학 심화반");

        Student completedClassStudent = findStudent(students, "문지훈");
        StudentClassMembershipDto completedMembership =
                enrollmentService.getDefaultMembership(completedClassStudent.getId());

        assertThat(completedMembership.getClassName()).isEqualTo("고3 수학 종강반");
        assertThat(completedMembership.getStatus()).isEqualTo(StudentClassEnrollmentStatus.COMPLETED);
        assertThat(completedMembership.isSelectable()).isTrue();
        assertThat(completedMembership.isReadOnly()).isTrue();
        assertThat(completedClassStudent.getAcademyClass().isEnded()).isTrue();
        assertThat(studentHomeworkRepository.findByStudentId(completedClassStudent.getId()))
                .singleElement()
                .satisfies(studentHomework -> assertThat(studentHomework.getHomework().getTitle())
                        .isEqualTo("종강반 마지막 복습"));
    }

    private Student findStudent(List<Student> students, String name) {
        return students.stream()
                .filter(student -> name.equals(student.getName()))
                .findFirst()
                .orElseThrow();
    }
}
