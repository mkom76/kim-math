<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { studentVideoAPI, authAPI, videoProgressAPI, type StudentLessonVideos, type VideoProgress } from '../api/client'
import { ElMessage } from 'element-plus'
import { VideoPlay, CircleCheck } from '@element-plus/icons-vue'

const lessonsWithVideos = ref<StudentLessonVideos[]>([])
const loading = ref(false)
const playDialogVisible = ref(false)
const selectedVideo = ref<any>(null)
const progressMap = ref<Map<number, VideoProgress>>(new Map())
const player = ref<any>(null)
const progressUpdateInterval = ref<any>(null)
const lastCurrentTime = ref(0)
const currentStudentId = ref<number | null>(null)

const INTERVAL_SEC = 30
const MAX_ALLOWED = 65 // 30 × 2.0 speed + 5 tolerance

const embedUrl = computed(() => {
  if (!selectedVideo.value) return ''
  return `https://www.youtube.com/embed/${selectedVideo.value.youtubeVideoId}?enablejsapi=1`
})

// Load YouTube iframe API
const loadYouTubeAPI = () => {
  if (window.YT) return Promise.resolve()

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
    console.log('Skip detected, not updating progress')
    lastCurrentTime.value = currentTime
    return
  }

  lastCurrentTime.value = currentTime

  try {
    const response = await videoProgressAPI.updateProgress(currentStudentId.value, videoId, {
      currentTime,
      duration
    })

    // Update local progress map
    progressMap.value.set(videoId, response.data)
  } catch (error) {
    // Fail silently - don't interrupt user
    console.error('Failed to update progress:', error)
  }
}

const onPlayerReady = (event: any) => {
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
  } catch (error) {
    ElMessage.error('영상 목록을 불러오는데 실패했습니다')
  } finally {
    loading.value = false
  }
}

const playVideo = async (video: any) => {
  selectedVideo.value = video
  playDialogVisible.value = true

  // Wait for dialog to render, then initialize YouTube player
  await new Promise(resolve => setTimeout(resolve, 100))

  await loadYouTubeAPI()

  // Initialize YouTube player
  if (window.YT && window.YT.Player) {
    const iframe = document.querySelector('iframe')
    if (iframe) {
      player.value = new window.YT.Player(iframe, {
        events: {
          onReady: onPlayerReady
        }
      })
    }
  }
}

const closeDialog = () => {
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
  return new Date(dateStr).toLocaleDateString('ko-KR', {
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
                <div style="position: relative; padding-top: 56.25%; background: #000">
                  <img
                    :src="video.thumbnailUrl"
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
          description="등록된 영상이 없습니다"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="playDialogVisible"
      :title="selectedVideo?.title"
      width="90%"
      :close-on-click-modal="true"
      @close="closeDialog"
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
