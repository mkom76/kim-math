package com.example.service;

import com.example.entity.Student;
import com.example.entity.Teacher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class AuthSessionService {

    public HttpSession startAuthenticatedSession(HttpServletRequest request) {
        HttpSession session = request.getSession();
        request.changeSessionId();
        return session;
    }

    public void bindStudent(HttpSession session, Student student) {
        session.removeAttribute("activeAcademyId");
        session.removeAttribute("activeRole");
        session.removeAttribute("studentAcademyId");
        session.setAttribute("userId", student.getId());
        session.setAttribute("userRole", "STUDENT");
        session.setAttribute("userName", student.getName());

        Long academyId = studentAcademyId(student);
        if (academyId != null) {
            session.setAttribute("studentAcademyId", academyId);
        }
    }

    public void bindTeacher(HttpSession session, Teacher teacher) {
        session.removeAttribute("activeAcademyId");
        session.removeAttribute("activeRole");
        session.removeAttribute("studentAcademyId");
        session.setAttribute("userId", teacher.getId());
        session.setAttribute("userRole", "TEACHER");
        session.setAttribute("userName", teacher.getName());
    }

    private Long studentAcademyId(Student student) {
        if (student.getAcademy() != null) {
            return student.getAcademy().getId();
        }
        if (student.getAcademyClass() != null && student.getAcademyClass().getAcademy() != null) {
            return student.getAcademyClass().getAcademy().getId();
        }
        return null;
    }
}
