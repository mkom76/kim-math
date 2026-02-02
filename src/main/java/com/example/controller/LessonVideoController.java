package com.example.controller;

import com.example.dto.LessonVideoDto;
import com.example.dto.VideoStatsDto;
import com.example.service.LessonVideoService;
import com.example.service.StudentVideoProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lessons/{lessonId}/videos")
@RequiredArgsConstructor
public class LessonVideoController {
    private final LessonVideoService lessonVideoService;
    private final StudentVideoProgressService videoProgressService;

    @GetMapping
    public ResponseEntity<List<LessonVideoDto>> getVideos(@PathVariable Long lessonId) {
        return ResponseEntity.ok(lessonVideoService.getVideos(lessonId));
    }

    @PostMapping
    public ResponseEntity<LessonVideoDto> addVideo(
            @PathVariable Long lessonId,
            @RequestBody Map<String, String> request
    ) {
        String youtubeUrl = request.get("youtubeUrl");
        if (youtubeUrl == null || youtubeUrl.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(lessonVideoService.addVideo(lessonId, youtubeUrl));
    }

    @PutMapping("/{videoId}/order")
    public ResponseEntity<LessonVideoDto> updateOrder(
            @PathVariable Long lessonId,
            @PathVariable Long videoId,
            @RequestBody Map<String, Integer> request
    ) {
        Integer orderIndex = request.get("orderIndex");
        if (orderIndex == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(lessonVideoService.updateOrder(lessonId, videoId, orderIndex));
    }

    @DeleteMapping("/{videoId}")
    public ResponseEntity<Void> deleteVideo(
            @PathVariable Long lessonId,
            @PathVariable Long videoId
    ) {
        lessonVideoService.deleteVideo(lessonId, videoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<List<VideoStatsDto>> getVideoStats(@PathVariable Long lessonId) {
        return ResponseEntity.ok(videoProgressService.getLessonVideoStats(lessonId));
    }
}
