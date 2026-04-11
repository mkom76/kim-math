package com.example.service;

import com.example.config.security.TenantContext;
import com.example.controller.LessonController;
import com.example.dto.AttendanceStatsDto;
import com.example.dto.HomeworkDto;
import com.example.dto.LessonDto;
import com.example.dto.LessonStudentStatsDto;
import com.example.dto.StudentHomeworkAssignmentDto;
import com.example.dto.StudentLessonDto;
import com.example.entity.*;
import com.example.exception.ForbiddenException;
import com.example.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LessonService {
    private final LessonRepository lessonRepository;
    private final AcademyRepository academyRepository;
    private final AcademyClassRepository academyClassRepository;
    private final TestRepository testRepository;
    private final HomeworkRepository homeworkRepository;
    private final StudentRepository studentRepository;
    private final StudentSubmissionRepository studentSubmissionRepository;
    private final StudentHomeworkRepository studentHomeworkRepository;
    private final StudentLessonRepository studentLessonRepository;
    private final AuthorizationService authorizationService;

    /**
     * Get or create lesson for a specific date/class
     * Called automatically when creating test/homework
     */
    public Lesson getOrCreateLesson(Long academyId, Long classId, LocalDate lessonDate) {
        return lessonRepository.findByAcademyIdAndClassIdAndLessonDate(academyId, classId, lessonDate)
                .orElseGet(() -> {
                    Academy academy = academyRepository.findById(academyId)
                            .orElseThrow(() -> new RuntimeException("Academy not found"));
                    AcademyClass academyClass = academyClassRepository.findById(classId)
                            .orElseThrow(() -> new RuntimeException("Class not found"));

                    Lesson lesson = Lesson.builder()
                            .lessonDate(lessonDate)
                            .academy(academy)
                            .academyClass(academyClass)
                            .build();
                    return lessonRepository.save(lesson);
                });
    }

    @Transactional(readOnly = true)
    public Page<LessonDto> getLessons(Pageable pageable) {
        return lessonRepository.findAll(pageable).map(LessonDto::from);
    }

    @Transactional(readOnly = true)
    public LessonDto getLesson(Long id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        authorizationService.assertCanAccessLesson(lesson);
        return LessonDto.from(lesson);
    }

    @Transactional(readOnly = true)
    public List<LessonDto> getLessonsByClass(Long classId) {
        AcademyClass academyClass = academyClassRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
        authorizationService.assertCanModifyClass(academyClass);

        return lessonRepository.findByAcademyClassIdOrderByLessonDateDesc(classId)
                .stream()
                .map(LessonDto::from)
                .collect(Collectors.toList());
    }

    public void deleteLesson(Long id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        authorizationService.assertCanAccessLesson(lesson);

        // Validate: cannot delete if test or homework attached
        if (lesson.getTest() != null) {
            throw new RuntimeException("Cannot delete lesson with attached test");
        }
        if (!lesson.getHomeworks().isEmpty()) {
            throw new RuntimeException("Cannot delete lesson with attached homework");
        }

        lessonRepository.delete(lesson);
    }

    /**
     * Create a new lesson (manual creation for the new workflow)
     */
    public LessonDto createLesson(Long academyId, Long classId, LocalDate lessonDate) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null) {
            throw new ForbiddenException("인증 컨텍스트가 없습니다");
        }
        if (academyId != null && !academyId.equals(ctx.academyId())) {
            throw new ForbiddenException("활성 학원과 다른 학원에 수업을 생성할 수 없습니다");
        }

        Academy academy = academyRepository.findById(ctx.academyId())
                .orElseThrow(() -> new RuntimeException("Academy not found"));
        AcademyClass academyClass = academyClassRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
        authorizationService.assertCanModifyClass(academyClass);

        // Check if lesson already exists for this date/class
        if (lessonRepository.findByAcademyIdAndClassIdAndLessonDate(academyId, classId, lessonDate).isPresent()) {
            throw new RuntimeException("Lesson already exists for this date and class");
        }

        Lesson lesson = Lesson.builder()
                .lessonDate(lessonDate)
                .academy(academy)
                .academyClass(academyClass)
                .build();

        lesson = lessonRepository.save(lesson);
        return LessonDto.from(lesson);
    }

    /**
     * Attach a test to a lesson
     */
    public LessonDto attachTest(Long lessonId, Long testId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        authorizationService.assertCanAccessLesson(lesson);
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found"));
        authorizationService.assertCanAccessTest(test);

        // Validate: test must not already be attached to another lesson
        if (test.getLesson() != null && !test.getLesson().getId().equals(lessonId)) {
            throw new RuntimeException("Test is already attached to another lesson");
        }

        // Validate: test must be from same academy and class
        if (!test.getAcademy().getId().equals(lesson.getAcademy().getId()) ||
            !test.getAcademyClass().getId().equals(lesson.getAcademyClass().getId())) {
            throw new RuntimeException("Test must be from the same academy and class as the lesson");
        }

        test.setLesson(lesson);
        testRepository.save(test);

        return LessonDto.from(lessonRepository.findById(lessonId).orElseThrow());
    }

    /**
     * Attach homework to a lesson
     */
    public LessonDto attachHomework(Long lessonId, Long homeworkId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        authorizationService.assertCanAccessLesson(lesson);
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("Homework not found"));
        authorizationService.assertCanAccessHomework(homework);

        // Validate: homework must not already be attached to another lesson
        if (homework.getLesson() != null && !homework.getLesson().getId().equals(lessonId)) {
            throw new RuntimeException("Homework is already attached to another lesson");
        }

        // Validate: homework must be from same academy and class
        if (!homework.getAcademy().getId().equals(lesson.getAcademy().getId()) ||
            !homework.getAcademyClass().getId().equals(lesson.getAcademyClass().getId())) {
            throw new RuntimeException("Homework must be from the same academy and class as the lesson");
        }

        homework.setLesson(lesson);
        homeworkRepository.save(homework);

        return LessonDto.from(lessonRepository.findById(lessonId).orElseThrow());
    }

    /**
     * Detach test from a lesson
     */
    public LessonDto detachTest(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        authorizationService.assertCanAccessLesson(lesson);

        if (lesson.getTest() != null) {
            Test test = lesson.getTest();
            test.setLesson(null);
            testRepository.save(test);
        }

        return LessonDto.from(lessonRepository.findById(lessonId).orElseThrow());
    }

    /**
     * Remove homework from a lesson (specific homework by ID)
     */
    public LessonDto removeHomework(Long lessonId, Long homeworkId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        authorizationService.assertCanAccessLesson(lesson);
        Homework homework = homeworkRepository.findById(homeworkId)
                .orElseThrow(() -> new RuntimeException("Homework not found"));
        authorizationService.assertCanAccessHomework(homework);

        // Validate: homework must belong to this lesson
        if (homework.getLesson() == null || !homework.getLesson().getId().equals(lessonId)) {
            throw new RuntimeException("Homework is not attached to this lesson");
        }

        // Remove homework from lesson (orphanRemoval will delete StudentHomework records)
        homework.setLesson(null);
        homeworkRepository.save(homework);

        return LessonDto.from(lessonRepository.findById(lessonId).orElseThrow());
    }

    /**
     * Get lessons for a specific student (based on their class)
     */
    @Transactional(readOnly = true)
    public List<LessonDto> getLessonsByStudent(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);

        return lessonRepository.findByAcademyClassIdOrderByLessonDateDesc(student.getAcademyClass().getId())
                .stream()
                .map(LessonDto::from)
                .collect(Collectors.toList());
    }

    /**
     * Update lesson feedback and announcement
     */
    public LessonDto updateLessonContent(Long lessonId, String commonFeedback, String announcement) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        authorizationService.assertCanAccessLesson(lesson);

        lesson.setCommonFeedback(commonFeedback);
        lesson.setAnnouncement(announcement);

        lesson = lessonRepository.save(lesson);
        return LessonDto.from(lesson);
    }

    /**
     * Update lesson date
     */
    public LessonDto updateLessonDate(Long lessonId, LocalDate newDate) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        authorizationService.assertCanAccessLesson(lesson);

        // Check if another lesson already exists for this date and class
        Optional<Lesson> existingLesson = lessonRepository.findByAcademyIdAndClassIdAndLessonDate(
                lesson.getAcademy().getId(),
                lesson.getAcademyClass().getId(),
                newDate);

        if (existingLesson.isPresent() && !existingLesson.get().getId().equals(lessonId)) {
            throw new RuntimeException("같은 반에 해당 날짜의 수업이 이미 존재합니다");
        }

        lesson.setLessonDate(newDate);
        lesson = lessonRepository.save(lesson);
        return LessonDto.from(lesson);
    }

    /**
     * Get student statistics for a lesson (test scores and homework completion)
     */
    @Transactional(readOnly = true)
    public LessonStudentStatsDto getLessonStudentStats(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        authorizationService.assertCanAccessLesson(lesson);

        // Get all students in this class
        List<Student> students = studentRepository.findAll().stream()
                .filter(s -> s.getAcademyClass() != null && s.getAcademyClass().getId().equals(lesson.getAcademyClass().getId()))
                .collect(Collectors.toList());

        LessonStudentStatsDto stats = new LessonStudentStatsDto();

        // Test statistics
        if (lesson.getTest() != null) {
            List<LessonStudentStatsDto.StudentTestScore> testScores = new ArrayList<>();
            List<StudentSubmission> submissions = studentSubmissionRepository.findByTestId(lesson.getTest().getId());
            Map<Long, StudentSubmission> submissionMap = submissions.stream()
                    .collect(Collectors.toMap(s -> s.getStudent().getId(), s -> s));

            for (Student student : students) {
                StudentSubmission submission = submissionMap.get(student.getId());
                testScores.add(LessonStudentStatsDto.StudentTestScore.builder()
                        .studentId(student.getId())
                        .studentName(student.getName())
                        .score(submission != null ? submission.getTotalScore() : null)
                        .submitted(submission != null)
                        .build());
            }

            // Sort by score (descending), null scores at the end
            testScores.sort((a, b) -> {
                if (a.getScore() == null) return 1;
                if (b.getScore() == null) return -1;
                return b.getScore().compareTo(a.getScore());
            });

            // Assign ranks
            int rank = 1;
            Integer prevScore = null;
            int sameRankCount = 0;
            for (LessonStudentStatsDto.StudentTestScore score : testScores) {
                if (score.getScore() != null) {
                    if (prevScore != null && prevScore.equals(score.getScore())) {
                        sameRankCount++;
                    } else {
                        rank += sameRankCount;
                        sameRankCount = 1;
                    }
                    score.setRank(rank);
                    prevScore = score.getScore();
                }
            }

            // Calculate average
            double average = submissions.stream()
                    .mapToInt(StudentSubmission::getTotalScore)
                    .average()
                    .orElse(0.0);

            stats.setTestScores(testScores);
            stats.setTestAverage(average);
        }

        // Homework statistics - 여러 숙제가 있을 수 있으므로 학생에게 할당된 숙제 기준으로 통계
        if (!lesson.getHomeworks().isEmpty()) {
            List<LessonStudentStatsDto.StudentHomeworkCompletion> homeworkCompletions = new ArrayList<>();

            // Get all homework IDs for this lesson
            List<Long> homeworkIds = lesson.getHomeworks().stream()
                    .map(Homework::getId)
                    .collect(Collectors.toList());

            // Get all student homework records for this lesson
            List<StudentHomework> studentHomeworks = studentHomeworkRepository
                    .findByHomeworkIdIn(homeworkIds);
            Map<Long, StudentHomework> homeworkMap = studentHomeworks.stream()
                    .collect(Collectors.toMap(sh -> sh.getStudent().getId(), sh -> sh));

            for (Student student : students) {
                StudentHomework studentHomework = homeworkMap.get(student.getId());
                homeworkCompletions.add(LessonStudentStatsDto.StudentHomeworkCompletion.builder()
                        .studentId(student.getId())
                        .studentName(student.getName())
                        .incorrectCount(studentHomework != null ? studentHomework.getIncorrectCount() : null)
                        .unsolvedCount(studentHomework != null ? studentHomework.getUnsolvedCount() : null)
                        .incorrectQuestions(studentHomework != null ? studentHomework.getIncorrectQuestions() : null)
                        .unsolvedQuestions(studentHomework != null ? studentHomework.getUnsolvedQuestions() : null)
                        .completion(studentHomework != null ? studentHomework.getCompletion() : null)
                        .completed(studentHomework != null)
                        .totalQuestions(studentHomework != null ? studentHomework.getHomework().getQuestionCount() : 0)
                        .build());
            }

            // Sort by completion (descending), null completion at the end
            homeworkCompletions.sort((a, b) -> {
                if (a.getCompletion() == null) return 1;
                if (b.getCompletion() == null) return -1;
                return b.getCompletion().compareTo(a.getCompletion());
            });

            // Calculate average completion (only for submitted homeworks)
            double average = studentHomeworks.stream()
                    .map(StudentHomework::getCompletion)
                    .filter(completion -> completion != null)
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0.0);

            stats.setHomeworkCompletions(homeworkCompletions);
            stats.setHomeworkAverage(average);
        }

        return stats;
    }

    /**
     * Get all homeworks attached to a lesson
     */
    @Transactional(readOnly = true)
    public List<HomeworkDto> getLessonHomeworks(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        authorizationService.assertCanAccessLesson(lesson);

        return lesson.getHomeworks().stream()
                .map(HomeworkDto::from)
                .collect(Collectors.toList());
    }

    /**
     * Assign homeworks to students
     * @param lessonId the lesson ID
     * @param assignments Map of studentId -> homeworkId
     */
    public void assignHomeworksToStudents(Long lessonId, Map<Long, Long> assignments) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        authorizationService.assertCanAccessLesson(lesson);

        // Get all homework IDs for validation
        List<Long> lessonHomeworkIds = lesson.getHomeworks().stream()
                .map(Homework::getId)
                .collect(Collectors.toList());

        Set<Long> validHomeworkIds = new HashSet<>(lessonHomeworkIds);

        // Process each assignment
        for (Map.Entry<Long, Long> entry : assignments.entrySet()) {
            Long studentId = entry.getKey();
            Long homeworkId = entry.getValue();

            // Validate homework belongs to this lesson
            if (!validHomeworkIds.contains(homeworkId)) {
                throw new RuntimeException("Homework " + homeworkId + " is not attached to this lesson");
            }

            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student " + studentId + " not found"));
            Homework homework = homeworkRepository.findById(homeworkId)
                    .orElseThrow(() -> new RuntimeException("Homework " + homeworkId + " not found"));

            // Get all student's homework assignments for this lesson's homeworks
            List<StudentHomework> existingAssignments = studentHomeworkRepository
                    .findByHomeworkIdIn(lessonHomeworkIds).stream()
                    .filter(sh -> sh.getStudent().getId().equals(studentId))
                    .collect(Collectors.toList());

            if (!existingAssignments.isEmpty()) {
                StudentHomework existingAssignment = existingAssignments.get(0);

                // If already assigned to the same homework, skip
                if (existingAssignment.getHomework().getId().equals(homeworkId)) {
                    continue;
                }

                // If already assigned to a different homework
                // Check if student has already submitted (has incorrectCount)
                if (existingAssignment.getIncorrectCount() != null) {
                    throw new RuntimeException(
                        "학생 " + student.getName() + "은(는) 이미 숙제 '" +
                        existingAssignment.getHomework().getTitle() + "'를 제출했습니다. " +
                        "제출된 숙제는 다른 숙제로 변경할 수 없습니다."
                    );
                }

                // If not submitted yet, delete old assignment and create new one
                studentHomeworkRepository.delete(existingAssignment);
            }

            // Create new assignment
            StudentHomework sh = StudentHomework.builder()
                    .student(student)
                    .homework(homework)
                    .incorrectCount(null)  // Not submitted yet
                    .build();
            studentHomeworkRepository.save(sh);
        }
    }

    /**
     * Get student homework assignments for a lesson
     */
    @Transactional(readOnly = true)
    public List<StudentHomeworkAssignmentDto> getAssignments(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        authorizationService.assertCanAccessLesson(lesson);

        // Get all students in this class
        List<Student> students = studentRepository.findAll().stream()
                .filter(s -> s.getAcademyClass() != null &&
                             s.getAcademyClass().getId().equals(lesson.getAcademyClass().getId()))
                .collect(Collectors.toList());

        // Get all homework IDs for this lesson
        List<Long> homeworkIds = lesson.getHomeworks().stream()
                .map(Homework::getId)
                .collect(Collectors.toList());

        // Get all student homework records
        List<StudentHomework> studentHomeworks = studentHomeworkRepository
                .findByHomeworkIdIn(homeworkIds);
        Map<Long, StudentHomework> assignmentMap = studentHomeworks.stream()
                .collect(Collectors.toMap(sh -> sh.getStudent().getId(), sh -> sh));

        // Build assignment DTOs
        return students.stream()
                .map(student -> {
                    StudentHomework sh = assignmentMap.get(student.getId());
                    return StudentHomeworkAssignmentDto.builder()
                            .studentId(student.getId())
                            .studentName(student.getName())
                            .assignedHomeworkId(sh != null ? sh.getHomework().getId() : null)
                            .assignedHomeworkTitle(sh != null ? sh.getHomework().getTitle() : null)
                            .incorrectCount(sh != null ? sh.getIncorrectCount() : null)
                            .unsolvedCount(sh != null ? sh.getUnsolvedCount() : null)
                            .incorrectQuestions(sh != null ? sh.getIncorrectQuestions() : null)
                            .unsolvedQuestions(sh != null ? sh.getUnsolvedQuestions() : null)
                            .completion(sh != null ? sh.getCompletion() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 수업의 출석 현황 조회
     */
    @Transactional(readOnly = true)
    public List<StudentLessonDto> getAttendance(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        authorizationService.assertCanAccessLesson(lesson);

        List<Student> students = studentRepository.findByAcademyClassId(
                lesson.getAcademyClass().getId());

        List<StudentLesson> existing = studentLessonRepository.findByLessonId(lessonId);
        Map<Long, StudentLesson> existingMap = existing.stream()
                .collect(Collectors.toMap(sl -> sl.getStudent().getId(), sl -> sl));

        return students.stream()
                .map(student -> {
                    StudentLesson sl = existingMap.get(student.getId());
                    return StudentLessonDto.builder()
                            .id(sl != null ? sl.getId() : null)
                            .studentId(student.getId())
                            .studentName(student.getName())
                            .lessonId(lessonId)
                            .lessonDate(lesson.getLessonDate())
                            .attendanceStatus(sl != null && sl.getAttendanceStatus() != null
                                    ? sl.getAttendanceStatus().name() : null)
                            .instructorFeedback(sl != null ? sl.getInstructorFeedback() : null)
                            .feedbackAuthor(sl != null ? sl.getFeedbackAuthor() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 출석 일괄 저장
     */
    public void saveAttendance(Long lessonId, List<LessonController.AttendanceRequest> attendanceList) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));
        authorizationService.assertCanAccessLesson(lesson);

        for (LessonController.AttendanceRequest req : attendanceList) {
            StudentLesson sl = studentLessonRepository
                    .findByStudentIdAndLessonId(req.getStudentId(), lessonId)
                    .orElseGet(() -> {
                        Student student = studentRepository.findById(req.getStudentId())
                                .orElseThrow(() -> new RuntimeException("Student not found"));
                        return StudentLesson.builder()
                                .student(student)
                                .lesson(lesson)
                                .build();
                    });

            sl.setAttendanceStatus(req.getStatus() != null
                    ? AttendanceStatus.valueOf(req.getStatus()) : null);
            studentLessonRepository.save(sl);
        }
    }

    /**
     * 학생별 출석 통계 조회
     */
    @Transactional(readOnly = true)
    public AttendanceStatsDto getAttendanceStats(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        authorizationService.assertCanAccessStudent(student);

        long totalLessons = lessonRepository.findByAcademyClassIdOrderByLessonDateDesc(
                student.getAcademyClass().getId()).size();

        List<Object[]> counts = studentLessonRepository
                .countByStudentIdGroupByAttendanceStatus(studentId);

        int presentCount = 0, absentCount = 0, lateCount = 0, earlyLeaveCount = 0, videoCount = 0, uncheckedCount = 0;

        for (Object[] row : counts) {
            AttendanceStatus status = (AttendanceStatus) row[0];
            long count = (Long) row[1];
            if (status == null) {
                uncheckedCount = (int) count;
            } else {
                switch (status) {
                    case PRESENT -> presentCount = (int) count;
                    case ABSENT -> absentCount = (int) count;
                    case LATE -> lateCount = (int) count;
                    case EARLY_LEAVE -> earlyLeaveCount = (int) count;
                    case VIDEO -> videoCount = (int) count;
                }
            }
        }

        int checkedCount = presentCount + absentCount + lateCount + earlyLeaveCount + videoCount;
        double attendanceRate = checkedCount > 0
                ? (double) (presentCount + videoCount) / checkedCount * 100 : 0;

        return AttendanceStatsDto.builder()
                .studentId(studentId)
                .studentName(student.getName())
                .totalLessons((int) totalLessons)
                .presentCount(presentCount)
                .absentCount(absentCount)
                .lateCount(lateCount)
                .earlyLeaveCount(earlyLeaveCount)
                .videoCount(videoCount)
                .uncheckedCount(uncheckedCount)
                .attendanceRate(Math.round(attendanceRate * 10) / 10.0)
                .build();
    }
}
