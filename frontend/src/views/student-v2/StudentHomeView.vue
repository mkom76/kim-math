<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  authAPI,
  lessonAPI,
  submissionAPI,
  studentAPI,
  studentHomeworkAPI,
  studentNotificationAPI,
  videoProgressAPI,
} from '@/api/client'
import type {
  AttendanceStats,
  AuthResponse,
  Student,
  StudentHomework,
  Submission,
  VideoProgress,
} from '@/api/client'
import { useStudentUiMode } from '@/composables/useStudentUiMode'
import StudentUiFeedbackDialog from '@/components/student-v2/StudentUiFeedbackDialog.vue'
import StudentPageHeader from '@/components/student-v2/StudentPageHeader.vue'

const router = useRouter()
const { leavePreview } = useStudentUiMode()
const loading = ref(false)
const currentUser = ref<AuthResponse>({})
const studentInfo = ref<Student | null>(null)
const mySubmissions = ref<Submission[]>([])
const homeworks = ref<StudentHomework[]>([])
const attendanceStats = ref<AttendanceStats | null>(null)
const unreadNotificationCount = ref(0)
const feedbackDialogVisible = ref(false)
const videoProgress = ref<VideoProgress[]>([])

const displayName = computed(() => currentUser.value.name || studentInfo.value?.name || '학생')
const attendanceRate = computed(() => attendanceStats.value?.attendanceRate ?? null)
const visibleScores = computed(() =>
  mySubmissions.value.filter((item) => typeof item.totalScore === 'number'),
)
const averageScore = computed(() => {
  if (!visibleScores.value.length) return null
  const total = visibleScores.value.reduce((sum, item) => sum + item.totalScore, 0)
  return Math.round((total / visibleScores.value.length) * 10) / 10
})
const completedHomeworks = computed(() => homeworks.value.filter((item) => item.completion != null))
const averageCompletion = computed(() => {
  if (!completedHomeworks.value.length) return null
  const total = completedHomeworks.value.reduce((sum, item) => sum + (item.completion ?? 0), 0)
  return Math.round(total / completedHomeworks.value.length)
})
const averageVideoProgress = computed(() => {
  if (!videoProgress.value.length) return null
  const total = videoProgress.value.reduce((sum, item) => sum + item.progressPercent, 0)
  return Math.round(total / videoProgress.value.length)
})

const returnToLegacyUi = async () => {
  leavePreview()
  await router.replace('/student/dashboard')
}

const fetchUnreadCount = async () => {
  try {
    const response = await studentNotificationAPI.getUnreadCount()
    unreadNotificationCount.value = response.data.count
  } catch {
    unreadNotificationCount.value = 0
  }
}

const fetchDashboard = async () => {
  loading.value = true
  try {
    const userResponse = await authAPI.getCurrentUser()
    currentUser.value = userResponse.data
    if (!currentUser.value.userId) return

    const studentId = currentUser.value.userId
    const [studentResponse, submissionsResponse, homeworksResponse, videoProgressResponse] =
      await Promise.all([
        studentAPI.getStudent(studentId),
        submissionAPI.getStudentSubmissions(studentId),
        studentHomeworkAPI.getByStudentId(studentId),
        videoProgressAPI
          .getStudentProgress(studentId)
          .catch(() => ({ data: [] as VideoProgress[] })),
      ])
    studentInfo.value = studentResponse.data
    mySubmissions.value = submissionsResponse.data.content || submissionsResponse.data
    homeworks.value = homeworksResponse.data.content || homeworksResponse.data
    videoProgress.value = videoProgressResponse.data

    try {
      attendanceStats.value = await lessonAPI.getAttendanceStats(studentId)
    } catch {
      attendanceStats.value = null
    }
    await fetchUnreadCount()
  } catch (error) {
    console.error('Failed to fetch student dashboard:', error)
    ElMessage.error('학습 정보를 불러오지 못했습니다')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDashboard()
  window.addEventListener('student-notifications-changed', fetchUnreadCount)
})

onBeforeUnmount(() => {
  window.removeEventListener('student-notifications-changed', fetchUnreadCount)
})
</script>

<template>
  <div class="student-page dashboard-page" v-loading="loading">
    <StudentPageHeader
      eyebrow="MY STUDY"
      :title="`${displayName}님, 반가워요`"
      :subtitle="`${studentInfo?.academyName || 'KIM MATH'}${studentInfo?.className ? ` · ${studentInfo.className}` : ''}`"
    >
      <template #meta>
        <p v-if="studentInfo?.school || studentInfo?.grade" class="dashboard-student-meta">
          <span v-if="studentInfo?.id">ID {{ studentInfo.id }}</span>
          <span v-if="studentInfo?.school">{{ studentInfo.school }}</span>
          <span v-if="studentInfo?.grade">{{ studentInfo.grade }}</span>
        </p>
      </template>
      <template #action>
        <div class="dashboard-header__actions">
          <button class="dashboard-preview-exit" @click="returnToLegacyUi">기존 UI</button>
          <button
            class="student-icon-button dashboard-notification-button"
            :aria-label="
              unreadNotificationCount
                ? `알림 ${unreadNotificationCount}개 확인하기`
                : '알림 확인하기'
            "
            @click="router.push('/student/notifications')"
          >
            <el-icon><Bell /></el-icon>
            <span
              v-if="unreadNotificationCount"
              class="dashboard-notification-button__badge"
              aria-hidden="true"
            />
          </button>
          <button
            class="student-icon-button"
            aria-label="설정 열기"
            @click="router.push('/settings')"
          >
            <el-icon><Setting /></el-icon>
          </button>
        </div>
      </template>
    </StudentPageHeader>

    <div class="dashboard-learning-cards" aria-label="학습 바로가기">
      <RouterLink
        to="/student/daily-feedback"
        class="student-hero dashboard-hero dashboard-learning-card"
      >
        <span class="dashboard-hero__content">
          <span class="student-hero__label">오늘의 학습</span>
          <strong class="student-hero__title">수업 피드백을 확인해 볼까요?</strong>
          <span class="student-hero__description"
            >선생님이 남긴 코멘트로 복습할 내용을 확인해 보세요.</span
          >
        </span>
        <span class="dashboard-learning-card__action">
          피드백 확인하기 <el-icon><ArrowRight /></el-icon>
        </span>
      </RouterLink>

      <RouterLink
        to="/student/videos"
        class="student-hero dashboard-hero dashboard-learning-card dashboard-learning-card--video"
      >
        <span class="dashboard-hero__content">
          <span class="student-hero__label">수업 다시보기</span>
          <strong class="student-hero__title">놓친 부분부터 이어서 학습해요</strong>
          <span class="student-hero__description"
            >지난 수업 영상을 원하는 구간부터 다시 확인하세요.</span
          >
        </span>
        <span class="dashboard-learning-card__action">
          영상 확인하기 <el-icon><VideoPlay /></el-icon>
        </span>
      </RouterLink>
    </div>

    <section aria-labelledby="study-status-title">
      <div class="student-section-heading">
        <div>
          <p class="student-section-heading__eyebrow">한눈에 보기</p>
          <h2 id="study-status-title" class="student-section-heading__title">나의 학습 현황</h2>
        </div>
      </div>
      <div class="student-stat-grid dashboard-stat-grid">
        <button
          class="student-stat-card is-interactive"
          @click="router.push('/student/statistics/tests')"
        >
          <span class="student-stat-card__label">평균 점수</span>
          <strong class="student-stat-card__value"
            >{{ averageScore ?? '-' }}<small v-if="averageScore !== null">점</small></strong
          >
          <span class="dashboard-stat-card__action"
            >시험 통계 보기 <el-icon><ArrowRight /></el-icon
          ></span>
        </button>
        <button
          type="button"
          class="student-stat-card is-interactive"
          @click="router.push('/student/statistics/homework')"
        >
          <span class="student-stat-card__label">숙제 완성도</span>
          <strong class="student-stat-card__value"
            >{{ averageCompletion ?? '-'
            }}<small v-if="averageCompletion !== null">%</small></strong
          >
          <span class="dashboard-stat-card__action"
            >숙제 통계 보기 <el-icon><ArrowRight /></el-icon
          ></span>
        </button>
        <button
          type="button"
          class="student-stat-card is-interactive"
          @click="router.push('/student/statistics/attendance')"
        >
          <span class="student-stat-card__label">출석률</span>
          <strong class="student-stat-card__value"
            >{{ attendanceRate ?? '-' }}<small v-if="attendanceRate !== null">%</small></strong
          >
          <span class="dashboard-stat-card__action"
            >출석 통계 보기 <el-icon><ArrowRight /></el-icon
          ></span>
        </button>
        <button
          type="button"
          class="student-stat-card is-interactive"
          @click="router.push('/student/statistics/videos')"
        >
          <span class="student-stat-card__label">영상 학습</span>
          <strong class="student-stat-card__value"
            >{{ averageVideoProgress ?? '-'
            }}<small v-if="averageVideoProgress !== null">%</small></strong
          >
          <span class="dashboard-stat-card__action"
            >영상 통계 보기 <el-icon><ArrowRight /></el-icon
          ></span>
        </button>
      </div>
    </section>

    <section class="dashboard-feedback-prompt" aria-label="새 UI 의견 보내기">
      <div>
        <span class="dashboard-feedback-prompt__icon"
          ><el-icon><ChatDotRound /></el-icon
        ></span>
        <span>
          <strong>새 화면은 어떠셨나요?</strong>
          <small>불편한 점을 알려주시면 더 편하게 다듬을게요.</small>
        </span>
      </div>
      <button type="button" @click="feedbackDialogVisible = true">
        의견 보내기 <el-icon><ArrowRight /></el-icon>
      </button>
    </section>

    <StudentUiFeedbackDialog v-model="feedbackDialogVisible" />
  </div>
</template>

<style scoped>
.dashboard-page {
  display: grid;
  min-width: 0;
  gap: 28px;
}
.dashboard-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.dashboard-student-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 8px 0 0;
}
.dashboard-student-meta span {
  padding: 4px 9px;
  border-radius: 999px;
  color: var(--student-primary-strong);
  background: var(--student-primary-soft);
  font-size: 11px;
  font-weight: 750;
}
.dashboard-preview-exit {
  min-height: 44px;
  padding: 0 13px;
  border: 1px solid rgba(255, 255, 255, 0.55);
  border-radius: 999px;
  color: var(--student-primary);
  background: var(--student-surface);
  box-shadow: var(--student-shadow-soft);
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
  cursor: pointer;
}
.dashboard-notification-button {
  position: relative;
}
.dashboard-notification-button__badge {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 10px;
  height: 10px;
  border: 2px solid var(--student-surface);
  border-radius: 50%;
  background: var(--student-danger);
  pointer-events: none;
}
.dashboard-learning-cards {
  display: grid;
  min-width: 0;
  gap: 14px;
}
.dashboard-hero {
  display: grid;
  min-width: 0;
  gap: 22px;
}
.dashboard-learning-card {
  border: 0;
  text-decoration: none;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
}
.dashboard-learning-card--video {
  background: var(--student-gradient-video);
  box-shadow: 0 18px 36px rgba(18, 84, 207, 0.24);
}
.dashboard-learning-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 22px 42px rgba(36, 87, 214, 0.26);
}
.dashboard-learning-card:active {
  transform: translateY(0) scale(0.995);
}
.dashboard-learning-card:focus-visible {
  outline: 3px solid rgba(36, 87, 214, 0.3);
  outline-offset: 4px;
}
.dashboard-hero__content {
  display: grid;
  min-width: 0;
  gap: 8px;
}
.dashboard-hero__content > * {
  min-width: 0;
  overflow-wrap: anywhere;
}
.dashboard-learning-card .student-hero__title {
  display: block;
  margin: 0;
}
.dashboard-learning-card .student-hero__description {
  position: relative;
  z-index: 1;
  display: block;
  max-width: 520px;
  color: rgba(255, 255, 255, 0.88);
  font-size: 14px;
  font-weight: 550;
  line-height: 1.6;
}
.dashboard-learning-card__action {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  width: fit-content;
  min-width: 190px;
  min-height: 52px;
  padding: 0 18px;
  border-radius: 16px;
  color: var(--student-primary-strong);
  background: var(--student-surface);
  box-shadow: 0 8px 20px rgba(10, 28, 74, 0.2);
  font-size: 15px;
  font-weight: 800;
}
.student-stat-card {
  text-align: left;
}
button.student-stat-card {
  cursor: pointer;
}
.student-stat-card.is-interactive {
  border-color: rgba(36, 87, 214, 0.32);
  box-shadow: 0 7px 20px rgba(36, 87, 214, 0.1);
  transition:
    transform 0.16s ease,
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    background-color 0.16s ease;
}
.student-stat-card.is-interactive:hover {
  border-color: var(--student-primary);
  background: var(--student-surface-hover);
  box-shadow: 0 10px 24px rgba(36, 87, 214, 0.14);
  transform: translateY(-2px);
}
.student-stat-card.is-interactive:active {
  transform: translateY(0) scale(0.985);
}
.student-stat-card__value small {
  margin-left: 3px;
  font-size: 14px;
  font-weight: 700;
}
.dashboard-stat-card__action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 5px;
  margin-top: 7px;
  color: var(--student-primary);
  font-size: 12px;
  font-weight: 800;
}
.dashboard-stat-card__action .el-icon {
  flex: 0 0 auto;
}
.dashboard-feedback-prompt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid var(--student-border);
  border-radius: var(--student-radius-lg);
  background: rgba(255, 255, 255, 0.78);
  box-shadow: var(--student-shadow-soft);
}
.dashboard-feedback-prompt > div {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
}
.dashboard-feedback-prompt__icon {
  display: grid;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 13px;
  color: var(--student-primary);
  background: var(--student-primary-soft);
  font-size: 19px;
}
.dashboard-feedback-prompt > div > span:last-child {
  display: grid;
  min-width: 0;
  gap: 3px;
}
.dashboard-feedback-prompt strong {
  color: var(--student-ink);
  font-size: 14px;
  font-weight: 800;
}
.dashboard-feedback-prompt small {
  color: var(--student-muted);
  font-size: 11px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}
.dashboard-feedback-prompt button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 5px;
  min-height: 42px;
  padding: 0 13px;
  border: 1px solid rgba(36, 87, 214, 0.28);
  border-radius: 13px;
  color: var(--student-primary-strong);
  background: var(--student-surface);
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  transition:
    background-color 0.16s ease,
    border-color 0.16s ease;
}
.dashboard-feedback-prompt button:hover {
  border-color: var(--student-primary);
  background: var(--student-primary-soft);
}
.dashboard-feedback-prompt button:focus-visible {
  outline: 3px solid rgba(36, 87, 214, 0.25);
  outline-offset: 2px;
}
@media (max-width: 420px) {
  .dashboard-page :deep(.student-page__header) {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
  .dashboard-header__actions {
    order: -1;
    align-self: flex-end;
  }
  .dashboard-learning-card__action {
    width: 100%;
    min-width: 0;
  }
  .dashboard-feedback-prompt {
    align-items: stretch;
    flex-direction: column;
  }
  .dashboard-feedback-prompt button {
    justify-content: space-between;
    width: 100%;
  }
}
@media (min-width: 680px) {
  .dashboard-hero {
    grid-template-columns: 1fr auto;
    align-items: end;
  }
}
@media (min-width: 720px) {
  .dashboard-page .dashboard-stat-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
</style>
