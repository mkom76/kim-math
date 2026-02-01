# 수업 영상 업로드 및 다시보기 기능 설계

**작성일:** 2026-02-01
**목적:** 선생님이 YouTube 영상 링크를 업로드하고, 학생이 자기 반 수업 영상을 다시보기할 수 있는 기능 구현

## 요구사항

### 선생님 기능
- YouTube URL만 입력하면 제목, 썸네일, 재생시간 자동으로 가져오기
- 하나의 수업에 여러 개의 영상 업로드 가능 (파트별)
- 영상 순서 변경 가능
- 영상 삭제 가능
- 기존 수업 관리 페이지에서 영상 관리

### 학생 기능
- 자기 반 수업 영상만 볼 수 있음
- 별도의 "다시보기" 메뉴에서 전체 영상 목록 확인
- 썸네일 클릭하여 영상 재생

---

## 1. 데이터 모델 설계

### 새로운 엔티티: LessonVideo

```java
@Entity
@Table(name = "lesson_videos")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonVideo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "youtube_url", nullable = false, length = 500)
    private String youtubeUrl;  // YouTube URL

    @Column(name = "youtube_video_id", nullable = false, length = 50)
    private String youtubeVideoId;  // YouTube video ID (파싱)

    @Column(name = "title", length = 200)
    private String title;  // YouTube API에서 가져온 제목

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;  // 썸네일 URL

    @Column(name = "duration", length = 20)
    private String duration;  // 재생 시간 (예: "PT15M33S")

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;  // 순서 (1부, 2부, 3부...)

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

### Lesson 엔티티 수정

```java
@OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("orderIndex ASC")
@Builder.Default
private List<LessonVideo> videos = new ArrayList<>();
```

### 관계
- Lesson : LessonVideo = 1 : N
- orderIndex로 영상 순서 관리
- orphanRemoval = true로 Lesson 삭제 시 영상도 함께 삭제

---

## 2. Backend API 설계

### 2.1 YouTube 서비스

```java
@Service
@RequiredArgsConstructor
public class YouTubeService {
    @Value("${youtube.api.key}")
    private String apiKey;

    private static final String API_URL = "https://www.googleapis.com/youtube/v3/videos";

    /**
     * YouTube URL에서 video ID 추출
     */
    public String extractVideoId(String youtubeUrl) {
        // 지원 형식:
        // - https://www.youtube.com/watch?v=VIDEO_ID
        // - https://youtu.be/VIDEO_ID
        // - https://www.youtube.com/embed/VIDEO_ID

        String videoId = null;

        // Pattern 1: youtube.com/watch?v=VIDEO_ID
        Pattern pattern1 = Pattern.compile("(?:youtube\\.com/watch\\?v=)([\\w-]+)");
        Matcher matcher1 = pattern1.matcher(youtubeUrl);
        if (matcher1.find()) {
            videoId = matcher1.group(1);
        }

        // Pattern 2: youtu.be/VIDEO_ID
        Pattern pattern2 = Pattern.compile("(?:youtu\\.be/)([\\w-]+)");
        Matcher matcher2 = pattern2.matcher(youtubeUrl);
        if (matcher2.find()) {
            videoId = matcher2.group(1);
        }

        // Pattern 3: youtube.com/embed/VIDEO_ID
        Pattern pattern3 = Pattern.compile("(?:youtube\\.com/embed/)([\\w-]+)");
        Matcher matcher3 = pattern3.matcher(youtubeUrl);
        if (matcher3.find()) {
            videoId = matcher3.group(1);
        }

        if (videoId == null) {
            throw new IllegalArgumentException("유효하지 않은 YouTube URL입니다");
        }

        return videoId;
    }

    /**
     * YouTube Data API v3로 영상 정보 가져오기
     */
    public YouTubeVideoInfo fetchVideoInfo(String videoId) {
        String url = API_URL + "?id=" + videoId
                    + "&key=" + apiKey
                    + "&part=snippet,contentDetails";

        // RestTemplate 또는 WebClient로 API 호출
        // Response 파싱하여 YouTubeVideoInfo 반환

        return YouTubeVideoInfo.builder()
                .videoId(videoId)
                .title(snippet.getTitle())
                .thumbnailUrl(snippet.getThumbnails().getHigh().getUrl())
                .duration(contentDetails.getDuration())
                .build();
    }
}

@Data
@Builder
public class YouTubeVideoInfo {
    private String videoId;
    private String title;
    private String thumbnailUrl;
    private String duration;  // ISO 8601 format (PT15M33S)
}
```

### 2.2 API 엔드포인트

**선생님용 (LessonVideoController):**

```java
@RestController
@RequestMapping("/api/lessons/{lessonId}/videos")
@RequiredArgsConstructor
public class LessonVideoController {

    // 영상 추가
    POST /api/lessons/{lessonId}/videos
    Request: { "youtubeUrl": "https://youtube.com/watch?v=..." }
    Response: LessonVideoDto

    // 영상 목록 조회
    GET /api/lessons/{lessonId}/videos
    Response: List<LessonVideoDto>

    // 영상 순서 변경
    PUT /api/lessons/{lessonId}/videos/{videoId}/order
    Request: { "orderIndex": 2 }
    Response: LessonVideoDto

    // 영상 삭제
    DELETE /api/lessons/{lessonId}/videos/{videoId}
    Response: void
}
```

**학생용 (StudentVideoController):**

```java
@RestController
@RequestMapping("/api/students/{studentId}/videos")
@RequiredArgsConstructor
public class StudentVideoController {

    // 학생의 반 전체 수업 영상 목록
    GET /api/students/{studentId}/videos
    Response: List<StudentLessonVideosDto>

    // StudentLessonVideosDto {
    //   lessonId, lessonDate, className,
    //   videos: List<LessonVideoDto>
    // }
}
```

### 2.3 DTO 설계

```java
@Data
@Builder
public class LessonVideoDto {
    private Long id;
    private Long lessonId;
    private String youtubeUrl;
    private String youtubeVideoId;
    private String title;
    private String thumbnailUrl;
    private String duration;
    private Integer orderIndex;
    private LocalDateTime createdAt;

    public static LessonVideoDto from(LessonVideo video) {
        return LessonVideoDto.builder()
                .id(video.getId())
                .lessonId(video.getLesson().getId())
                .youtubeUrl(video.getYoutubeUrl())
                .youtubeVideoId(video.getYoutubeVideoId())
                .title(video.getTitle())
                .thumbnailUrl(video.getThumbnailUrl())
                .duration(video.getDuration())
                .orderIndex(video.getOrderIndex())
                .createdAt(video.getCreatedAt())
                .build();
    }
}

@Data
@Builder
public class StudentLessonVideosDto {
    private Long lessonId;
    private String lessonDate;
    private String className;
    private List<LessonVideoDto> videos;
}
```

### 2.4 서비스 로직

**LessonVideoService:**

```java
@Service
@Transactional
@RequiredArgsConstructor
public class LessonVideoService {
    private final LessonVideoRepository lessonVideoRepository;
    private final LessonRepository lessonRepository;
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
        int nextOrder = lessonVideoRepository.findByLessonId(lessonId).size() + 1;

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
     * 영상 순서 변경
     */
    public LessonVideoDto updateOrder(Long lessonId, Long videoId, int newOrderIndex) {
        // 1. 해당 영상 찾기
        LessonVideo video = lessonVideoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        // 2. 같은 수업의 모든 영상 조회
        List<LessonVideo> allVideos = lessonVideoRepository.findByLessonId(lessonId);

        // 3. 순서 재배치 로직
        int oldIndex = video.getOrderIndex();
        if (oldIndex == newOrderIndex) {
            return LessonVideoDto.from(video);
        }

        // 위로 이동: oldIndex -> newOrderIndex (감소)
        if (newOrderIndex < oldIndex) {
            for (LessonVideo v : allVideos) {
                if (v.getOrderIndex() >= newOrderIndex && v.getOrderIndex() < oldIndex) {
                    v.setOrderIndex(v.getOrderIndex() + 1);
                }
            }
        }
        // 아래로 이동: oldIndex -> newOrderIndex (증가)
        else {
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
     * 학생용: 자기 반의 모든 수업 영상 조회
     */
    @Transactional(readOnly = true)
    public List<StudentLessonVideosDto> getStudentVideos(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        Long classId = student.getAcademyClass().getId();

        // 해당 반의 모든 수업 조회 (최신순)
        List<Lesson> lessons = lessonRepository
                .findByAcademyClassIdOrderByLessonDateDesc(classId);

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
```

---

## 3. Frontend 설계

### 3.1 선생님 화면 (LessonDetailView.vue 수정)

기존 수업 상세 페이지에 영상 관리 섹션 추가:

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { lessonVideoAPI, type LessonVideo } from '../api/client'

const lessonId = computed(() => Number(route.params.id))
const videos = ref<LessonVideo[]>([])
const addVideoDialogVisible = ref(false)
const youtubeUrl = ref('')
const previewVideo = ref<any>(null)

const fetchVideos = async () => {
  const response = await lessonVideoAPI.getVideos(lessonId.value)
  videos.value = response.data
}

const addVideo = async () => {
  await lessonVideoAPI.addVideo(lessonId.value, { youtubeUrl: youtubeUrl.value })
  ElMessage.success('영상이 추가되었습니다')
  addVideoDialogVisible.value = false
  youtubeUrl.value = ''
  fetchVideos()
}

const moveUp = async (video: LessonVideo) => {
  await lessonVideoAPI.updateOrder(lessonId.value, video.id, video.orderIndex - 1)
  fetchVideos()
}

const moveDown = async (video: LessonVideo) => {
  await lessonVideoAPI.updateOrder(lessonId.value, video.id, video.orderIndex + 1)
  fetchVideos()
}

const deleteVideo = async (video: LessonVideo) => {
  await ElMessageBox.confirm('영상을 삭제하시겠습니까?', '확인')
  await lessonVideoAPI.deleteVideo(lessonId.value, video.id)
  ElMessage.success('영상이 삭제되었습니다')
  fetchVideos()
}

const formatDuration = (duration: string) => {
  // ISO 8601 duration (PT15M33S) -> "15:33"
  const match = duration.match(/PT(\d+M)?(\d+S)?/)
  if (!match) return duration

  const minutes = match[1] ? parseInt(match[1]) : 0
  const seconds = match[2] ? parseInt(match[2]) : 0
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

onMounted(() => {
  fetchVideos()
})
</script>

<template>
  <!-- 기존 수업 상세 내용 -->

  <!-- 영상 관리 섹션 -->
  <el-card shadow="never" style="margin-top: 24px">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <h3 style="margin: 0; font-size: 18px; font-weight: 600">
          <el-icon style="margin-right: 8px"><VideoPlay /></el-icon>
          수업 영상
        </h3>
        <el-button type="primary" @click="addVideoDialogVisible = true">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>
          영상 추가
        </el-button>
      </div>
    </template>

    <!-- 영상 목록 -->
    <div v-if="videos.length > 0">
      <el-card
        v-for="(video, index) in videos"
        :key="video.id"
        style="margin-bottom: 16px"
      >
        <div style="display: flex; gap: 16px; align-items: center">
          <!-- 썸네일 -->
          <img
            :src="video.thumbnailUrl"
            style="width: 160px; height: 90px; object-fit: cover; border-radius: 4px"
          />

          <!-- 영상 정보 -->
          <div style="flex: 1">
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px">
              <el-tag type="info" size="small">{{ index + 1 }}부</el-tag>
              <h4 style="margin: 0; font-size: 16px">{{ video.title }}</h4>
            </div>
            <p style="margin: 0; color: #909399; font-size: 14px">
              재생시간: {{ formatDuration(video.duration) }}
            </p>
          </div>

          <!-- 작업 버튼 -->
          <div style="display: flex; gap: 8px">
            <el-tooltip content="위로 이동" placement="top">
              <el-button
                size="small"
                circle
                @click="moveUp(video)"
                :disabled="index === 0"
              >
                <el-icon><Top /></el-icon>
              </el-button>
            </el-tooltip>

            <el-tooltip content="아래로 이동" placement="top">
              <el-button
                size="small"
                circle
                @click="moveDown(video)"
                :disabled="index === videos.length - 1"
              >
                <el-icon><Bottom /></el-icon>
              </el-button>
            </el-tooltip>

            <el-tooltip content="삭제" placement="top">
              <el-button
                size="small"
                type="danger"
                circle
                @click="deleteVideo(video)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
        </div>
      </el-card>
    </div>

    <el-empty v-else description="등록된 영상이 없습니다" />
  </el-card>

  <!-- 영상 추가 다이얼로그 -->
  <el-dialog
    v-model="addVideoDialogVisible"
    title="영상 추가"
    width="500px"
  >
    <el-form label-width="100px">
      <el-form-item label="YouTube URL" required>
        <el-input
          v-model="youtubeUrl"
          placeholder="https://www.youtube.com/watch?v=..."
          clearable
        />
        <div style="margin-top: 8px; font-size: 12px; color: #909399">
          YouTube 영상 URL을 입력하면 제목과 썸네일이 자동으로 가져와집니다
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="addVideoDialogVisible = false">취소</el-button>
      <el-button
        type="primary"
        @click="addVideo"
        :disabled="!youtubeUrl"
      >
        추가
      </el-button>
    </template>
  </el-dialog>
</template>
```

### 3.2 학생 화면 (새로운 StudentVideosView.vue)

```vue
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { studentVideoAPI, type StudentLessonVideos } from '../api/client'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
const lessonsWithVideos = ref<StudentLessonVideos[]>([])
const loading = ref(false)
const playDialogVisible = ref(false)
const selectedVideo = ref<any>(null)

const embedUrl = computed(() => {
  if (!selectedVideo.value) return ''
  return `https://www.youtube.com/embed/${selectedVideo.value.youtubeVideoId}`
})

const fetchVideos = async () => {
  loading.value = true
  try {
    const response = await studentVideoAPI.getVideos(authStore.studentId)
    lessonsWithVideos.value = response.data
  } catch (error) {
    ElMessage.error('영상 목록을 불러오는데 실패했습니다')
  } finally {
    loading.value = false
  }
}

const playVideo = (video: any) => {
  selectedVideo.value = video
  playDialogVisible.value = true
}

const formatDate = (dateStr: string) => {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

const formatDuration = (duration: string) => {
  const match = duration.match(/PT(\d+M)?(\d+S)?/)
  if (!match) return duration

  const minutes = match[1] ? parseInt(match[1]) : 0
  const seconds = match[2] ? parseInt(match[2]) : 0
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

onMounted(() => {
  fetchVideos()
})
</script>

<template>
  <div class="student-view">
    <el-card shadow="never">
      <template #header>
        <div>
          <h1 style="margin: 0; font-size: 28px; font-weight: 600; color: #303133">
            <el-icon size="32" color="#409eff" style="margin-right: 12px">
              <VideoPlay />
            </el-icon>
            수업 다시보기
          </h1>
          <p style="margin: 8px 0 0; color: #909399">
            지난 수업 영상을 다시 볼 수 있습니다
          </p>
        </div>
      </template>

      <div v-loading="loading">
        <!-- 수업별로 그룹화된 영상 목록 -->
        <div
          v-for="lesson in lessonsWithVideos"
          :key="lesson.lessonId"
          style="margin-bottom: 40px"
        >
          <div style="margin-bottom: 16px; padding-bottom: 8px; border-bottom: 2px solid #e4e7ed">
            <h3 style="margin: 0; font-size: 20px; font-weight: 600">
              {{ formatDate(lesson.lessonDate) }}
            </h3>
            <p style="margin: 4px 0 0; color: #909399">
              {{ lesson.className }}
            </p>
          </div>

          <el-row :gutter="16">
            <el-col
              :xs="24"
              :sm="12"
              :md="8"
              :lg="6"
              v-for="video in lesson.videos"
              :key="video.id"
              style="margin-bottom: 16px"
            >
              <el-card
                shadow="hover"
                @click="playVideo(video)"
                style="cursor: pointer; height: 100%"
              >
                <!-- 썸네일 -->
                <div style="position: relative; padding-top: 56.25%; background: #000">
                  <img
                    :src="video.thumbnailUrl"
                    style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: cover"
                  />

                  <!-- 재생 아이콘 오버레이 -->
                  <div style="
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    background: rgba(0,0,0,0.7);
                    width: 60px;
                    height: 60px;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                  ">
                    <el-icon color="#fff" size="32">
                      <VideoPlay />
                    </el-icon>
                  </div>

                  <!-- 재생 시간 -->
                  <div style="
                    position: absolute;
                    bottom: 8px;
                    right: 8px;
                    background: rgba(0,0,0,0.8);
                    color: white;
                    padding: 2px 6px;
                    border-radius: 4px;
                    font-size: 12px;
                  ">
                    {{ formatDuration(video.duration) }}
                  </div>
                </div>

                <!-- 영상 제목 -->
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
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>

        <el-empty
          v-if="!loading && lessonsWithVideos.length === 0"
          description="등록된 영상이 없습니다"
        />
      </div>
    </el-card>

    <!-- 영상 재생 다이얼로그 -->
    <el-dialog
      v-model="playDialogVisible"
      :title="selectedVideo?.title"
      width="90%"
      :close-on-click-modal="true"
    >
      <div style="position: relative; padding-top: 56.25%; background: #000">
        <iframe
          v-if="playDialogVisible && selectedVideo"
          :src="embedUrl"
          style="
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%
          "
          frameborder="0"
          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
          allowfullscreen
        />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.student-view {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}
</style>
```

### 3.3 라우터 설정

```javascript
// frontend/src/router/index.ts

// 학생용 라우트 추가
{
  path: '/student/videos',
  name: 'StudentVideos',
  component: () => import('../views/StudentVideosView.vue'),
  meta: { requiresAuth: true, role: 'student' }
}
```

### 3.4 API Client 설정

```typescript
// frontend/src/api/client.ts

export interface LessonVideo {
  id: number
  lessonId: number
  youtubeUrl: string
  youtubeVideoId: string
  title: string
  thumbnailUrl: string
  duration: string
  orderIndex: number
  createdAt: string
}

export interface StudentLessonVideos {
  lessonId: number
  lessonDate: string
  className: string
  videos: LessonVideo[]
}

export const lessonVideoAPI = {
  getVideos: (lessonId: number) =>
    client.get<LessonVideo[]>(`/api/lessons/${lessonId}/videos`),

  addVideo: (lessonId: number, data: { youtubeUrl: string }) =>
    client.post<LessonVideo>(`/api/lessons/${lessonId}/videos`, data),

  updateOrder: (lessonId: number, videoId: number, orderIndex: number) =>
    client.put<LessonVideo>(`/api/lessons/${lessonId}/videos/${videoId}/order`, { orderIndex }),

  deleteVideo: (lessonId: number, videoId: number) =>
    client.delete(`/api/lessons/${lessonId}/videos/${videoId}`)
}

export const studentVideoAPI = {
  getVideos: (studentId: number) =>
    client.get<StudentLessonVideos[]>(`/api/students/${studentId}/videos`)
}
```

---

## 4. 구현 순서

1. **Backend - YouTube 서비스 구현**
   - YouTubeService 클래스 생성
   - application.properties에 YouTube API 키 설정
   - URL 파싱 및 API 호출 로직 구현

2. **Backend - 데이터베이스**
   - LessonVideo 엔티티 생성
   - LessonVideoRepository 생성
   - Lesson 엔티티에 videos 필드 추가
   - 마이그레이션 스크립트 작성

3. **Backend - API 구현**
   - LessonVideoController 생성
   - StudentVideoController 생성
   - LessonVideoService 비즈니스 로직 구현
   - DTO 클래스들 생성

4. **Frontend - 선생님 화면**
   - LessonDetailView.vue 수정
   - 영상 관리 섹션 추가
   - API 연동

5. **Frontend - 학생 화면**
   - StudentVideosView.vue 신규 생성
   - 라우터에 경로 추가
   - 학생 네비게이션 메뉴에 "다시보기" 추가

6. **테스트 및 배포**
   - YouTube API 호출 테스트
   - 영상 추가/삭제/순서변경 테스트
   - 권한 검증 (학생이 다른 반 영상 접근 불가)

---

## 5. 추가 고려사항

### 5.1 YouTube URL 파싱

지원할 URL 형식:
- `https://www.youtube.com/watch?v=VIDEO_ID`
- `https://youtu.be/VIDEO_ID`
- `https://www.youtube.com/embed/VIDEO_ID`

정규식으로 video ID 추출 후 유효성 검증

### 5.2 에러 처리

**유효하지 않은 YouTube URL:**
- 프론트엔드에서 URL 형식 검증
- 백엔드에서 video ID 추출 실패 시 400 Bad Request

**YouTube API 호출 실패:**
- Rate limit 초과: 사용자에게 안내 메시지
- 비공개/삭제된 영상: "영상을 찾을 수 없습니다" 에러
- API 키 오류: 서버 로그 기록 및 관리자 알림

**권한 에러:**
- 학생이 다른 반 영상 접근 시도: 403 Forbidden
- 선생님이 다른 학원 수업에 영상 추가 시도: 403 Forbidden

### 5.3 성능 최적화

**YouTube API 할당량 관리:**
- 하루 10,000 units 제한
- 영상 추가 시에만 API 호출 (1회당 1 unit)
- 썸네일 URL을 DB에 저장하여 재호출 방지

**데이터베이스 인덱싱:**
```sql
CREATE INDEX idx_lesson_videos_lesson_id ON lesson_videos(lesson_id);
CREATE INDEX idx_lesson_videos_order ON lesson_videos(lesson_id, order_index);
```

**페이지네이션:**
- 학생용 영상 목록이 많아질 경우 무한 스크롤 또는 페이지네이션 추가

### 5.4 보안

**YouTube API 키 관리:**
```properties
# application.properties
youtube.api.key=${YOUTUBE_API_KEY:}
```
환경변수로 관리하고 git에 커밋하지 않음

**학생 권한 검증:**
```java
// StudentVideoService
public List<StudentLessonVideosDto> getStudentVideos(Long studentId) {
    Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));

    // 학생이 속한 반의 수업만 필터링
    Long classId = student.getAcademyClass().getId();

    List<Lesson> lessons = lessonRepository
            .findByAcademyClassIdOrderByLessonDateDesc(classId);
    // ...
}
```

### 5.5 YouTube Data API v3 설정

1. Google Cloud Console에서 프로젝트 생성
2. YouTube Data API v3 활성화
3. API 키 발급
4. 할당량 모니터링 설정

**API 응답 예시:**
```json
{
  "items": [
    {
      "id": "VIDEO_ID",
      "snippet": {
        "title": "영상 제목",
        "thumbnails": {
          "high": {
            "url": "https://i.ytimg.com/vi/VIDEO_ID/hqdefault.jpg"
          }
        }
      },
      "contentDetails": {
        "duration": "PT15M33S"
      }
    }
  ]
}
```

### 5.6 재생 시간 포맷팅

ISO 8601 duration format (PT15M33S) → "15:33" 변환:
```javascript
function formatDuration(duration) {
  const match = duration.match(/PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?/);
  if (!match) return duration;

  const hours = parseInt(match[1] || 0);
  const minutes = parseInt(match[2] || 0);
  const seconds = parseInt(match[3] || 0);

  if (hours > 0) {
    return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  }
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}
```

---

## 6. 데이터베이스 마이그레이션

```sql
-- lesson_videos 테이블 생성
CREATE TABLE lesson_videos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lesson_id BIGINT NOT NULL,
    youtube_url VARCHAR(500) NOT NULL,
    youtube_video_id VARCHAR(50) NOT NULL,
    title VARCHAR(200),
    thumbnail_url VARCHAR(500),
    duration VARCHAR(20),
    order_index INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE,
    INDEX idx_lesson_id (lesson_id),
    INDEX idx_order (lesson_id, order_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 7. 향후 개선 사항 (Optional)

1. **재생 기록 추적**
   - 학생별 영상 시청 완료 여부 저장
   - 마지막 시청 위치 기억 (YouTube Player API 활용)

2. **댓글/질문 기능**
   - 영상별 학생 질문 및 선생님 답변

3. **영상 검색**
   - 제목으로 영상 검색 기능

4. **다운로드 금지**
   - YouTube 임베드 설정으로 다운로드 제한

5. **통계**
   - 영상별 조회수, 평균 시청 시간 등

---

## 완료 체크리스트

### Backend
- [ ] YouTubeService 구현
- [ ] LessonVideo 엔티티 생성
- [ ] LessonVideoRepository 생성
- [ ] LessonVideoController 구현
- [ ] StudentVideoController 구현
- [ ] LessonVideoService 구현
- [ ] DTO 클래스 생성
- [ ] 마이그레이션 스크립트 작성
- [ ] YouTube API 키 설정

### Frontend
- [ ] LessonDetailView.vue 수정 (영상 관리 섹션)
- [ ] StudentVideosView.vue 생성
- [ ] API client 함수 추가
- [ ] 라우터 설정
- [ ] 학생 네비게이션 메뉴 추가

### 테스트
- [ ] YouTube URL 파싱 테스트
- [ ] YouTube API 호출 테스트
- [ ] 영상 추가 기능 테스트
- [ ] 영상 순서 변경 테스트
- [ ] 영상 삭제 테스트
- [ ] 학생 권한 검증 테스트
- [ ] 영상 재생 테스트

### 배포
- [ ] YouTube API 키 환경변수 설정
- [ ] 데이터베이스 마이그레이션 실행
- [ ] 프로덕션 빌드 및 배포
