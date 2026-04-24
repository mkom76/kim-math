package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.StudentHomeworkDto;
import com.example.entity.Homework;
import com.example.entity.Student;
import com.example.entity.StudentHomework;
import com.example.exception.ForbiddenException;
import com.example.repository.HomeworkRepository;
import com.example.repository.StudentHomeworkRepository;
import com.example.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class StudentHomeworkService {
    private final StudentHomeworkRepository studentHomeworkRepository;
    private final StudentRepository studentRepository;
    private final HomeworkRepository homeworkRepository;
    private final AuthorizationService authorizationService;

    public List<StudentHomeworkDto> getByStudentId(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);

        return studentHomeworkRepository.findByStudentId(studentId).stream()
                .map(StudentHomeworkDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StudentHomeworkDto> getFollowUpsByStudent(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);

        return studentHomeworkRepository.findByStudentIdAndFollowUpFlagTrue(studentId).stream()
                .map(StudentHomeworkDto::from)
                .collect(Collectors.toList());
    }

    public StudentHomeworkDto updateIncorrectCount(Long studentId, Long homeworkId, Integer incorrectCount, Integer unsolvedCount, String incorrectQuestions, String unsolvedQuestions) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("Homework not found"));
        authorizationService.assertCanAccessHomework(homework);

        StudentHomework studentHomework = studentHomeworkRepository
                .findByStudentIdAndHomeworkId(studentId, homeworkId)
                .orElse(StudentHomework.builder()
                        .student(student)
                        .homework(homework)
                        .build());

        studentHomework.setIncorrectCount(incorrectCount);
        studentHomework.setUnsolvedCount(unsolvedCount);
        studentHomework.setIncorrectQuestions(incorrectQuestions);
        studentHomework.setUnsolvedQuestions(unsolvedQuestions);
        studentHomework = studentHomeworkRepository.save(studentHomework);

        return StudentHomeworkDto.from(studentHomework);
    }

    public StudentHomeworkDto updateQuestionedQuestions(Long studentId, Long homeworkId, String questionedQuestions) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.role() != null || !studentId.equals(ctx.teacherId())) {
            throw new ForbiddenException("본인의 숙제만 수정할 수 있습니다");
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("Homework not found"));

        String normalized = QuestionNumberNormalizer.normalize(questionedQuestions, homework.getQuestionCount());

        StudentHomework studentHomework = studentHomeworkRepository
                .findByStudentIdAndHomeworkId(studentId, homeworkId)
                .orElse(StudentHomework.builder()
                        .student(student)
                        .homework(homework)
                        .build());

        studentHomework.setQuestionedQuestions(normalized);
        studentHomework = studentHomeworkRepository.save(studentHomework);

        return StudentHomeworkDto.from(studentHomework);
    }

    public StudentHomeworkDto setFollowUp(Long studentId, Long homeworkId, boolean followUp) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("Homework not found"));
        authorizationService.assertCanAccessHomework(homework);

        StudentHomework studentHomework = studentHomeworkRepository
                .findByStudentIdAndHomeworkId(studentId, homeworkId)
                .orElseThrow(() -> new RuntimeException("StudentHomework not found"));

        studentHomework.setFollowUpFlag(followUp);
        studentHomework = studentHomeworkRepository.save(studentHomework);

        return StudentHomeworkDto.from(studentHomework);
    }

}
