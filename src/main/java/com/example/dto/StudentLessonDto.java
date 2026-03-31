package com.example.dto;

import com.example.entity.StudentLesson;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentLessonDto {
    private Long id;
    private Long studentId;
    private String studentName;
    private Long lessonId;
    private LocalDate lessonDate;
    private String instructorFeedback;
    private String feedbackAuthor;
    private LocalDateTime createdAt;
    private String attendanceStatus; // "PRESENT", "ABSENT", "LATE", "EARLY_LEAVE" or null
    private LocalDateTime updatedAt;

    public static StudentLessonDto from(StudentLesson studentLesson) {
        return StudentLessonDto.builder()
                .id(studentLesson.getId())
                .studentId(studentLesson.getStudent() != null ? studentLesson.getStudent().getId() : null)
                .studentName(studentLesson.getStudent() != null ? studentLesson.getStudent().getName() : null)
                .lessonId(studentLesson.getLesson() != null ? studentLesson.getLesson().getId() : null)
                .lessonDate(studentLesson.getLesson() != null ? studentLesson.getLesson().getLessonDate() : null)
                .instructorFeedback(studentLesson.getInstructorFeedback())
                .feedbackAuthor(studentLesson.getFeedbackAuthor())
                .attendanceStatus(studentLesson.getAttendanceStatus() != null
                        ? studentLesson.getAttendanceStatus().name() : null)
                .createdAt(studentLesson.getCreatedAt())
                .updatedAt(studentLesson.getUpdatedAt())
                .build();
    }
}
