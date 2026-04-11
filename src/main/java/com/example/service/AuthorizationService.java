package com.example.service;

import com.example.config.security.TenantContext;
import com.example.entity.AcademyClass;
import com.example.entity.Clinic;
import com.example.entity.Homework;
import com.example.entity.Lesson;
import com.example.entity.Student;
import com.example.entity.StudentSubmission;
import com.example.entity.TeacherAcademyRole;
import com.example.entity.Test;
import com.example.exception.ForbiddenException;
import com.example.repository.AcademyClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
 *   <li>{@code TEACHER}: must own the class containing the entity.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final AcademyClassRepository academyClassRepository;

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
        // Student: academy match is enough (filters cover ownership)
        if (ctx.role() == null) {
            return;
        }
        // Teacher: must own the class containing this entity
        if (entityClassId == null) {
            throw new ForbiddenException("이 리소스에 접근할 수 없습니다");
        }
        AcademyClass clazz = academyClassRepository.findById(entityClassId)
                .orElseThrow(() -> new ForbiddenException("이 리소스에 접근할 수 없습니다"));
        if (clazz.getOwnerTeacherId() == null || !ctx.teacherId().equals(clazz.getOwnerTeacherId())) {
            throw new ForbiddenException("본인이 담당하는 반의 리소스만 접근할 수 있습니다");
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
        Long classId = student.getAcademyClass() != null ? student.getAcademyClass().getId() : null;
        assertCanAccess(academyId, classId);
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
    }
}
