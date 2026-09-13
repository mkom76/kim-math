package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.StudentEnrollmentDto;
import com.example.entity.AcademyClass;
import com.example.entity.Student;
import com.example.entity.StudentClassEnrollment;
import com.example.entity.StudentClassEnrollmentStatus;
import com.example.entity.TeacherAcademyRole;
import com.example.exception.ForbiddenException;
import com.example.repository.AcademyClassRepository;
import com.example.repository.StudentClassEnrollmentRepository;
import com.example.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class StudentEnrollmentManagementService {
    private final StudentRepository studentRepository;
    private final AcademyClassRepository classRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final StudentClassEnrollmentService enrollmentService;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public List<StudentEnrollmentDto> getEnrollments(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다"));
        authorizationService.assertCanAccessStudent(student);
        return visibleEnrollments(student);
    }

    @Transactional(readOnly = true)
    public List<StudentEnrollmentDto> visibleEnrollments(Student student) {
        List<StudentClassEnrollment> enrollments = enrollmentRepository
                .findByStudentIdOrderByStartedAtDescIdDesc(student.getId());
        if (enrollments.isEmpty()) {
            AcademyClass legacyClass = student.getAcademyClass();
            if (legacyClass == null || !authorizationService.canAccessClass(legacyClass)) {
                return List.of();
            }
            return List.of(StudentEnrollmentDto.builder()
                    .classId(legacyClass.getId()).className(legacyClass.getName())
                    .status(legacyClass.isEnded()
                            ? StudentClassEnrollmentStatus.COMPLETED : StudentClassEnrollmentStatus.ACTIVE)
                    .startedAt(student.getCreatedAt()).endedAt(legacyClass.getEndedAt())
                    .canManage(canManage(legacyClass, StudentClassEnrollmentStatus.ACTIVE))
                    .build());
        }
        return enrollments.stream()
                .filter(enrollment -> authorizationService.canAccessClass(enrollment.getAcademyClass()))
                .map(enrollment -> StudentEnrollmentDto.builder()
                        .classId(enrollment.getAcademyClass().getId())
                        .className(enrollment.getAcademyClass().getName())
                        .status(enrollment.getStatus())
                        .startedAt(enrollment.getStartedAt()).endedAt(enrollment.getEndedAt())
                        .endReason(enrollment.getEndReason())
                        .canManage(canManage(enrollment.getAcademyClass(), enrollment.getStatus()))
                        .build())
                .toList();
    }

    public List<StudentEnrollmentDto> add(Long studentId, Long classId) {
        Student student = requireStudentForUpdate(studentId);
        AcademyClass target = requireTargetClass(student, classId);
        materializeLegacyEnrollment(student);
        requireNotActive(studentId, classId);
        enrollmentService.ensureActiveEnrollment(student, target, TenantContext.current().teacherId());
        updateRepresentativeClass(student);
        return visibleEnrollments(student);
    }

    public List<StudentEnrollmentDto> complete(Long studentId, Long classId) {
        Student student = requireStudentForUpdate(studentId);
        materializeLegacyEnrollment(student);
        StudentClassEnrollment source = requireActiveSource(studentId, classId);
        completeEnrollment(source, "MANUAL_COMPLETION");
        updateRepresentativeClass(student);
        return visibleEnrollments(student);
    }

    public List<StudentEnrollmentDto> transfer(Long studentId, Long classId, Long targetClassId) {
        if (classId.equals(targetClassId)) {
            throw new IllegalArgumentException("현재 반과 다른 반을 선택해주세요");
        }
        Student student = requireStudentForUpdate(studentId);
        AcademyClass target = requireTargetClass(student, targetClassId);
        materializeLegacyEnrollment(student);
        StudentClassEnrollment source = requireActiveSource(studentId, classId);
        requireNotActive(studentId, targetClassId);
        completeEnrollment(source, "TRANSFERRED");
        enrollmentService.ensureActiveEnrollment(student, target, TenantContext.current().teacherId());
        updateRepresentativeClass(student);
        return visibleEnrollments(student);
    }

    private Student requireStudentForUpdate(Long studentId) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.role() == null) {
            throw new ForbiddenException("선생님만 반 소속을 변경할 수 있습니다");
        }
        authorizationService.assertNotAssistant();
        // Serialize a student's add/complete/transfer requests, including first-time inserts.
        Student student = studentRepository.findLockedById(studentId)
                .orElseThrow(() -> new ForbiddenException("학생에 접근할 수 없습니다"));
        authorizationService.assertCanAccessStudent(student);
        return student;
    }

    private AcademyClass requireTargetClass(Student student, Long classId) {
        AcademyClass target = classRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("반을 찾을 수 없습니다"));
        authorizationService.assertCanModifyClass(target);
        if (!student.getAcademy().getId().equals(target.getAcademy().getId())) {
            throw new ForbiddenException("다른 학원의 반에 배정할 수 없습니다");
        }
        AcademyClassPolicy.assertActive(target);
        return target;
    }

    private StudentClassEnrollment requireActiveSource(Long studentId, Long classId) {
        StudentClassEnrollment source = enrollmentRepository.findByStudentIdAndAcademyClassId(studentId, classId)
                .orElseThrow(() -> new IllegalArgumentException("학생의 반 소속을 찾을 수 없습니다"));
        authorizationService.assertCanModifyClass(source.getAcademyClass());
        AcademyClassPolicy.assertActive(source.getAcademyClass());
        if (source.getStatus() != StudentClassEnrollmentStatus.ACTIVE) {
            throw new IllegalArgumentException("수강 중인 반만 종료하거나 이동할 수 있습니다");
        }
        return source;
    }

    private void requireNotActive(Long studentId, Long classId) {
        enrollmentRepository.findByStudentIdAndAcademyClassId(studentId, classId)
                .filter(enrollment -> enrollment.getStatus() == StudentClassEnrollmentStatus.ACTIVE)
                .ifPresent(enrollment -> {
                    throw new IllegalArgumentException("이미 수강 중인 반입니다");
                });
    }

    private void completeEnrollment(StudentClassEnrollment enrollment, String reason) {
        enrollment.setStatus(StudentClassEnrollmentStatus.COMPLETED);
        enrollment.setEndedAt(LocalDateTime.now());
        enrollment.setEndReason(reason);
    }

    private void materializeLegacyEnrollment(Student student) {
        if (enrollmentRepository.existsByStudentId(student.getId())) {
            return;
        }
        AcademyClass legacyClass = student.getAcademyClass();
        StudentClassEnrollment enrollment = enrollmentService.ensureActiveEnrollment(
                student, legacyClass, TenantContext.current().teacherId());
        if (legacyClass.isEnded()) {
            enrollment.setStatus(StudentClassEnrollmentStatus.COMPLETED);
            enrollment.setEndedAt(legacyClass.getEndedAt());
            enrollment.setEndReason("CLASS_ENDED");
        }
    }

    private void updateRepresentativeClass(Student student) {
        List<StudentClassEnrollment> enrollments = enrollmentRepository
                .findByStudentIdOrderByStartedAtDescIdDesc(student.getId());
        // Keep the compatibility column usable until all older consumers are retired.
        enrollments.stream()
                .filter(enrollment -> enrollment.getStatus() == StudentClassEnrollmentStatus.ACTIVE)
                .findFirst()
                .ifPresent(enrollment -> student.setAcademyClass(enrollment.getAcademyClass()));
    }

    private boolean canManage(AcademyClass academyClass, StudentClassEnrollmentStatus status) {
        TenantContext.Context ctx = TenantContext.current();
        return ctx != null && ctx.role() != null && ctx.role() != TeacherAcademyRole.ASSISTANT
                && status == StudentClassEnrollmentStatus.ACTIVE && !academyClass.isEnded();
    }
}
