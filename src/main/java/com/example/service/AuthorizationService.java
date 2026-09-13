package com.example.service;

import com.example.config.security.TenantContext;
import com.example.entity.AcademyClass;
import com.example.entity.Clinic;
import com.example.entity.Homework;
import com.example.entity.Lesson;
import com.example.entity.Student;
import com.example.entity.StudentClassEnrollment;
import com.example.entity.StudentClassEnrollmentStatus;
import com.example.entity.StudentSubmission;
import com.example.entity.TeacherAcademyRole;
import com.example.entity.Test;
import com.example.exception.ForbiddenException;
import com.example.repository.AcademyClassRepository;
import com.example.repository.ClassAssistantRepository;
import com.example.repository.StudentClassEnrollmentRepository;
import com.example.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Central authorization assertions used by services that load entities by
 * primary key (which bypasses Hibernate tenant filters) or otherwise need
 * to verify caller access before returning or mutating data.
 *
 * <p>Rules encoded here:
 * <ul>
 *   <li>Caller must have a {@link TenantContext} — no anonymous access.</li>
 *   <li>Entity's academy must match the caller's active academy.</li>
 *   <li>{@code ACADEMY_ADMIN}: full access within their own academy.</li>
 *   <li>{@code STUDENT} (role == null in context): academy match is sufficient;
 *       Hibernate filters constrain the rest.</li>
 *   <li>{@code TEACHER}: must own the class containing the entity, OR be
 *       registered as an assistant of that class.</li>
 *   <li>{@code ASSISTANT}: must be registered as an assistant of the class
 *       containing the entity. (Assistants own no classes by definition.)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final AcademyClassRepository academyClassRepository;
    private final ClassAssistantRepository classAssistantRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;

    /**
     * Generic check: caller can access an entity belonging to the given
     * academy and (optionally) class. Used internally by the per-entity
     * helpers below; may also be invoked directly when only IDs are at hand.
     *
     * @param entityAcademyId the academy the entity belongs to (required)
     * @param entityClassId   the class the entity belongs to; may be null for
     *                        entities that are not class-scoped, in which
     *                        case TEACHER role will be rejected.
     */
    public void assertCanAccess(Long entityAcademyId, Long entityClassId) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null) {
            throw new ForbiddenException("인증 컨텍스트가 없습니다");
        }
        if (entityAcademyId == null || !entityAcademyId.equals(ctx.academyId())) {
            throw new ForbiddenException("다른 학원의 리소스에 접근할 수 없습니다");
        }
        // Admin: full access within own academy
        if (ctx.role() == TeacherAcademyRole.ACADEMY_ADMIN) {
            return;
        }
        // Student: academy and the active class selected in the session must match.
        if (ctx.role() == null) {
            if (ctx.studentClassId() != null
                    && entityClassId != null
                    && !ctx.studentClassId().equals(entityClassId)) {
                throw new ForbiddenException("현재 선택한 반의 리소스만 접근할 수 있습니다");
            }
            return;
        }
        // Teacher / Assistant: must own the class OR be registered as its assistant
        if (entityClassId == null) {
            throw new ForbiddenException("이 리소스에 접근할 수 없습니다");
        }
        AcademyClass clazz = academyClassRepository.findById(entityClassId)
                .orElseThrow(() -> new ForbiddenException("이 리소스에 접근할 수 없습니다"));
        if (!canAccessClass(clazz)) {
            throw new ForbiddenException("본인이 담당하거나 보조하는 반의 리소스만 접근할 수 있습니다");
        }
    }

    /** Staff visibility for a class, including completed classes. */
    public boolean canAccessClass(AcademyClass clazz) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.role() == null || clazz == null || clazz.getAcademy() == null
                || !clazz.getAcademy().getId().equals(ctx.academyId())) {
            return false;
        }
        return ctx.role() == TeacherAcademyRole.ACADEMY_ADMIN
                || ctx.teacherId().equals(clazz.getOwnerTeacherId())
                || classAssistantRepository.existsByClassIdAndTeacherId(clazz.getId(), ctx.teacherId());
    }

    /**
     * Reject ASSISTANT role from operations that create new top-level entities
     * (a new class, a new student, etc.). Assistants only operate on existing
     * entities scoped to their assigned classes.
     */
    public void assertNotAssistant() {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null) {
            throw new ForbiddenException("인증 컨텍스트가 없습니다");
        }
        if (ctx.role() == TeacherAcademyRole.ASSISTANT) {
            throw new ForbiddenException("조교는 이 작업을 수행할 수 없습니다");
        }
    }

    /**
     * Require a student caller to act on their own student record.
     */
    public void assertCurrentStudent(Long studentId) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.role() != null || !ctx.teacherId().equals(studentId)) {
            throw new ForbiddenException("본인의 클리닉 신청만 변경할 수 있습니다");
        }
    }

    public void assertStudentClassWritable() {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.role() != null) {
            return;
        }
        if (ctx.studentClassReadOnly()) {
            throw new ForbiddenException("종강한 반은 조회만 할 수 있습니다");
        }
        if (ctx.studentClassId() == null) {
            return;
        }
        // Enrollment changes must take effect before an existing student session refreshes.
        boolean writable = enrollmentRepository
                .findByStudentIdAndAcademyClassId(ctx.teacherId(), ctx.studentClassId())
                .map(enrollment -> enrollment.getStatus() == StudentClassEnrollmentStatus.ACTIVE
                        && !enrollment.getAcademyClass().isEnded()
                        && enrollment.getAcademyClass().getAcademy().getId().equals(ctx.academyId()))
                .orElseGet(() -> !enrollmentRepository.existsByStudentId(ctx.teacherId())
                        && studentRepository.findById(ctx.teacherId())
                                .map(student -> student.getAcademyClass() != null
                                        && ctx.studentClassId().equals(student.getAcademyClass().getId())
                                        && !student.getAcademyClass().isEnded()
                                        && student.getAcademyClass().getAcademy().getId().equals(ctx.academyId()))
                                .orElse(false));
        if (!writable) {
            throw new ForbiddenException("현재 수강 중인 반만 변경할 수 있습니다");
        }
    }

    /**
     * Unified check for modifying an AcademyClass. Preserves the previous
     * behavior (academy match → admin pass → owner check) by delegating to
     * {@link #assertCanAccess(Long, Long)}.
     */
    public void assertCanModifyClass(AcademyClass clazz) {
        if (clazz == null) {
            throw new ForbiddenException("반을 찾을 수 없습니다");
        }
        Long academyId = clazz.getAcademy() != null ? clazz.getAcademy().getId() : null;
        assertCanAccess(academyId, clazz.getId());
    }

    public void assertIsAcademyAdmin() {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.role() != TeacherAcademyRole.ACADEMY_ADMIN) {
            throw new ForbiddenException("어드민 권한이 필요합니다");
        }
    }

    public void assertCanAccessStudent(Student student) {
        if (student == null) {
            throw new ForbiddenException("학생을 찾을 수 없습니다");
        }
        Long academyId = student.getAcademy() != null
                ? student.getAcademy().getId()
                : (student.getAcademyClass() != null && student.getAcademyClass().getAcademy() != null
                    ? student.getAcademyClass().getAcademy().getId()
                    : null);
        TenantContext.Context ctx = TenantContext.current();
        if (ctx != null && ctx.role() == null) {
            if (!student.getId().equals(ctx.teacherId())) {
                throw new ForbiddenException("본인의 학생 정보만 접근할 수 있습니다");
            }
            assertCanAccess(academyId, null);
            return;
        }
        if (ctx == null || academyId == null || !academyId.equals(ctx.academyId())) {
            throw new ForbiddenException("이 학생에 접근할 수 없습니다");
        }
        if (ctx.role() == TeacherAcademyRole.ACADEMY_ADMIN) {
            return;
        }
        List<StudentClassEnrollment> enrollments = enrollmentRepository
                .findByStudentIdOrderByStartedAtDescIdDesc(student.getId());
        boolean accessible = enrollments.isEmpty()
                ? canAccessClass(student.getAcademyClass())
                : enrollments.stream()
                        .filter(enrollment -> enrollment.getStatus() == StudentClassEnrollmentStatus.ACTIVE
                                || enrollment.getStatus() == StudentClassEnrollmentStatus.COMPLETED)
                        .anyMatch(enrollment -> canAccessClass(enrollment.getAcademyClass()));
        if (!accessible) {
            throw new ForbiddenException("본인이 담당하거나 보조하는 반의 학생만 접근할 수 있습니다");
        }
    }

    public void assertCanAccessLesson(Lesson lesson) {
        if (lesson == null) {
            throw new ForbiddenException("수업을 찾을 수 없습니다");
        }
        Long academyId = lesson.getAcademy() != null ? lesson.getAcademy().getId() : null;
        Long classId = lesson.getAcademyClass() != null ? lesson.getAcademyClass().getId() : null;
        assertCanAccess(academyId, classId);
    }

    public void assertCanAccessTest(Test test) {
        if (test == null) {
            throw new ForbiddenException("시험을 찾을 수 없습니다");
        }
        Long academyId = test.getAcademy() != null ? test.getAcademy().getId() : null;
        Long classId = test.getAcademyClass() != null ? test.getAcademyClass().getId() : null;
        assertCanAccess(academyId, classId);
    }

    public void assertCanAccessHomework(Homework homework) {
        if (homework == null) {
            throw new ForbiddenException("숙제를 찾을 수 없습니다");
        }
        Long academyId = homework.getAcademy() != null ? homework.getAcademy().getId() : null;
        Long classId = homework.getAcademyClass() != null ? homework.getAcademyClass().getId() : null;
        assertCanAccess(academyId, classId);
    }

    public void assertCanAccessClinic(Clinic clinic) {
        if (clinic == null) {
            throw new ForbiddenException("클리닉을 찾을 수 없습니다");
        }
        AcademyClass clazz = clinic.getAcademyClass();
        if (clazz == null) {
            throw new ForbiddenException("클리닉의 반 정보가 없습니다");
        }
        Long academyId = clazz.getAcademy() != null ? clazz.getAcademy().getId() : null;
        assertCanAccess(academyId, clazz.getId());
    }

    public void assertCanAccessSubmission(StudentSubmission submission) {
        if (submission == null) {
            throw new ForbiddenException("제출 기록을 찾을 수 없습니다");
        }
        if (submission.getStudent() == null) {
            throw new ForbiddenException("제출 기록의 학생 정보가 없습니다");
        }
        assertCanAccessStudent(submission.getStudent());
        if (submission.getTest() != null) {
            assertCanAccessTest(submission.getTest());
        }
    }
}
