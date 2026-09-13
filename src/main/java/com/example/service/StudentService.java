package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.StudentCreateRequest;
import com.example.dto.StudentCreateResponse;
import com.example.dto.StudentDto;
import com.example.dto.StudentEnrollmentDto;
import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Student;
import com.example.entity.StudentClassEnrollmentStatus;
import com.example.entity.StudentStatus;
import com.example.exception.ForbiddenException;
import com.example.repository.AcademyRepository;
import com.example.repository.AcademyClassRepository;
import com.example.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class StudentService {
    private final StudentRepository studentRepository;
    private final AcademyRepository academyRepository;
    private final AcademyClassRepository academyClassRepository;
    private final AuthorizationService authorizationService;
    private final PinCredentialService pinCredentialService;
    private final StudentConsentIssuer studentConsentIssuer;
    private final StudentClassEnrollmentService studentClassEnrollmentService;
    private final StudentEnrollmentManagementService enrollmentManagementService;

    public Page<StudentDto> getStudents(String name, Pageable pageable) {
        Page<Student> students;
        if (name != null && !name.isEmpty()) {
            students = studentRepository.findByNameContaining(name, pageable);
        } else {
            students = studentRepository.findAll(pageable);
        }
        return students.map(this::toDto);
    }

    public StudentDto getStudent(Long id, Long activeClassId) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);
        StudentDto dto = toDto(student);
        if (activeClassId != null) {
            AcademyClass activeClass = academyClassRepository.findById(activeClassId)
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            dto.setClassId(activeClass.getId());
            dto.setClassName(activeClass.getName());
        }
        return dto;
    }

    public StudentCreateResponse createStudent(StudentCreateRequest dto) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null) {
            throw new ForbiddenException("인증 컨텍스트가 없습니다");
        }
        authorizationService.assertNotAssistant();

        // Force active academy from session — ignore dto.academyId if it differs
        if (dto.getAcademyId() != null && !dto.getAcademyId().equals(ctx.academyId())) {
            throw new ForbiddenException("활성 학원과 다른 학원에 학생을 생성할 수 없습니다");
        }

        Academy academy = academyRepository.findById(ctx.academyId())
                .orElseThrow(() -> new RuntimeException("Academy not found"));
        AcademyClass academyClass = academyClassRepository.findById(dto.getClassId())
                .orElseThrow(() -> new RuntimeException("Class not found"));

        // Ensure target class belongs to the active academy and caller may access it
        authorizationService.assertCanModifyClass(academyClass);
        AcademyClassPolicy.assertActive(academyClass);

        String pin = StudentBulkService.last4Digits(dto.getParentPhone());
        if (pin == null) {
            throw new IllegalArgumentException("보호자 휴대폰에서 숫자 4자리를 추출할 수 없습니다");
        }

        Student student = Student.builder()
                .name(dto.getName().trim())
                .grade(dto.getGrade().trim())
                .school(dto.getSchool().trim())
                .parentName(dto.getParentName().trim())
                .parentPhone(normalizePhone(dto.getParentPhone()))
                .contactPhone(emptyToNull(normalizePhone(dto.getContactPhone())))
                .status(StudentStatus.PENDING_CONSENT)
                .academy(academy)
                .academyClass(academyClass)
                .build();
        pinCredentialService.setStudentPin(student, pin);

        student = studentRepository.save(student);
        studentClassEnrollmentService.ensureActiveEnrollment(
                student, academyClass, currentTeacherId());
        String consentToken = studentConsentIssuer.issue(student, LocalDateTime.now());
        return StudentCreateResponse.builder()
                .student(toDto(student))
                .consentToken(consentToken)
                .build();
    }

    public StudentDto updateStudent(Long id, StudentDto dto) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);

        if (dto.getAcademyId() != null && !dto.getAcademyId().equals(student.getAcademy().getId())) {
            throw new ForbiddenException("학생 계정의 소속 학원은 변경할 수 없습니다");
        }

        if (dto.getClassId() != null && !dto.getClassId().equals(student.getAcademyClass().getId())) {
            throw new IllegalArgumentException("반 변경은 반 관리의 추가·수강 종료·이동 기능을 사용해주세요");
        }

        student.setName(dto.getName());
        student.setGrade(dto.getGrade());
        student.setSchool(dto.getSchool());

        student = studentRepository.save(student);
        return toDto(student);
    }

    public void deleteStudent(Long id) {
        // A personal student record now owns history across multiple teachers' classes.
        authorizationService.assertIsAcademyAdmin();
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);
        studentRepository.delete(student);
    }

    public StudentDto resetPin(Long id, String newPin) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);
        pinCredentialService.setStudentPin(student, newPin);
        student = studentRepository.save(student);
        return toDto(student);
    }

    public StudentDto setScoreVisibility(Long id, boolean hide) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);
        student.setHideScoresFromStudent(hide);
        student = studentRepository.save(student);
        return toDto(student);
    }

    private StudentDto toDto(Student student) {
        StudentDto dto = StudentDto.from(student);
        TenantContext.Context ctx = TenantContext.current();
        if (ctx != null && ctx.role() != null) {
            var enrollments = enrollmentManagementService.visibleEnrollments(student);
            dto.setEnrollments(enrollments);
            // Do not expose another teacher's representative class through the shared profile.
            StudentEnrollmentDto displayClass = enrollments.stream()
                    .filter(enrollment -> enrollment.getStatus() == StudentClassEnrollmentStatus.ACTIVE)
                    .findFirst()
                    .orElse(enrollments.isEmpty() ? null : enrollments.get(0));
            dto.setClassId(displayClass == null ? null : displayClass.getClassId());
            dto.setClassName(displayClass == null ? null : displayClass.getClassName());
        }
        return dto;
    }

    private static String normalizePhone(String raw) {
        if (raw == null) return null;
        return raw.trim().replaceAll("[^0-9-]", "");
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private Long currentTeacherId() {
        TenantContext.Context ctx = TenantContext.current();
        return ctx != null && ctx.role() != null ? ctx.teacherId() : null;
    }
}
