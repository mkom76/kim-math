# Video Progress Tracking Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Track student video watching progress with YouTube iframe API, prevent skipping, and display completion status

**Architecture:** YouTube iframe API tracks playback every 30 seconds, backend stores progress with rollback prevention, frontend displays progress bars and teacher stats

**Tech Stack:** Spring Boot, JPA, Vue 3, TypeScript, Element Plus, YouTube iframe API

---

## Task 1: Database Schema and Entity

**Files:**
- Create: `src/main/resources/db/migration/V7__create_student_video_progress_table.sql`
- Create: `src/main/java/com/example/entity/StudentVideoProgress.java`

**Step 1: Create migration file**

Create file `src/main/resources/db/migration/V7__create_student_video_progress_table.sql`:

```sql
CREATE TABLE student_video_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    lesson_video_id BIGINT NOT NULL,
    current_time INT NOT NULL DEFAULT 0,
    duration INT NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    last_watched_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (lesson_video_id) REFERENCES lesson_videos(id) ON DELETE CASCADE,

    UNIQUE KEY uk_student_video (student_id, lesson_video_id),
    INDEX idx_student_id (student_id),
    INDEX idx_lesson_video_id (lesson_video_id),
    INDEX idx_completed (completed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Step 2: Create StudentVideoProgress entity**

Create file `src/main/java/com/example/entity/StudentVideoProgress.java`:

```java
package com.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "student_video_progress")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentVideoProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_video_id", nullable = false)
    private LessonVideo lessonVideo;

    @Column(name = "current_time", nullable = false)
    private Integer currentTime = 0;

    @Column(name = "duration", nullable = false)
    private Integer duration = 0;

    @Column(name = "completed", nullable = false)
    private Boolean completed = false;

    @Column(name = "last_watched_at")
    private LocalDateTime lastWatchedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Integer getProgressPercent() {
        if (duration == null || duration == 0) {
            return 0;
        }
        return (currentTime * 100) / duration;
    }
}
```

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V7__create_student_video_progress_table.sql
git add src/main/java/com/example/entity/StudentVideoProgress.java
git commit -m "feat: add StudentVideoProgress entity and migration

- Create student_video_progress table
- Track current_time, duration, completed status
- Unique constraint on (student_id, lesson_video_id)

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 2: Repository and DTOs

**Files:**
- Create: `src/main/java/com/example/repository/StudentVideoProgressRepository.java`
- Create: `src/main/java/com/example/dto/StudentVideoProgressDto.java`
- Create: `src/main/java/com/example/dto/VideoStatsDto.java`
- Create: `src/main/java/com/example/dto/StudentProgressDto.java`

**Step 1: Create repository**

Create file `src/main/java/com/example/repository/StudentVideoProgressRepository.java`:

```java
package com.example.repository;

import com.example.entity.StudentVideoProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentVideoProgressRepository extends JpaRepository<StudentVideoProgress, Long> {
    Optional<StudentVideoProgress> findByStudentIdAndLessonVideoId(Long studentId, Long lessonVideoId);
    List<StudentVideoProgress> findByStudentId(Long studentId);
    List<StudentVideoProgress> findByLessonVideoId(Long lessonVideoId);
}
```

**Step 2: Create StudentVideoProgressDto**

Create file `src/main/java/com/example/dto/StudentVideoProgressDto.java`:

```java
package com.example.dto;

import com.example.entity.StudentVideoProgress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentVideoProgressDto {
    private Long videoId;
    private Integer currentTime;
    private Integer duration;
    private Integer progressPercent;
    private Boolean completed;
    private LocalDateTime lastWatchedAt;

    public static StudentVideoProgressDto from(StudentVideoProgress progress) {
        return StudentVideoProgressDto.builder()
                .videoId(progress.getLessonVideo().getId())
                .currentTime(progress.getCurrentTime())
                .duration(progress.getDuration())
                .progressPercent(progress.getProgressPercent())
                .completed(progress.getCompleted())
                .lastWatchedAt(progress.getLastWatchedAt())
                .build();
    }
}
```

**Step 3: Create VideoStatsDto and StudentProgressDto**

Create file `src/main/java/com/example/dto/VideoStatsDto.java`:

```java
package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoStatsDto {
    private Long videoId;
    private String title;
    private List<StudentProgressDto> studentProgress;
}
```

Create file `src/main/java/com/example/dto/StudentProgressDto.java`:

```java
package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProgressDto {
    private Long studentId;
    private String studentName;
    private Integer progressPercent;
    private Boolean completed;
    private LocalDateTime lastWatchedAt;
}
```

**Step 4: Commit**

```bash
git add src/main/java/com/example/repository/StudentVideoProgressRepository.java
git add src/main/java/com/example/dto/StudentVideoProgressDto.java
git add src/main/java/com/example/dto/VideoStatsDto.java
git add src/main/java/com/example/dto/StudentProgressDto.java
git commit -m "feat: add video progress repository and DTOs

- Add repository with student/video queries
- Add DTOs for progress tracking and stats

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 3: Service Layer - Progress Update Logic

**Files:**
- Create: `src/main/java/com/example/service/StudentVideoProgressService.java`

**Step 1: Create service with update logic**

Create file `src/main/java/com/example/service/StudentVideoProgressService.java`:

```java
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
```

**Step 2: Commit**

```bash
git add src/main/java/com/example/service/StudentVideoProgressService.java
git commit -m "feat: add video progress service with rollback prevention

- Update progress with currentTime > existing check
- Auto-complete at 90% threshold
- Add student progress query

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 4: Controllers

**Files:**
- Create: `src/main/java/com/example/controller/StudentVideoProgressController.java`

**Step 1: Create controller**

Create file `src/main/java/com/example/controller/StudentVideoProgressController.java`:

```java
package com.example.controller;

import com.example.dto.StudentVideoProgressDto;
import com.example.service.StudentVideoProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class StudentVideoProgressController {
    private final StudentVideoProgressService progressService;

    @PutMapping("/api/students/{studentId}/videos/{videoId}/progress")
    public ResponseEntity<StudentVideoProgressDto> updateProgress(
            @PathVariable Long studentId,
            @PathVariable Long videoId,
            @RequestBody Map<String, Integer> request
    ) {
        Integer currentTime = request.get("currentTime");
        Integer duration = request.get("duration");

        if (currentTime == null || duration == null) {
            return ResponseEntity.badRequest().build();
        }

        StudentVideoProgressDto progress = progressService.updateProgress(
                studentId, videoId, currentTime, duration
        );

        return ResponseEntity.ok(progress);
    }

    @GetMapping("/api/students/{studentId}/videos/progress")
    public ResponseEntity<List<StudentVideoProgressDto>> getStudentProgress(
            @PathVariable Long studentId
    ) {
        return ResponseEntity.ok(progressService.getStudentProgress(studentId));
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/example/controller/StudentVideoProgressController.java
git commit -m "feat: add video progress API endpoints

- PUT /api/students/{studentId}/videos/{videoId}/progress
- GET /api/students/{studentId}/videos/progress

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 5: Frontend - API Client

**Files:**
- Modify: `frontend/src/api/client.ts`

**Step 1: Add progress API types and functions**

Modify `frontend/src/api/client.ts`, add before `export default client`:

```typescript
// Video Progress types and API
export interface VideoProgressUpdate {
  currentTime: number
  duration: number
}

export interface VideoProgress {
  videoId: number
  currentTime: number
  duration: number
  progressPercent: number
  completed: boolean
  lastWatchedAt: string
}

export const videoProgressAPI = {
  updateProgress: (studentId: number, videoId: number, data: VideoProgressUpdate) =>
    client.put<VideoProgress>(`/api/students/${studentId}/videos/${videoId}/progress`, data),

  getStudentProgress: (studentId: number) =>
    client.get<VideoProgress[]>(`/api/students/${studentId}/videos/progress`)
}
```

**Step 2: Commit**

```bash
git add frontend/src/api/client.ts
git commit -m "feat: add video progress API client

- Add VideoProgress and VideoProgressUpdate types
- Add updateProgress and getStudentProgress functions

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 6: Frontend - Student Videos View with Progress Tracking

**Files:**
- Modify: `frontend/src/views/StudentVideosView.vue`

**Step 1: Add YouTube iframe API and progress tracking**

Modify `frontend/src/views/StudentVideosView.vue`:

Add to script section (after existing imports):

```typescript
import { videoProgressAPI, authAPI, type VideoProgress } from '../api/client'
import { CircleCheck } from '@element-plus/icons-vue'

// Progress tracking state
const progressMap = ref<Map<number, VideoProgress>>(new Map())
const player = ref<any>(null)
const progressUpdateInterval = ref<any>(null)
const lastCurrentTime = ref(0)
const currentStudentId = ref<number>(0)

// YouTube iframe API constants
const INTERVAL_SEC = 30
const MAX_PLAYBACK_SPEED = 2.0
const TOLERANCE = 5
const MAX_ALLOWED = INTERVAL_SEC * MAX_PLAYBACK_SPEED + TOLERANCE // 65 seconds

// Load YouTube iframe API
const loadYouTubeAPI = () => {
  if (!(window as any).YT) {
    const tag = document.createElement('script')
    tag.src = 'https://www.youtube.com/iframe_player_api'
    const firstScriptTag = document.getElementsByTagName('script')[0]
    firstScriptTag.parentNode?.insertBefore(tag, firstScriptTag)
  }
}

// Fetch student progress
const fetchProgress = async (studentId: number) => {
  try {
    const response = await videoProgressAPI.getStudentProgress(studentId)
    const map = new Map<number, VideoProgress>()
    response.data.forEach(progress => {
      map.set(progress.videoId, progress)
    })
    progressMap.value = map
  } catch (error) {
    console.error('Failed to fetch progress:', error)
  }
}

// Update progress (with skip detection)
const updateProgress = async (videoId: number) => {
  if (!player.value) return

  try {
    const currentTime = Math.floor(player.value.getCurrentTime())
    const duration = Math.floor(player.value.getDuration())

    // Skip detection
    const timeDiff = currentTime - lastCurrentTime.value
    if (lastCurrentTime.value > 0 && timeDiff > MAX_ALLOWED) {
      console.log(`Skip detected: ${timeDiff}s > ${MAX_ALLOWED}s`)
      lastCurrentTime.value = currentTime
      return
    }

    // Update progress
    await videoProgressAPI.updateProgress(currentStudentId.value, videoId, {
      currentTime,
      duration
    })

    lastCurrentTime.value = currentTime

    // Refresh progress map
    await fetchProgress(currentStudentId.value)
  } catch (error) {
    // Fail silently - don't interrupt user experience
    console.error('Progress update failed:', error)
  }
}

// Player ready callback
const onPlayerReady = (event: any, video: any) => {
  player.value = event.target
  lastCurrentTime.value = 0

  // Start progress update interval
  progressUpdateInterval.value = setInterval(() => {
    updateProgress(video.id)
  }, INTERVAL_SEC * 1000)

  // Initial update after 5 seconds
  setTimeout(() => updateProgress(video.id), 5000)
}

// Modify existing playVideo function
const playVideo = (video: any) => {
  selectedVideo.value = video
  playDialogVisible.value = true
  lastCurrentTime.value = 0

  // Initialize YouTube player when dialog opens
  setTimeout(() => {
    if ((window as any).YT && (window as any).YT.Player) {
      const iframe = document.querySelector('iframe')
      if (iframe) {
        new (window as any).YT.Player(iframe, {
          events: {
            onReady: (event: any) => onPlayerReady(event, video)
          }
        })
      }
    }
  }, 500)
}

// Close dialog cleanup
const closeDialog = () => {
  if (progressUpdateInterval.value) {
    clearInterval(progressUpdateInterval.value)
    progressUpdateInterval.value = null
  }
  if (player.value) {
    player.value.destroy()
    player.value = null
  }
  playDialogVisible.value = false
  selectedVideo.value = null
  lastCurrentTime.value = 0
}

// Helper functions
const getProgress = (videoId: number) => {
  return progressMap.value.get(videoId)?.progressPercent || 0
}

const getLastWatched = (videoId: number) => {
  return progressMap.value.get(videoId)?.lastWatchedAt
}

const formatLastWatched = (dateStr: string) => {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)

  if (diffMins < 1) return '방금 전'
  if (diffMins < 60) return `${diffMins}분 전`
  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours}시간 전`
  const diffDays = Math.floor(diffHours / 24)
  return `${diffDays}일 전`
}

// Modify existing fetchVideos to also fetch progress
const originalFetchVideos = fetchVideos
const fetchVideos = async () => {
  loading.value = true
  try {
    const userResponse = await authAPI.getCurrentUser()
    const studentId = userResponse.data.userId

    if (!studentId) {
      ElMessage.error('로그인이 필요합니다')
      return
    }

    currentStudentId.value = studentId

    const response = await studentVideoAPI.getVideos(studentId)
    lessonsWithVideos.value = response.data

    // Fetch progress
    await fetchProgress(studentId)
  } catch (error) {
    ElMessage.error('영상 목록을 불러오는데 실패했습니다')
  } finally {
    loading.value = false
  }
}

// Load YouTube API on mount
onMounted(() => {
  loadYouTubeAPI()
  fetchVideos()
})
```

**Step 2: Add progress UI to template**

Modify template section, update video card:

```vue
<el-card
  shadow="hover"
  @click="playVideo(video)"
  style="cursor: pointer; height: 100%"
>
  <!-- Existing thumbnail section -->
  <div style="position: relative; padding-top: 56.25%; background: #000">
    <img
      :src="video.thumbnailUrl"
      style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: cover"
    />
    <!-- ... existing play button and duration ... -->
  </div>

  <!-- Existing title section -->
  <div style="padding: 12px 0">
    <el-tag type="info" size="small" style="margin-bottom: 8px">
      {{ video.orderIndex }}부
    </el-tag>
    <h4 style="
      margin: 0;
      font-size: 14px;
      font-weight: 500;
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
    ">
      {{ video.title }}
    </h4>

    <!-- 👇 Add progress section -->
    <div style="margin-top: 12px">
      <el-progress
        :percentage="getProgress(video.id)"
        :status="getProgress(video.id) === 100 ? 'success' : undefined"
        :stroke-width="6"
      />
      <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 6px; font-size: 12px">
        <span v-if="getProgress(video.id) === 100" style="color: #67c23a; display: flex; align-items: center; gap: 4px">
          <el-icon><CircleCheck /></el-icon>
          <span>시청 완료</span>
        </span>
        <span v-else-if="getProgress(video.id) > 0" style="color: #409eff">
          {{ getProgress(video.id) }}% 시청중
        </span>
        <span v-else style="color: #909399">
          미시청
        </span>
        <span v-if="getLastWatched(video.id)" style="color: #909399">
          {{ formatLastWatched(getLastWatched(video.id)) }}
        </span>
      </div>
    </div>
  </div>
</el-card>
```

Update dialog to use close handler:

```vue
<el-dialog
  v-model="playDialogVisible"
  :title="selectedVideo?.title"
  width="90%"
  :close-on-click-modal="true"
  @close="closeDialog"
>
  <!-- existing iframe content -->
</el-dialog>
```

**Step 3: Commit**

```bash
git add frontend/src/views/StudentVideosView.vue
git commit -m "feat: add video progress tracking to student view

- Integrate YouTube iframe API
- Update progress every 30 seconds
- Skip detection (65-second threshold for 2x speed)
- Display progress bars on video cards
- Show completion status and last watched time

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 7: Testing and Documentation

**Files:**
- Create: `docs/VIDEO_PROGRESS_TESTING.md`

**Step 1: Create testing guide**

Create file `docs/VIDEO_PROGRESS_TESTING.md`:

```markdown
# Video Progress Tracking - Testing Guide

## Manual Testing Checklist

### Student View - Progress Tracking

**Basic Functionality:**
- [ ] Play video and wait 30 seconds → Progress updates (check network tab)
- [ ] Watch video at 1x speed for 1 minute → Progress increases correctly
- [ ] Watch video at 2x speed for 1 minute → Progress increases correctly
- [ ] Refresh page → Progress persists
- [ ] Watch to 90% → Completion checkmark appears

**Skip Detection:**
- [ ] Jump forward 5 minutes → Progress does NOT update (check console for "Skip detected")
- [ ] Watch normally after skip → Progress resumes updating from current position
- [ ] 2x speed playback for 30 seconds (60s of video) → Progress updates normally

**Progress Rollback Prevention:**
- [ ] Watch to 50%, then seek back to 20% → Progress stays at 50%
- [ ] Watch to 100% (completed), then restart video → Stays marked as completed

**UI Display:**
- [ ] Completed video shows green progress bar + checkmark
- [ ] In-progress video shows blue progress bar + percentage
- [ ] Unwatched video shows gray bar + "미시청"
- [ ] Last watched time displays correctly ("5분 전", "2시간 전", etc.)

**Edge Cases:**
- [ ] Close dialog mid-video → Progress saved up to last 30-second interval
- [ ] Network failure during update → Fails silently, no error toast
- [ ] Open multiple videos in different tabs → Each tracks independently

### Performance Testing

**Load Test (if possible):**
- [ ] Simulate 10+ students watching simultaneously
- [ ] Check server CPU/memory usage
- [ ] Verify database queries are efficient (check slow query log)

## Test Scenarios

### Scenario 1: Normal Viewing
1. Student opens video
2. Watches for 2 minutes at 1x speed
3. Expected: 4 progress updates (30s, 1min, 1min30s, 2min)
4. Progress bar shows ~33% (if 6-minute video)

### Scenario 2: Speed Watching
1. Student sets playback to 2x speed
2. Watches for 1 minute real time (= 2 minutes of video)
3. Expected: 2 progress updates (30s, 1min real time = 60s, 2min video time)
4. Progress updates correctly

### Scenario 3: Skip Attempt
1. Student watches to 1 minute
2. Skips to 5 minutes
3. Expected: No progress update, console shows "Skip detected: 240s > 65s"
4. Progress stays at 1 minute

### Scenario 4: Completion
1. Student watches to 90% (e.g., 5:24 of 6:00 video)
2. Expected: Video marked as completed
3. Green progress bar + checkmark appears

### Scenario 5: Resume Watching
1. Student watches to 50%
2. Closes browser
3. Returns next day
4. Expected: Progress bar shows 50%, "1일 전" timestamp

## Known Limitations

- Progress updates every 30 seconds, so up to 30 seconds of progress can be lost if browser crashes
- Skip detection allows up to 65 seconds of jump (to accommodate 2x speed)
- Multiple devices: Last updated device wins (potential race condition, but acceptable)

## Troubleshooting

**Progress not updating:**
- Check browser console for errors
- Verify YouTube iframe API loaded (check for `window.YT`)
- Check network tab for PUT requests to `/api/students/{id}/videos/{id}/progress`

**Skip detection too sensitive:**
- Verify TOLERANCE constant (should be 5 seconds)
- Check if student is using >2x speed (not officially supported by YouTube)

**Progress bar not showing:**
- Check if `progressMap` is populated (Vue devtools)
- Verify API response has correct videoId matching

## API Testing (Postman/curl)

**Update Progress:**
```bash
curl -X PUT http://localhost:8080/api/students/1/videos/1/progress \
  -H "Content-Type: application/json" \
  -d '{"currentTime": 120, "duration": 600}'
```

**Get Student Progress:**
```bash
curl http://localhost:8080/api/students/1/videos/progress
```
```

**Step 2: Commit**

```bash
git add docs/VIDEO_PROGRESS_TESTING.md
git commit -m "docs: add video progress tracking testing guide

- Manual testing checklist
- Test scenarios for normal/skip/completion cases
- Performance testing guidelines
- API testing examples

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Completion Checklist

### Backend
- [ ] StudentVideoProgress entity and migration
- [ ] Repository with student/video queries
- [ ] Service with rollback prevention logic
- [ ] API controllers for update and query
- [ ] 90% completion threshold

### Frontend
- [ ] API client with progress types
- [ ] YouTube iframe API integration
- [ ] 30-second progress update interval
- [ ] Skip detection (65-second threshold)
- [ ] Progress bars on video cards
- [ ] Completion status display

### Testing
- [ ] Manual testing checklist completed
- [ ] Skip detection verified
- [ ] Rollback prevention verified
- [ ] Completion threshold verified
- [ ] Performance acceptable with 10+ simultaneous users

---

## Notes

- **Skip Detection:** 65 seconds = 30s interval × 2x speed + 5s tolerance
- **Completion:** 90% threshold allows skipping credits/outros
- **Network Failures:** Silent failures prevent UX disruption
- **Progress Rollback:** Backend prevents regression, frontend tracks last position
- **Teacher View:** Not included in this plan (will be added in separate task if needed)
