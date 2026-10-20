package com.example.service;

import com.example.entity.*;
import com.example.repository.StudentHomeworkRepository;
import com.example.repository.StudentLessonRepository;
import com.example.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LessonHomeworkAssignmentService {
    private final StudentHomeworkRepository studentHomeworkRepository;
    private final StudentLessonRepository studentLessonRepository;
    private final StudentClassEnrollmentService enrollmentService;
    private final StudentRepository studentRepository;

    @Transactional(readOnly = true)
    public Map<Long, StudentHomework> getProgressByStudent(Lesson lesson) {
        Map<Long, Homework> assigned = new HashMap<>();
        Set<Long> explicitStudents = new HashSet<>();
        for (StudentLesson record : studentLessonRepository.findByLessonId(lesson.getId())) {
            if (record.isHomeworkAssignmentSet()) {
                explicitStudents.add(record.getStudent().getId());
                if (record.getAssignedHomework() != null) {
                    assigned.put(record.getStudent().getId(), record.getAssignedHomework());
                }
            }
        }
        List<Long> homeworkIds = lesson.getHomeworks().stream().map(Homework::getId).toList();
        Map<Long, StudentHomework> result = new HashMap<>();
        for (StudentHomework progress : studentHomeworkRepository.findByLessonIdAndHomeworkIdIn(lesson.getId(), homeworkIds)) {
            Long studentId = progress.getStudent().getId();
            Homework homework = progress.getHomework();
            boolean matches = assigned.containsKey(studentId)
                    ? assigned.get(studentId).getId().equals(homework.getId())
                    : !explicitStudents.contains(studentId) && homework.getLesson() != null
                        && homework.getLesson().getId().equals(lesson.getId());
            if (matches) result.putIfAbsent(studentId, progress);
        }
        return result;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void assign(Lesson lesson, Map<Long, Long> assignments) {
        Map<Long, Homework> homeworks = lesson.getHomeworks().stream()
                .collect(Collectors.toMap(Homework::getId, homework -> homework));
        Map<Long, Student> students = enrollmentService.getActiveStudentsForClass(lesson.getAcademyClass().getId())
                .stream().collect(Collectors.toMap(Student::getId, student -> student));
        if (!students.keySet().containsAll(assignments.keySet())) {
            throw new IllegalArgumentException("수업에 소속된 학생만 숙제를 배정할 수 있습니다");
        }
        if (!assignments.isEmpty()) studentRepository.findAllLockedById(assignments.keySet().stream().sorted().toList());
        Map<Long, StudentHomework> previous = getProgressByStudent(lesson);
        for (Map.Entry<Long, Long> entry : assignments.entrySet()) {
            Student student = students.get(entry.getKey());
            if (student == null) throw new IllegalArgumentException("수업에 소속된 학생만 숙제를 배정할 수 있습니다");
            Homework homework = entry.getValue() == null ? null : homeworks.get(entry.getValue());
            if (entry.getValue() != null && homework == null) {
                throw new IllegalArgumentException("수업에 연결된 숙제만 배정할 수 있습니다");
            }
            StudentHomework old = previous.get(student.getId());
            if (old != null && old.getIncorrectCount() != null
                    && (homework == null || !old.getHomework().getId().equals(homework.getId()))) {
                throw new IllegalArgumentException("학생 " + student.getName() + "의 제출된 숙제는 변경하거나 제외할 수 없습니다");
            }
            StudentLesson record = studentLessonRepository.findByStudentIdAndLessonId(student.getId(), lesson.getId())
                    .orElseGet(() -> StudentLesson.builder().student(student).lesson(lesson).build());
            record.setAssignedHomework(homework);
            record.setHomeworkAssignmentSet(true);
            studentLessonRepository.save(record);
            if (homework != null) {
                StudentHomework progress = studentHomeworkRepository.findByStudentIdAndHomeworkIdAndLessonId(
                        student.getId(), homework.getId(), lesson.getId()).orElse(null);
                if (progress == null) {
                    progress = studentHomeworkRepository.save(StudentHomework.builder().student(student).homework(homework).lesson(lesson)
                            .classHistory(new ArrayList<>(List.of(lesson.getAcademyClass()))).build());
                    homework.getStudentHomeworks().add(progress);
                } else {
                    progress.recordClass(lesson.getAcademyClass());
                }
            }
            if (old != null && (homework == null || !old.getHomework().getId().equals(homework.getId()))) {
                boolean hasLearningData = old.getIncorrectCount() != null || old.getUnsolvedCount() != null
                        || old.getIncorrectQuestions() != null || old.getUnsolvedQuestions() != null
                        || old.getQuestionedQuestions() != null || Boolean.TRUE.equals(old.getFollowUpFlag());
                if (!hasLearningData) {
                    old.getHomework().getStudentHomeworks().removeIf(progress -> progress.getId().equals(old.getId()));
                    studentHomeworkRepository.delete(old);
                }
            }
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void detach(Lesson lesson, Homework homework) {
        // Keep the student/homework learning record; only remove this lesson's assignment.
        for (StudentLesson record : studentLessonRepository.findByLessonId(lesson.getId())) {
            if (record.getAssignedHomework() != null && record.getAssignedHomework().getId().equals(homework.getId())) {
                record.setAssignedHomework(null);
                record.setHomeworkAssignmentSet(true);
            }
        }
    }

    @Transactional(readOnly = true)
    public Optional<Lesson> findLesson(StudentHomework progress, Long classId) {
        return Optional.ofNullable(progress.getLesson())
                .filter(lesson -> lesson.getAcademyClass().getId().equals(classId));
    }
}
