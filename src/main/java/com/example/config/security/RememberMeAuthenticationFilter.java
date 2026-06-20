package com.example.config.security;

import com.example.entity.Student;
import com.example.entity.StudentStatus;
import com.example.entity.Teacher;
import com.example.repository.StudentRepository;
import com.example.repository.TeacherRepository;
import com.example.service.AuthSessionService;
import com.example.service.RememberMeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class RememberMeAuthenticationFilter extends OncePerRequestFilter {

    private final RememberMeService rememberMeService;
    private final AuthSessionService authSessionService;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession existing = request.getSession(false);
        if (existing == null || existing.getAttribute("userId") == null) {
            rememberMeService.consume(request, response)
                    .ifPresent(login -> restoreSession(request, response, login));
        }
        filterChain.doFilter(request, response);
    }

    private void restoreSession(HttpServletRequest request,
                                HttpServletResponse response,
                                RememberMeService.RememberedLogin login) {
        if ("STUDENT".equals(login.userRole())) {
            Optional<Student> student = studentRepository.findById(login.userId());
            if (student.isPresent()
                    && student.get().getStatus() != StudentStatus.PENDING_CONSENT
                    && student.get().getStatus() != StudentStatus.REVOKED) {
                authSessionService.bindStudent(authSessionService.startAuthenticatedSession(request), student.get());
                return;
            }
        } else if ("TEACHER".equals(login.userRole())) {
            Optional<Teacher> teacher = teacherRepository.findById(login.userId());
            if (teacher.isPresent()) {
                authSessionService.bindTeacher(authSessionService.startAuthenticatedSession(request), teacher.get());
                return;
            }
        }

        log.warn("[auth] remember-me rejected: role={}, userId={}", login.userRole(), login.userId());
        rememberMeService.clear(request, response);
    }
}
