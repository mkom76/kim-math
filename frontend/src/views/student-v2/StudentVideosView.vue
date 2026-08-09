<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { studentVideoAPI, authAPI, videoProgressAPI, type LessonVideo, type StudentLessonVideos, type VideoProgress } from '@/api/client'
import { ElMessage } from 'element-plus'
import { VideoPlay, CircleCheck } from '@element-plus/icons-vue'
import { useBreakpoint } from '@/composables/useBreakpoint'

const lessonsWithVideos = ref<StudentLessonVideos[]>([])
const loading = ref(false)
const playDialogVisible = ref(false)
const selectedVideo = ref<LessonVideo | null>(null)
const progressMap = ref<Map<number, VideoProgress>>(new Map())
const player = ref<YT.Player | null>(null)
const progressUpdateInterval = ref<ReturnType<typeof setInterval> | null>(null)
const videoFrame = ref<HTMLIFrameElement | null>(null)
const lastCurrentTime = ref(0)
const currentStudentId = ref<number | null>(null)
const { isMobile } = useBreakpoint()

const INTERVAL_SEC = 30
const MAX_ALLOWED = 65 // 30 × 2.0 speed + 5 tolerance

const embedUrl = computed(() => {
  if (!selectedVideo.value) return ''
  return `https://www.youtube-nocookie.com/embed/${selectedVideo.value.youtubeVideoId}?enablejsapi=1&playsinline=1`
})

// Load YouTube iframe API
const loadYouTubeAPI = () => {
  if (window.YT?.Player) return Promise.resolve()

  return new Promise((resolve) => {
    const tag = document.createElement('script')
    tag.src = 'https://www.youtube.com/iframe_api'
    const firstScriptTag = document.getElementsByTagName('script')[0]
    if (firstScriptTag && firstScriptTag.parentNode) {
      firstScriptTag.parentNode.insertBefore(tag, firstScriptTag)
    } else {
      document.head.appendChild(tag)
    }

    window.onYouTubeIframeAPIReady = () => {
      resolve(undefined)
    }
  })
}

const fetchProgress = async () => {
  if (!currentStudentId.value) return

  try {
    const response = await videoProgressAPI.getStudentProgress(currentStudentId.value)
    const progressData = response.data

    progressMap.value.clear()
    progressData.forEach((progress: VideoProgress) => {
      progressMap.value.set(progress.videoId, progress)
    })
  } catch (error) {
    // Fail silently - don't interrupt user
    console.error('Failed to fetch progress:', error)
  }
}

const updateProgress = async (videoId: number, currentTime: number, duration: number) => {
  if (!currentStudentId.value) return

  // Skip detection: if time difference > 65 seconds, don't update progress
  const timeDiff = currentTime - lastCurrentTime.value
  if (timeDiff > MAX_ALLOWED) {
    lastCurrentTime.value = currentTime
    return
  }

  lastCurrentTime.value = currentTime

  try {
    const response = await videoProgressAPI.updateProgress(currentStudentId.value, videoId, {
      watchedTime: Math.floor(currentTime),
      duration: Math.floor(duration)
    })

    // Update local progress map
    progressMap.value.set(videoId, response.data)
  } catch (error) {
    // Fail silently - don't interrupt user
    console.error('Failed to update progress:', error)
  }
}

const onPlayerReady = (event: { target: YT.Player }) => {
  player.value = event.target
  lastCurrentTime.value = 0

  // Start 30-second interval
  progressUpdateInterval.value = setInterval(() => {
    if (player.value && selectedVideo.value) {
      const currentTime = player.value.getCurrentTime()
      const duration = player.value.getDuration()

      if (currentTime > 0 && duration > 0) {
        updateProgress(selectedVideo.value.id, currentTime, duration)
      }
    }
  }, INTERVAL_SEC * 1000)
}

const fetchVideos = async () => {
  loading.value = true
  try {
    // Get current user from auth API
    const userResponse = await authAPI.getCurrentUser()
    const studentId = userResponse.data.userId

    if (!studentId) {
      ElMessage.error('로그인이 필요합니다')
      return
    }

    currentStudentId.value = studentId

    const response = await studentVideoAPI.getVideos(studentId)
    lessonsWithVideos.value = response.data

    // Fetch progress data
    await fetchProgress()
  } catch {
    ElMessage.error('영상 목록을 불러오는데 실패했습니다')
  } finally {
    loading.value = false
  }
}

const playVideo = async (video: LessonVideo) => {
  cleanupPlayer()
  selectedVideo.value = video
  playDialogVisible.value = true

  // Wait for dialog to render, then initialize YouTube player
  await nextTick()
  await loadYouTubeAPI()
  await nextTick()

  // Initialize YouTube player
  if (window.YT?.Player) {
    if (videoFrame.value) {
      player.value = new window.YT.Player(videoFrame.value, {
        events: {
          onReady: onPlayerReady
        }
      })
    }
  }
}

const cleanupPlayer = () => {
  // Cleanup intervals
  if (progressUpdateInterval.value) {
    clearInterval(progressUpdateInterval.value)
    progressUpdateInterval.value = null
  }

  // Destroy player
  if (player.value && player.value.destroy) {
    player.value.destroy()
  }
  player.value = null
  lastCurrentTime.value = 0
}

const closeDialog = () => {
  cleanupPlayer()
}

// Helper functions
const getProgress = (videoId: number): number => {
  const progress = progressMap.value.get(videoId)
  return progress ? progress.progressPercent : 0
}

const getLastWatched = (videoId: number): string | null => {
  const progress = progressMap.value.get(videoId)
  return progress ? progress.lastWatchedAt : null
}

const formatLastWatched = (lastWatchedAt: string): string => {
  const date = new Date(lastWatchedAt)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 60) {
    return `${diffMins}분 전`
  } else if (diffHours < 24) {
    return `${diffHours}시간 전`
  } else if (diffDays < 7) {
    return `${diffDays}일 전`
  } else {
    return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
  }
}

const formatDate = (dateStr: string) => {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateStr)
  const date = match
    ? new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
    : new Date(dateStr)
  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

const formatDuration = (duration: string) => {
  const match = duration.match(/PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?/)
  if (!match) return duration

  const hours = parseInt(match[1] || '0')
  const minutes = parseInt(match[2] || '0')
  const seconds = parseInt(match[3] || '0')

  if (hours > 0) {
    return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
  }
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

onMounted(() => {
  fetchVideos()
})

onUnmounted(() => {
  cleanupPlayer()
})
</script>

<template>
  <div class="student-page student-page--wide videos-page">
    <el-card class="videos-shell" shadow="never">
      <template #header>
        <div class="student-page__header">
          <div>
            <p class="student-page__eyebrow">LESSON VIDEO</p>
            <h1 class="student-page__title">수업 다시보기</h1>
            <p class="student-page__subtitle">놓친 부분부터 이어서 학습하세요.</p>
          </div>
          <span class="videos-header-icon"><el-icon><VideoPlay /></el-icon></span>
        </div>
      </template>

      <div v-loading="loading">
        <div
          v-for="lesson in lessonsWithVideos"
          :key="lesson.lessonId"
          class="lesson-video-group"
        >
          <div class="lesson-video-group__header">
            <h2>
              {{ formatDate(lesson.lessonDate) }}
            </h2>
            <p>
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
                class="video-card"
                shadow="hover"
                @click="playVideo(video)"
                @keydown.enter="playVideo(video)"
                @keydown.space.prevent="playVideo(video)"
                role="button"
                tabindex="0"
              >
                <div class="video-card__thumbnail">
                  <img
                    :src="video.thumbnailUrl"
                    :alt="`${video.title} 썸네일`"
                    style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: cover"
                  />

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

                <div class="video-card__body">
                  <el-tag type="info" size="small" style="margin-bottom: 8px">
                    {{ video.orderIndex }}부
                  </el-tag>
                  <h3 style="
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
                  </h3>

                  <!-- Progress Bar -->
                  <div style="margin-top: 12px">
                    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px">
                      <div style="display: flex; align-items: center; gap: 4px">
                        <el-icon v-if="getProgress(video.id) === 100" color="#67c23a" size="16">
                          <CircleCheck />
                        </el-icon>
                        <span style="font-size: 12px; color: #909399">
                          {{ getProgress(video.id) === 100 ? '완료' : getProgress(video.id) > 0 ? `${getProgress(video.id)}%` : '시청 안함' }}
                        </span>
                      </div>
                      <span v-if="getLastWatched(video.id)" style="font-size: 11px; color: #c0c4cc">
                        {{ formatLastWatched(getLastWatched(video.id)!) }}
                      </span>
                    </div>
                    <el-progress
                      :percentage="getProgress(video.id)"
                      :stroke-width="4"
                      :show-text="false"
                      :color="getProgress(video.id) === 100 ? '#67c23a' : '#409eff'"
                    />
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>

        <el-empty
          v-if="!loading && lessonsWithVideos.length === 0"
          description="아직 등록된 수업 영상이 없어요"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="playDialogVisible"
      :title="selectedVideo?.title"
      :width="isMobile ? '100%' : '90%'"
      :fullscreen="isMobile"
      :close-on-click-modal="true"
      @close="closeDialog"
    >
      <div class="video-player-frame">
        <iframe
          v-if="playDialogVisible && selectedVideo"
          ref="videoFrame"
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
.videos-page { padding-bottom: 24px; }
.videos-shell { border: 0; background: transparent; box-shadow: none; }
.videos-shell :deep(> .el-card__header) { padding: 0 0 24px; border: 0; }
.videos-shell :deep(> .el-card__body) { padding: 0; }
.videos-header-icon { display: grid; flex: 0 0 48px; height: 48px; place-items: center; border-radius: 15px; color: var(--student-primary); background: var(--student-primary-soft); font-size: 24px; }
.lesson-video-group { margin-bottom: 30px; }
.lesson-video-group:last-child { margin-bottom: 0; }
.lesson-video-group__header { margin-bottom: 14px; }
.lesson-video-group__header h2 { margin: 0; color: var(--student-ink); font-size: 18px; font-weight: 800; letter-spacing: -.3px; }
.lesson-video-group__header p { margin: 4px 0 0; color: var(--student-muted); font-size: 13px; }
.video-card { overflow: hidden; height: 100%; border: 1px solid var(--student-border); border-radius: 18px; background: var(--student-surface); cursor: pointer; box-shadow: var(--student-shadow-sm); transition: transform .18s ease, box-shadow .18s ease; }
.video-card:hover { transform: translateY(-2px); box-shadow: var(--student-shadow-md); }
.video-card:focus-visible { outline: 3px solid color-mix(in srgb, var(--student-primary) 30%, transparent); outline-offset: 3px; }
.video-card :deep(.el-card__body) { padding: 0; }
.video-card__thumbnail { position: relative; padding-top: 56.25%; overflow: hidden; background: #111827; }
.video-card__thumbnail::after { position: absolute; inset: 0; content: ''; background: linear-gradient(180deg, transparent 50%, rgba(5, 11, 24, .35)); }
.video-card__body { padding: 15px !important; }
.video-card__body > h3 { display: -webkit-box !important; overflow: hidden; min-height: 42px; margin: 0 !important; color: var(--student-ink); font-size: 15px !important; font-weight: 750 !important; line-height: 1.4; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.video-player-frame { position: relative; padding-top: 56.25%; overflow: hidden; border-radius: 14px; background: #000; }
@media (max-width: 767px) {
  .videos-shell :deep(.el-row) { margin-right: 0 !important; margin-left: 0 !important; }
  .videos-shell :deep(.el-col) { padding-right: 0 !important; padding-left: 0 !important; }
  .video-card__body { padding: 15px !important; }
}
</style>
