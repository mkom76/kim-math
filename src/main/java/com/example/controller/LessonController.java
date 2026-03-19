package com.example.controller;

import com.example.dto.HomeworkDto;
import com.example.dto.LessonDto;
import com.example.dto.LessonStudentStatsDto;
import com.example.dto.StudentHomeworkAssignmentDto;
import com.example.service.LessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lessons")
@RequiredArgsConstructor
public class LessonController {
    private final LessonService lessonService;

    @GetMapping
    public ResponseEntity<Page<LessonDto>> getLessons(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(lessonService.getLessons(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LessonDto> getLesson(@PathVariable Long id) {
        return ResponseEntity.ok(lessonService.getLesson(id));
    }

    @GetMapping("/class/{classId}")
    public ResponseEntity<List<LessonDto>> getLessonsByClass(@PathVariable Long classId) {
        return ResponseEntity.ok(lessonService.getLessonsByClass(classId));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLesson(@PathVariable Long id) {
        lessonService.deleteLesson(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping
    public ResponseEntity<LessonDto> createLesson(@RequestBody LessonDto dto) {
        return ResponseEntity.ok(lessonService.createLesson(dto.getAcademyId(), dto.getClassId(), dto.getLessonDate()));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PutMapping("/{lessonId}/test/{testId}")
    public ResponseEntity<LessonDto> attachTest(@PathVariable Long lessonId, @PathVariable Long testId) {
        return ResponseEntity.ok(lessonService.attachTest(lessonId, testId));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PutMapping("/{lessonId}/homework/{homeworkId}")
    public ResponseEntity<LessonDto> attachHomework(@PathVariable Long lessonId, @PathVariable Long homeworkId) {
        return ResponseEntity.ok(lessonService.attachHomework(lessonId, homeworkId));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @DeleteMapping("/{lessonId}/test")
    public ResponseEntity<LessonDto> detachTest(@PathVariable Long lessonId) {
        return ResponseEntity.ok(lessonService.detachTest(lessonId));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @DeleteMapping("/{lessonId}/homeworks/{homeworkId}")
    public ResponseEntity<LessonDto> removeHomework(
            @PathVariable Long lessonId,
            @PathVariable Long homeworkId) {
        return ResponseEntity.ok(lessonService.removeHomework(lessonId, homeworkId));
    }

    @GetMapping("/{lessonId}/homeworks")
    public ResponseEntity<List<HomeworkDto>> getLessonHomeworks(@PathVariable Long lessonId) {
        return ResponseEntity.ok(lessonService.getLessonHomeworks(lessonId));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping("/{lessonId}/assign-homeworks")
    public ResponseEntity<Void> assignHomeworksToStudents(
            @PathVariable Long lessonId,
            @RequestBody Map<Long, Long> assignments) {
        lessonService.assignHomeworksToStudents(lessonId, assignments);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{lessonId}/assignments")
    public ResponseEntity<List<StudentHomeworkAssignmentDto>> getAssignments(@PathVariable Long lessonId) {
        return ResponseEntity.ok(lessonService.getAssignments(lessonId));
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<LessonDto>> getLessonsByStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(lessonService.getLessonsByStudent(studentId));
    }

    @GetMapping("/{lessonId}/stats")
    public ResponseEntity<LessonStudentStatsDto> getLessonStudentStats(@PathVariable Long lessonId) {
        return ResponseEntity.ok(lessonService.getLessonStudentStats(lessonId));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PutMapping("/{lessonId}/content")
    public ResponseEntity<LessonDto> updateLessonContent(
            @PathVariable Long lessonId,
            @RequestBody UpdateLessonContentRequest request) {
        return ResponseEntity.ok(lessonService.updateLessonContent(
                lessonId, request.getCommonFeedback(), request.getAnnouncement()));
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PutMapping("/{lessonId}/date")
    public ResponseEntity<LessonDto> updateLessonDate(
            @PathVariable Long lessonId,
            @RequestBody UpdateLessonDateRequest request) {
        return ResponseEntity.ok(lessonService.updateLessonDate(lessonId, request.getLessonDate()));
    }

    // Request DTO for updating lesson content
    public static class UpdateLessonContentRequest {
        private String commonFeedback;
        private String announcement;

        public String getCommonFeedback() {
            return commonFeedback;
        }

        public void setCommonFeedback(String commonFeedback) {
            this.commonFeedback = commonFeedback;
        }

        public String getAnnouncement() {
            return announcement;
        }

        public void setAnnouncement(String announcement) {
            this.announcement = announcement;
        }
    }

    // Request DTO for updating lesson date
    public static class UpdateLessonDateRequest {
        private java.time.LocalDate lessonDate;

        public java.time.LocalDate getLessonDate() {
            return lessonDate;
        }

        public void setLessonDate(java.time.LocalDate lessonDate) {
            this.lessonDate = lessonDate;
        }
    }
}
