package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.StudentHomeworkDto;
import com.example.entity.Homework;
import com.example.entity.Student;
import com.example.entity.StudentHomework;
import com.example.entity.StudentClassEnrollmentStatus;
import com.example.exception.ForbiddenException;
import com.example.repository.HomeworkRepository;
import com.example.repository.StudentHomeworkRepository;
import com.example.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

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
    private final StudentClassEnrollmentService enrollmentService;

    public List<StudentHomeworkDto> getByStudentId(Long studentId, Long activeClassId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);

        List<StudentHomework> homeworks = activeClassId == null
                ? studentHomeworkRepository.findByStudentId(studentId)
                : studentHomeworkRepository.findByStudentIdAndHomeworkAcademyClassId(
                        studentId, activeClassId);
        return homeworks.stream()
                .map(StudentHomeworkDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StudentHomeworkDto> getFollowUpsByStudent(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);

        TenantContext.Context ctx = TenantContext.current();
        return studentHomeworkRepository.findByStudentIdAndFollowUpFlagTrue(studentId).stream()
                .filter(progress -> ctx == null || ctx.role() != null || ctx.studentClassId() == null
                        || progress.getClassHistory().stream().anyMatch(clazz -> clazz.getId().equals(ctx.studentClassId())))
                .map(StudentHomeworkDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StudentHomeworkDto updateIncorrectCount(Long studentId, Long homeworkId, Integer incorrectCount, Integer unsolvedCount, String incorrectQuestions, String unsolvedQuestions) {
        return updateIncorrectCount(studentId, homeworkId, incorrectCount, unsolvedCount, incorrectQuestions, unsolvedQuestions, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StudentHomeworkDto updateIncorrectCount(Long studentId, Long homeworkId, Integer incorrectCount, Integer unsolvedCount, String incorrectQuestions, String unsolvedQuestions, Long studentHomeworkId) {
        authorizationService.assertStudentClassWritable();
        Student student = studentRepository.findLockedById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("Homework not found"));

        StudentHomework studentHomework = writableProgress(student, homework, true, studentHomeworkId);

        studentHomework.setIncorrectCount(incorrectCount);
        studentHomework.setUnsolvedCount(unsolvedCount);
        studentHomework.setIncorrectQuestions(incorrectQuestions);
        studentHomework.setUnsolvedQuestions(unsolvedQuestions);
        studentHomework = studentHomeworkRepository.save(studentHomework);

        return StudentHomeworkDto.from(studentHomework);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StudentHomeworkDto updateQuestionedQuestions(Long studentId, Long homeworkId, String questionedQuestions) {
        return updateQuestionedQuestions(studentId, homeworkId, questionedQuestions, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StudentHomeworkDto updateQuestionedQuestions(Long studentId, Long homeworkId, String questionedQuestions, Long studentHomeworkId) {
        authorizationService.assertStudentClassWritable();
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null || ctx.role() != null || !studentId.equals(ctx.teacherId())) {
            throw new ForbiddenException("본인의 숙제만 수정할 수 있습니다");
        }

        Student student = studentRepository.findLockedById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("Homework not found"));

        StudentHomework studentHomework = writableProgress(student, homework, true, studentHomeworkId);
        String normalized = QuestionNumberNormalizer.normalize(questionedQuestions, homework.getQuestionCount());

        studentHomework.setQuestionedQuestions(normalized);
        studentHomework = studentHomeworkRepository.save(studentHomework);

        return StudentHomeworkDto.from(studentHomework);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StudentHomeworkDto setFollowUp(Long studentId, Long homeworkId, boolean followUp) {
        return setFollowUp(studentId, homeworkId, followUp, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StudentHomeworkDto setFollowUp(Long studentId, Long homeworkId, boolean followUp, Long studentHomeworkId) {
        Student student = studentRepository.findLockedById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("Homework not found"));

        StudentHomework studentHomework = writableProgress(student, homework, false, studentHomeworkId);

        studentHomework.setFollowUpFlag(followUp);
        studentHomework = studentHomeworkRepository.save(studentHomework);

        return StudentHomeworkDto.from(studentHomework);
    }

    private StudentHomework writableProgress(Student student, Homework homework, boolean allowCreate, Long studentHomeworkId) {
        if (studentHomeworkId == null) {
            List<Long> ids = studentHomeworkRepository.findRecordIds(student.getId(), homework.getId());
            if (ids.size() > 1) throw new IllegalArgumentException("여러 수업에 배정된 숙제입니다. 수정할 수업의 숙제 기록을 지정해 주세요");
            studentHomeworkId = ids.isEmpty() ? null : ids.get(0);
        }
        StudentHomework progress = studentHomeworkId == null ? null : studentHomeworkRepository.findById(studentHomeworkId)
                .orElseThrow(() -> new IllegalArgumentException("학생의 숙제 기록을 찾을 수 없습니다"));
        if (progress != null && (!progress.getStudent().getId().equals(student.getId())
                || !progress.getHomework().getId().equals(homework.getId()))) {
            throw new IllegalArgumentException("학생과 숙제에 해당하는 기록이 아닙니다");
        }
        if (progress != null) {
            authorizationService.assertCanAccessStudentHomework(progress);
            return progress;
        }
        if (!allowCreate) throw new IllegalArgumentException("학생의 숙제 기록을 찾을 수 없습니다");
        // Legacy same-class entry remains supported. Shared records are created by lesson assignment.
        authorizationService.assertCanAccess(homework.getAcademy().getId(), homework.getAcademyClass().getId());
        boolean member = enrollmentService.getMemberships(student.getId()).stream().anyMatch(membership ->
                membership.getClassId().equals(homework.getAcademyClass().getId())
                        && membership.getStatus() == StudentClassEnrollmentStatus.ACTIVE);
        if (!member) throw new ForbiddenException("배정받은 숙제의 기록만 수정할 수 있습니다");
        return StudentHomework.builder().student(student).homework(homework)
                .classHistory(new java.util.ArrayList<>(List.of(homework.getAcademyClass()))).build();
    }

}
