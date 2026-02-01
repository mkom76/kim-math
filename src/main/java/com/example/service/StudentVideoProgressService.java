package com.example.service;

import com.example.dto.StudentProgressDto;
import com.example.dto.StudentVideoProgressDto;
import com.example.dto.VideoStatsDto;
import com.example.entity.LessonVideo;
import com.example.entity.Student;
import com.example.entity.StudentVideoProgress;
import com.example.repository.LessonVideoRepository;
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

        StudentVideoProgress progress = progressRepository
                .findByStudentIdAndLessonVideoId(studentId, videoId)
                .orElse(StudentVideoProgress.builder()
                        .student(student)
                        .lessonVideo(video)
                        .currentTime(0)
                        .duration(duration)
                        .completed(false)
                        .build());

        // Prevent progress rollback: only update if new time is greater
        if (currentTime > progress.getCurrentTime()) {
            progress.setCurrentTime(currentTime);
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
        // This will be implemented after we have the lesson video relationship
        // For now, return empty list
        return List.of();
    }
}
