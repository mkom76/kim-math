<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { studentVideoAPI, type StudentLessonVideos } from '../api/client'
import { ElMessage } from 'element-plus'
import { VideoPlay } from '@element-plus/icons-vue'

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
    // Get studentId from localStorage or auth store
    const studentId = localStorage.getItem('studentId')
    if (!studentId) {
      ElMessage.error('로그인이 필요합니다')
      return
    }

    const response = await studentVideoAPI.getVideos(Number(studentId))
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
