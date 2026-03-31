package com.example.service;

import com.example.dto.StudentProgressDto;
import com.example.dto.StudentVideoProgressDto;
import com.example.dto.VideoStatsDto;
import com.example.entity.AttendanceStatus;
import com.example.entity.LessonVideo;
import com.example.entity.Student;
import com.example.entity.StudentLesson;
import com.example.entity.StudentVideoProgress;
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

    private static final int COMPLETION_THRESHOLD = 90; // 90% to mark as completed

    /**
     * Update video progress for a student
     * - Prevents progress rollback (only updates if currentTime > existing)
     * - Auto-marks as completed if >= 90%
     */
    public StudentVideoProgressDto updateProgress(Long studentId, Long videoId,
                                                   Integer currentTime, Integer duration) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        LessonVideo video = lessonVideoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

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
        Long classId = firstVideo.getLesson().getAcademyClass().getId();
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
