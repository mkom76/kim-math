package com.example.service;

import com.example.dto.StudentClassMembershipDto;
import com.example.entity.AcademyClass;
import com.example.entity.Student;
import com.example.entity.StudentClassEnrollment;
import com.example.entity.StudentClassEnrollmentStatus;
import com.example.repository.StudentClassEnrollmentRepository;
import com.example.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class StudentClassEnrollmentService {
    private static final String CLASS_ENDED_REASON = "CLASS_ENDED";

    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;

    public StudentClassEnrollment ensureActiveEnrollment(
            Student student,
            AcademyClass academyClass,
            Long createdByTeacherId) {
        requireSameAcademy(student, academyClass);

        StudentClassEnrollment enrollment = enrollmentRepository
                .findByStudentIdAndAcademyClassId(student.getId(), academyClass.getId())
                .orElseGet(() -> StudentClassEnrollment.builder()
                        .student(student)
                        .academyClass(academyClass)
                        .startedAt(LocalDateTime.now())
                        .createdByTeacherId(createdByTeacherId)
                        .build());

        enrollment.setStatus(StudentClassEnrollmentStatus.ACTIVE);
        enrollment.setEndedAt(null);
        enrollment.setEndReason(null);
        if (enrollment.getStartedAt() == null) {
            enrollment.setStartedAt(LocalDateTime.now());
        }
        return enrollmentRepository.save(enrollment);
    }

    @Transactional(readOnly = true)
    public List<StudentClassMembershipDto> getMemberships(Long studentId) {
        Student student = requireStudent(studentId);
        List<StudentClassEnrollment> enrollments =
                enrollmentRepository.findByStudentIdOrderByStartedAtDescIdDesc(studentId);

        if (enrollments.isEmpty()) {
            return List.of(legacyMembership(student));
        }
        enrollments.forEach(enrollment -> requireSameAcademy(
                student, enrollment.getAcademyClass()));

        boolean hasActive = enrollments.stream()
                .anyMatch(enrollment -> enrollment.getStatus() == StudentClassEnrollmentStatus.ACTIVE);
        boolean completedFallbackAssigned = false;
        List<StudentClassMembershipDto> memberships = new java.util.ArrayList<>(enrollments.size());

        for (StudentClassEnrollment enrollment : enrollments) {
            boolean selectable = enrollment.getStatus() == StudentClassEnrollmentStatus.ACTIVE;
            if (!hasActive
                    && !completedFallbackAssigned
                    && enrollment.getStatus() == StudentClassEnrollmentStatus.COMPLETED) {
                selectable = true;
                completedFallbackAssigned = true;
            }
            memberships.add(StudentClassMembershipDto.from(enrollment, selectable));
        }
        return memberships;
    }

    @Transactional(readOnly = true)
    public StudentClassMembershipDto getDefaultMembership(Long studentId) {
        return getMemberships(studentId).stream()
                .filter(StudentClassMembershipDto::isSelectable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("선택 가능한 반 소속이 없습니다"));
    }

    @Transactional(readOnly = true)
    public StudentClassMembershipDto requireSelectableMembership(Long studentId, Long classId) {
        return getMemberships(studentId).stream()
                .filter(membership -> membership.getClassId().equals(classId))
                .filter(StudentClassMembershipDto::isSelectable)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("선택할 수 없는 반입니다"));
    }

    @Transactional(readOnly = true)
    public boolean isActivelyEnrolled(Long studentId, Long classId) {
        Optional<StudentClassEnrollment> enrollment =
                enrollmentRepository.findByStudentIdAndAcademyClassId(studentId, classId);
        if (enrollment.isPresent()) {
            return enrollment.get().getStatus() == StudentClassEnrollmentStatus.ACTIVE;
        }
        if (enrollmentRepository.existsByStudentId(studentId)) {
            return false;
        }
        Student student = requireStudent(studentId);
        return student.getAcademyClass() != null
                && student.getAcademyClass().getId().equals(classId)
                && !student.getAcademyClass().isEnded();
    }

    public void completeClassEnrollments(Long classId, LocalDateTime endedAt) {
        List<StudentClassEnrollment> activeEnrollments = enrollmentRepository
                .findByAcademyClassIdAndStatus(classId, StudentClassEnrollmentStatus.ACTIVE);
        activeEnrollments.forEach(enrollment -> {
            enrollment.setStatus(StudentClassEnrollmentStatus.COMPLETED);
            enrollment.setEndedAt(endedAt);
            enrollment.setEndReason(CLASS_ENDED_REASON);
        });
        enrollmentRepository.saveAll(activeEnrollments);
    }

    public void reopenClassEnrollments(Long classId) {
        List<StudentClassEnrollment> completedEnrollments = enrollmentRepository
                .findByAcademyClassIdAndStatus(classId, StudentClassEnrollmentStatus.COMPLETED);
        completedEnrollments.stream()
                .filter(enrollment -> CLASS_ENDED_REASON.equals(enrollment.getEndReason()))
                .forEach(enrollment -> {
                    enrollment.setStatus(StudentClassEnrollmentStatus.ACTIVE);
                    enrollment.setEndedAt(null);
                    enrollment.setEndReason(null);
                });
        enrollmentRepository.saveAll(completedEnrollments);
    }

    @Transactional(readOnly = true)
    public List<Student> getActiveStudentsForClass(Long classId) {
        Map<Long, Student> students = new LinkedHashMap<>();
        enrollmentRepository.findByAcademyClassIdAndStatus(
                        classId, StudentClassEnrollmentStatus.ACTIVE)
                .forEach(enrollment -> students.put(
                        enrollment.getStudent().getId(), enrollment.getStudent()));

        // Compatibility for rows created before the enrollment migration.
        studentRepository.findByAcademyClassId(classId).stream()
                .filter(student -> !enrollmentRepository.existsByStudentId(student.getId()))
                .forEach(student -> students.put(student.getId(), student));
        return List.copyOf(students.values());
    }

    @Transactional(readOnly = true)
    public boolean hasEnrollmentForClass(Long classId) {
        return enrollmentRepository.existsByAcademyClassId(classId);
    }

    private Student requireStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다"));
    }

    private StudentClassMembershipDto legacyMembership(Student student) {
        AcademyClass academyClass = student.getAcademyClass();
        if (academyClass == null) {
            throw new IllegalStateException("학생의 기존 반 정보가 없습니다");
        }
        boolean readOnly = academyClass.isEnded();
        return StudentClassMembershipDto.builder()
                .classId(academyClass.getId())
                .className(academyClass.getName())
                .academyId(academyClass.getAcademy().getId())
                .academyName(academyClass.getAcademy().getName())
                .status(readOnly
                        ? StudentClassEnrollmentStatus.COMPLETED
                        : StudentClassEnrollmentStatus.ACTIVE)
                .startedAt(student.getCreatedAt())
                .endedAt(academyClass.getEndedAt())
                .selectable(true)
                .readOnly(readOnly)
                .build();
    }

    private void requireSameAcademy(Student student, AcademyClass academyClass) {
        Long studentAcademyId = student.getAcademy() == null ? null : student.getAcademy().getId();
        Long classAcademyId = academyClass.getAcademy() == null ? null : academyClass.getAcademy().getId();
        if (studentAcademyId == null || !studentAcademyId.equals(classAcademyId)) {
            throw new IllegalArgumentException("학생과 반의 학원이 다릅니다");
        }
    }
}
