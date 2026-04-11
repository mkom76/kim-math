package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.StudentProgressDto;
import com.example.dto.StudentVideoProgressDto;
import com.example.dto.VideoStatsDto;
import com.example.entity.AttendanceStatus;
import com.example.entity.Lesson;
import com.example.entity.LessonVideo;
import com.example.entity.Student;
import com.example.entity.StudentLesson;
import com.example.entity.StudentVideoProgress;
import com.example.exception.ForbiddenException;
import com.example.repository.LessonVideoRepository;
import com.example.repository.StudentLessonRepository;
import com.example.repository.StudentRepository;
import com.example.repository.StudentVideoProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class StudentVideoProgressService {
    private final StudentVideoProgressRepository progressRepository;
    private final StudentRepository studentRepository;
    private final LessonVideoRepository lessonVideoRepository;
    private final StudentLessonRepository studentLessonRepository;
    private final AuthorizationService authorizationService;

    private void assertCanAccessOwnProgress(Long studentId) {
        TenantContext.Context ctx = TenantContext.current();
        if (ctx == null) {
            throw new ForbiddenException("인증 컨텍스트가 없습니다");
        }
        // Student sessions have role == null and studentId in the teacherId slot
        if (ctx.role() == null) {
            if (!ctx.teacherId().equals(studentId)) {
                throw new ForbiddenException("본인의 진도만 접근할 수 있습니다");
            }
        } else {
            // Teacher access — load the student and use the standard assert
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new ForbiddenException("학생을 찾을 수 없습니다"));
            authorizationService.assertCanAccessStudent(student);
        }
    }

    private static final int COMPLETION_THRESHOLD = 90; // 90% to mark as completed

    /**
     * Update video progress for a student
     * - Prevents progress rollback (only updates if currentTime > existing)
     * - Auto-marks as completed if >= 90%
     */
    public StudentVideoProgressDto updateProgress(Long studentId, Long videoId,
                                                   Integer currentTime, Integer duration) {
        assertCanAccessOwnProgress(studentId);

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        LessonVideo video = lessonVideoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));
        authorizationService.assertCanAccessLesson(video.getLesson());

        // 결석한 수업의 영상은 시청 불가
        Long lessonId = video.getLesson().getId();
        studentLessonRepository.findByStudentIdAndLessonId(studentId, lessonId)
                .ifPresent(sl -> {
                    if (sl.getAttendanceStatus() == AttendanceStatus.ABSENT) {
                        throw new RuntimeException("결석한 수업의 영상은 시청할 수 없습니다.");
                    }
                });

        StudentVideoProgress progress = progressRepository
                .findByStudentIdAndLessonVideoId(studentId, videoId)
                .orElse(StudentVideoProgress.builder()
                        .student(student)
                        .lessonVideo(video)
                        .watchedTime(0)
                        .duration(duration)
                        .completed(false)
                        .build());

        // Prevent progress rollback: only update if new time is greater
        if (currentTime > progress.getWatchedTime()) {
            progress.setWatchedTime(currentTime);
        }

        progress.setDuration(duration);
        progress.setLastWatchedAt(LocalDateTime.now());

        // Auto-complete if >= 90%
        int progressPercent = (currentTime * 100) / duration;
        if (progressPercent >= COMPLETION_THRESHOLD) {
            progress.setCompleted(true);
        }

        progress = progressRepository.save(progress);
        return StudentVideoProgressDto.from(progress);
    }

    /**
     * Get all video progress for a student
     */
    @Transactional(readOnly = true)
    public List<StudentVideoProgressDto> getStudentProgress(Long studentId) {
        assertCanAccessOwnProgress(studentId);

        return progressRepository.findByStudentId(studentId).stream()
                .map(StudentVideoProgressDto::from)
                .collect(Collectors.toList());
    }

    /**
     * Get video watching stats for a lesson (teacher view)
     */
    @Transactional(readOnly = true)
    public List<VideoStatsDto> getLessonVideoStats(Long lessonId) {
        // 1. 해당 수업의 모든 영상 가져오기
        List<LessonVideo> videos = lessonVideoRepository.findByLessonIdOrderByOrderIndexAsc(lessonId);

        if (videos.isEmpty()) {
            return List.of();
        }

        // 2. 해당 수업의 반에 속한 모든 학생 가져오기
        LessonVideo firstVideo = videos.get(0);
        Lesson lesson = firstVideo.getLesson();
        authorizationService.assertCanAccessLesson(lesson);
        Long classId = lesson.getAcademyClass().getId();
        List<Student> students = studentRepository.findByAcademyClassId(classId);

        // 3. 각 영상에 대해 학생들의 시청 진행률 통계 생성
        return videos.stream()
                .map(video -> {
                    List<StudentProgressDto> studentProgress = students.stream()
                            .map(student -> {
                                StudentVideoProgress progress = progressRepository
                                        .findByStudentIdAndLessonVideoId(student.getId(), video.getId())
                                        .orElse(null);

                                if (progress == null) {
                                    // 아직 시청하지 않은 학생
                                    return StudentProgressDto.builder()
                                            .studentId(student.getId())
                                            .studentName(student.getName())
                                            .progressPercent(0)
                                            .completed(false)
                                            .lastWatchedAt(null)
                                            .build();
                                } else {
                                    // 시청 기록이 있는 학생
                                    int progressPercent = progress.getDuration() > 0
                                            ? (progress.getWatchedTime() * 100) / progress.getDuration()
                                            : 0;
                                    return StudentProgressDto.builder()
                                            .studentId(student.getId())
                                            .studentName(student.getName())
                                            .progressPercent(progressPercent)
                                            .completed(progress.getCompleted())
                                            .lastWatchedAt(progress.getLastWatchedAt())
                                            .build();
                                }
                            })
                            .collect(Collectors.toList());

                    return VideoStatsDto.builder()
                            .videoId(video.getId())
                            .title(video.getTitle())
                            .studentProgress(studentProgress)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
