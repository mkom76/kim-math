package com.example.service;

import com.example.dto.StudentClassMembershipDto;
import com.example.exception.ForbiddenException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentClassContextService {
    public static final String ACTIVE_STUDENT_CLASS_ID = "activeStudentClassId";
    public static final String ACTIVE_STUDENT_CLASS_READ_ONLY = "activeStudentClassReadOnly";

    private final StudentClassEnrollmentService enrollmentService;

    public StudentClassMembershipDto initialize(HttpSession session, Long studentId) {
        StudentClassMembershipDto membership = enrollmentService.getDefaultMembership(studentId);
        bind(session, membership);
        return membership;
    }

    public StudentClassMembershipDto current(HttpSession session, Long studentId) {
        Long selectedClassId = (Long) session.getAttribute(ACTIVE_STUDENT_CLASS_ID);
        if (selectedClassId != null) {
            try {
                StudentClassMembershipDto membership =
                        enrollmentService.requireSelectableMembership(studentId, selectedClassId);
                bind(session, membership);
                return membership;
            } catch (IllegalArgumentException ignored) {
                // The class may have ended or the enrollment may have changed since login.
            }
        }
        return initialize(session, studentId);
    }

    public StudentClassMembershipDto switchClass(HttpSession session, Long studentId, Long classId) {
        StudentClassMembershipDto membership =
                enrollmentService.requireSelectableMembership(studentId, classId);
        bind(session, membership);
        return membership;
    }

    public Long activeClassIdForStudentRequest(HttpSession session, Long studentId) {
        String role = (String) session.getAttribute("userRole");
        if (!"STUDENT".equals(role)) {
            return null;
        }
        Long userId = (Long) session.getAttribute("userId");
        if (!studentId.equals(userId)) {
            throw new ForbiddenException("본인의 반 데이터만 조회할 수 있습니다");
        }
        return current(session, studentId).getClassId();
    }

    private void bind(HttpSession session, StudentClassMembershipDto membership) {
        session.setAttribute(ACTIVE_STUDENT_CLASS_ID, membership.getClassId());
        session.setAttribute(ACTIVE_STUDENT_CLASS_READ_ONLY, membership.isReadOnly());
    }
}
