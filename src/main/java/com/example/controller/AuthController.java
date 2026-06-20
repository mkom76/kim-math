package com.example.controller;

import com.example.dto.AuthResponse;
import com.example.dto.LoginDto;
import com.example.dto.MembershipDto;
import com.example.dto.SwitchAcademyRequest;
import com.example.entity.Student;
import com.example.entity.StudentStatus;
import com.example.entity.Teacher;
import com.example.entity.TeacherAcademy;
import com.example.repository.StudentRepository;
import com.example.repository.TeacherRepository;
import com.example.service.LoginSecurityService;
import com.example.service.MembershipService;
import com.example.service.PinCredentialService;
import com.example.service.AuthSessionService;
import com.example.service.RememberMeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final MembershipService membershipService;
    private final PinCredentialService pinCredentialService;
    private final LoginSecurityService loginSecurityService;
    private final AuthSessionService authSessionService;
    private final RememberMeService rememberMeService;

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        if (token == null) {
            return ResponseEntity.ok(Map.of());
        }
        return ResponseEntity.ok(Map.of(
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName(),
                "token", token.getToken()
        ));
    }

    @PostMapping("/student/login")
    @Transactional
    public ResponseEntity<AuthResponse> studentLogin(@RequestBody LoginDto loginDto,
                                                     HttpServletRequest request,
                                                     HttpServletResponse response) {
        Optional<Student> studentOpt = loginDto.getStudentId() == null
                ? Optional.empty()
                : studentRepository.findById(loginDto.getStudentId());

        if (studentOpt.isEmpty()) {
            log.warn("[auth] student login failed: unknown id={}", loginDto.getStudentId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.builder()
                            .message("학생 ID 또는 PIN이 올바르지 않습니다.")
                            .build());
        }

        Student student = studentOpt.get();
        LocalDateTime now = LocalDateTime.now();

        if (loginSecurityService.isLocked(student, now)) {
            log.warn("[auth] student login locked: studentId={}", student.getId());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(AuthResponse.builder()
                            .message(loginSecurityService.lockMessage(student.getLockedUntil(), now))
                            .build());
        }

        if (!pinCredentialService.verifyStudentPin(student, loginDto.getPin())) {
            loginSecurityService.recordFailure(student, now);
            log.warn("[auth] student login failed: studentId={}, failures={}",
                    student.getId(), student.getFailedLoginCount());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.builder()
                            .message("학생 ID 또는 PIN이 올바르지 않습니다.")
                            .build());
        }

        if (student.getStatus() == StudentStatus.PENDING_CONSENT) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(AuthResponse.builder()
                            .message("개인정보 동의가 완료되어야 이용할 수 있습니다. 학원에서 받은 동의 링크를 확인해 주세요.")
                            .build());
        }
        if (student.getStatus() == StudentStatus.REVOKED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(AuthResponse.builder()
                            .message("계정이 비활성화되었습니다. 학원에 문의해 주세요.")
                            .build());
        }

        loginSecurityService.recordSuccess(student, now);

        authSessionService.bindStudent(authSessionService.startAuthenticatedSession(request), student);
        refreshRememberMe(request, response, "STUDENT", student.getId(), loginDto.getRememberMe());

        return ResponseEntity.ok(AuthResponse.builder()
                .userId(student.getId())
                .name(student.getName())
                .role("STUDENT")
                .message("로그인 성공")
                .build());
    }

    @PostMapping("/teacher/login")
    @Transactional
    public ResponseEntity<AuthResponse> teacherLogin(@RequestBody LoginDto loginDto,
                                                     HttpServletRequest request,
                                                     HttpServletResponse response) {
        Optional<Teacher> teacherOpt = loginDto.getUsername() == null
                ? Optional.empty()
                : teacherRepository.findByUsername(loginDto.getUsername());

        if (teacherOpt.isEmpty()) {
            log.warn("[auth] teacher login failed: unknown username={}", loginDto.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.builder()
                            .message("아이디 또는 PIN이 올바르지 않습니다.")
                            .build());
        }

        Teacher teacher = teacherOpt.get();
        LocalDateTime now = LocalDateTime.now();

        if (loginSecurityService.isLocked(teacher, now)) {
            log.warn("[auth] teacher login locked: teacherId={}, username={}",
                    teacher.getId(), teacher.getUsername());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(AuthResponse.builder()
                            .message(loginSecurityService.lockMessage(teacher.getLockedUntil(), now))
                            .build());
        }

        if (!pinCredentialService.verifyTeacherPin(teacher, loginDto.getPin())) {
            loginSecurityService.recordFailure(teacher, now);
            log.warn("[auth] teacher login failed: teacherId={}, username={}, failures={}",
                    teacher.getId(), teacher.getUsername(), teacher.getFailedLoginCount());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.builder()
                            .message("아이디 또는 PIN이 올바르지 않습니다.")
                            .build());
        }

        List<MembershipDto> memberships = membershipService.getMembershipsForTeacher(teacher.getId());
        if (memberships.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthResponse.builder()
                            .message("소속된 학원이 없습니다. 관리자에게 문의하세요.")
                            .build());
        }

        loginSecurityService.recordSuccess(teacher, now);

        authSessionService.bindTeacher(authSessionService.startAuthenticatedSession(request), teacher);
        refreshRememberMe(request, response, "TEACHER", teacher.getId(), loginDto.getRememberMe());

        return ResponseEntity.ok(AuthResponse.builder()
                .userId(teacher.getId())
                .name(teacher.getName())
                .role("TEACHER")
                .memberships(memberships)
                .message("로그인 성공")
                .build());
    }

    @PostMapping("/switch-academy")
    public ResponseEntity<AuthResponse> switchAcademy(
            @Valid @RequestBody SwitchAcademyRequest req,
            HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        String userRole = (String) session.getAttribute("userRole");

        if (userId == null || !"TEACHER".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        TeacherAcademy membership;
        try {
            membership = membershipService.requireMembership(userId, req.getAcademyId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(AuthResponse.builder().message("해당 학원에 소속되어 있지 않습니다.").build());
        }

        session.setAttribute("activeAcademyId", membership.getAcademyId());
        session.setAttribute("activeRole", membership.getRole().name());

        return ResponseEntity.ok(AuthResponse.builder()
                .userId(userId)
                .name((String) session.getAttribute("userName"))
                .role("TEACHER")
                .activeAcademyId(membership.getAcademyId())
                .activeRole(membership.getRole().name())
                .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       HttpServletResponse response) {
        rememberMeService.clear(request, response);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        String userRole = (String) session.getAttribute("userRole");
        String userName = (String) session.getAttribute("userName");

        if (userId == null || userRole == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AuthResponse.AuthResponseBuilder builder = AuthResponse.builder()
                .userId(userId)
                .name(userName)
                .role(userRole);

        if ("TEACHER".equals(userRole)) {
            builder.memberships(membershipService.getMembershipsForTeacher(userId));
            Long activeAcademyId = (Long) session.getAttribute("activeAcademyId");
            String activeRole = (String) session.getAttribute("activeRole");
            if (activeAcademyId != null) {
                builder.activeAcademyId(activeAcademyId);
                builder.activeRole(activeRole);
            }
        }

        return ResponseEntity.ok(builder.build());
    }

    @PutMapping("/change-pin")
    public ResponseEntity<AuthResponse> changePin(
            @RequestBody Map<String, String> request,
            HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        String userRole = (String) session.getAttribute("userRole");

        if (userId == null || userRole == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String currentPin = request.get("currentPin");
        String newPin = request.get("newPin");

        if (currentPin == null || newPin == null) {
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder()
                            .message("현재 PIN과 새 PIN을 입력해주세요")
                            .build());
        }

        try {
            if ("STUDENT".equals(userRole)) {
                Optional<Student> studentOpt = studentRepository.findById(userId);
                if (studentOpt.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(AuthResponse.builder()
                                    .message("사용자를 찾을 수 없습니다")
                                    .build());
                }

                Student student = studentOpt.get();
                if (!pinCredentialService.verifyStudentPin(student, currentPin)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(AuthResponse.builder()
                                    .message("현재 PIN이 올바르지 않습니다")
                                    .build());
                }

                pinCredentialService.setStudentPin(student, newPin);
                studentRepository.save(student);
                log.info("[auth] student pin changed: studentId={}", student.getId());
            } else if ("TEACHER".equals(userRole)) {
                Optional<Teacher> teacherOpt = teacherRepository.findById(userId);
                if (teacherOpt.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(AuthResponse.builder()
                                    .message("사용자를 찾을 수 없습니다")
                                    .build());
                }

                Teacher teacher = teacherOpt.get();
                if (!pinCredentialService.verifyTeacherPin(teacher, currentPin)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(AuthResponse.builder()
                                    .message("현재 PIN이 올바르지 않습니다")
                                    .build());
                }

                pinCredentialService.setTeacherPin(teacher, newPin);
                teacherRepository.save(teacher);
                log.info("[auth] teacher pin changed: teacherId={}", teacher.getId());
            }

            return ResponseEntity.ok(AuthResponse.builder()
                    .message("PIN이 성공적으로 변경되었습니다")
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder()
                            .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(AuthResponse.builder()
                            .message("PIN 변경에 실패했습니다")
                    .build());
        }
    }

    private void refreshRememberMe(HttpServletRequest request,
                                   HttpServletResponse response,
                                   String userRole,
                                   Long userId,
                                   Boolean rememberMe) {
        if (Boolean.TRUE.equals(rememberMe)) {
            rememberMeService.issue(response, userRole, userId);
        } else {
            rememberMeService.clear(request, response);
        }
    }
}
