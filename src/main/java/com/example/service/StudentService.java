package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.StudentDto;
import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Student;
import com.example.exception.ForbiddenException;
import com.example.repository.AcademyRepository;
import com.example.repository.AcademyClassRepository;
import com.example.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class StudentService {
    private final StudentRepository studentRepository;
    private final AcademyRepository academyRepository;
    private final AcademyClassRepository academyClassRepository;
    private final AuthorizationService authorizationService;

    public Page<StudentDto> getStudents(String name, Pageable pageable) {
        Page<Student> students;
        if (name != null && !name.isEmpty()) {
            students = studentRepository.findByNameContaining(name, pageable);
        } else {
            students = studentRepository.findAll(pageable);
        }
        return students.map(StudentDto::from);
    }

    public StudentDto getStudent(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);
        return StudentDto.from(student);
    }

    public StudentDto createStudent(StudentDto dto) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null) {
            throw new ForbiddenException("인증 컨텍스트가 없습니다");
        }

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

        Student student = Student.builder()
                .name(dto.getName())
                .grade(dto.getGrade())
                .school(dto.getSchool())
                .pin(dto.getPin())
                .academy(academy)
                .academyClass(academyClass)
                .build();

        student = studentRepository.save(student);
        return StudentDto.from(student);
    }

    public StudentDto updateStudent(Long id, StudentDto dto) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);

        student.setName(dto.getName());
        student.setGrade(dto.getGrade());
        student.setSchool(dto.getSchool());

        if (dto.getAcademyId() != null && !dto.getAcademyId().equals(student.getAcademy().getId())) {
            Academy academy = academyRepository.findById(dto.getAcademyId())
                    .orElseThrow(() -> new RuntimeException("Academy not found"));
            student.setAcademy(academy);
        }

        if (dto.getClassId() != null && !dto.getClassId().equals(student.getAcademyClass().getId())) {
            AcademyClass academyClass = academyClassRepository.findById(dto.getClassId())
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            authorizationService.assertCanModifyClass(academyClass);
            student.setAcademyClass(academyClass);
        }

        student = studentRepository.save(student);
        return StudentDto.from(student);
    }

    public void deleteStudent(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);
        studentRepository.delete(student);
    }

    public StudentDto resetPin(Long id, String newPin) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);
        student.setPin(newPin);
        student = studentRepository.save(student);
        return StudentDto.from(student);
    }

    public StudentDto setScoreVisibility(Long id, boolean hide) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);
        student.setHideScoresFromStudent(hide);
        student = studentRepository.save(student);
        return StudentDto.from(student);
    }
}