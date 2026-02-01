package com.example.service;

import com.example.dto.LessonVideoDto;
import com.example.dto.StudentLessonVideosDto;
import com.example.dto.YouTubeVideoInfo;
import com.example.entity.Lesson;
import com.example.entity.LessonVideo;
import com.example.entity.Student;
import com.example.repository.LessonRepository;
import com.example.repository.LessonVideoRepository;
import com.example.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class LessonVideoService {
    private final LessonVideoRepository lessonVideoRepository;
    private final LessonRepository lessonRepository;
    private final StudentRepository studentRepository;
    private final YouTubeService youtubeService;

    /**
     * 영상 추가
     */
    public LessonVideoDto addVideo(Long lessonId, String youtubeUrl) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Lesson not found"));

        // 1. YouTube URL에서 video ID 추출
        String videoId = youtubeService.extractVideoId(youtubeUrl);

        // 2. YouTube API로 영상 정보 가져오기
        YouTubeVideoInfo videoInfo = youtubeService.fetchVideoInfo(videoId);

        // 3. 다음 순서 번호 계산
        List<LessonVideo> existingVideos = lessonVideoRepository.findByLessonIdOrderByOrderIndexAsc(lessonId);
        int nextOrder = existingVideos.size() + 1;

        // 4. LessonVideo 엔티티 생성 및 저장
        LessonVideo video = LessonVideo.builder()
                .lesson(lesson)
                .youtubeUrl(youtubeUrl)
                .youtubeVideoId(videoInfo.getVideoId())
                .title(videoInfo.getTitle())
                .thumbnailUrl(videoInfo.getThumbnailUrl())
                .duration(videoInfo.getDuration())
                .orderIndex(nextOrder)
                .build();

        video = lessonVideoRepository.save(video);
        return LessonVideoDto.from(video);
    }

    /**
     * 영상 목록 조회
     */
    @Transactional(readOnly = true)
    public List<LessonVideoDto> getVideos(Long lessonId) {
        return lessonVideoRepository.findByLessonIdOrderByOrderIndexAsc(lessonId).stream()
                .map(LessonVideoDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 영상 순서 변경
     */
    public LessonVideoDto updateOrder(Long lessonId, Long videoId, int newOrderIndex) {
        LessonVideo video = lessonVideoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        if (!video.getLesson().getId().equals(lessonId)) {
            throw new RuntimeException("Video does not belong to this lesson");
        }

        List<LessonVideo> allVideos = lessonVideoRepository.findByLessonIdOrderByOrderIndexAsc(lessonId);

        int oldIndex = video.getOrderIndex();
        if (oldIndex == newOrderIndex) {
            return LessonVideoDto.from(video);
        }

        // 순서 재배치
        if (newOrderIndex < oldIndex) {
            // 위로 이동
            for (LessonVideo v : allVideos) {
                if (v.getOrderIndex() >= newOrderIndex && v.getOrderIndex() < oldIndex) {
                    v.setOrderIndex(v.getOrderIndex() + 1);
                }
            }
        } else {
            // 아래로 이동
            for (LessonVideo v : allVideos) {
                if (v.getOrderIndex() > oldIndex && v.getOrderIndex() <= newOrderIndex) {
                    v.setOrderIndex(v.getOrderIndex() - 1);
                }
            }
        }

        video.setOrderIndex(newOrderIndex);
        lessonVideoRepository.saveAll(allVideos);

        return LessonVideoDto.from(video);
    }

    /**
     * 영상 삭제
     */
    public void deleteVideo(Long lessonId, Long videoId) {
        LessonVideo video = lessonVideoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        if (!video.getLesson().getId().equals(lessonId)) {
            throw new RuntimeException("Video does not belong to this lesson");
        }

        int deletedOrder = video.getOrderIndex();
        lessonVideoRepository.delete(video);

        // 삭제된 영상 뒤의 영상들 순서 조정
        List<LessonVideo> remainingVideos = lessonVideoRepository.findByLessonIdOrderByOrderIndexAsc(lessonId);
        for (LessonVideo v : remainingVideos) {
            if (v.getOrderIndex() > deletedOrder) {
                v.setOrderIndex(v.getOrderIndex() - 1);
            }
        }
        lessonVideoRepository.saveAll(remainingVideos);
    }

    /**
     * 학생용: 자기 반의 모든 수업 영상 조회
     */
    @Transactional(readOnly = true)
    public List<StudentLessonVideosDto> getStudentVideos(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        if (student.getAcademyClass() == null) {
            throw new RuntimeException("Student is not assigned to a class");
        }

        Long classId = student.getAcademyClass().getId();

        // 해당 반의 모든 수업 조회 (최신순)
        List<Lesson> lessons = lessonRepository.findAll().stream()
                .filter(lesson -> lesson.getAcademyClass() != null &&
                        lesson.getAcademyClass().getId().equals(classId))
                .sorted((a, b) -> b.getLessonDate().compareTo(a.getLessonDate()))
                .collect(Collectors.toList());

        return lessons.stream()
                .filter(lesson -> !lesson.getVideos().isEmpty())
                .map(lesson -> StudentLessonVideosDto.builder()
                        .lessonId(lesson.getId())
                        .lessonDate(lesson.getLessonDate().toString())
                        .className(lesson.getAcademyClass().getName())
                        .videos(lesson.getVideos().stream()
                                .map(LessonVideoDto::from)
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }
}
