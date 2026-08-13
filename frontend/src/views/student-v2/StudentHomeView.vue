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
} from '@/api/client'
import type { AttendanceStats, AuthResponse, Student, StudentHomework, Submission } from '@/api/client'
import { useStudentUiMode } from '@/composables/useStudentUiMode'

const router = useRouter()
const { leavePreview } = useStudentUiMode()
const loading = ref(false)
const currentUser = ref<AuthResponse>({})
const studentInfo = ref<Student | null>(null)
const mySubmissions = ref<Submission[]>([])
const homeworks = ref<StudentHomework[]>([])
const attendanceStats = ref<AttendanceStats | null>(null)
const attendanceDialogVisible = ref(false)
const unreadNotificationCount = ref(0)

const displayName = computed(() => currentUser.value.name || studentInfo.value?.name || '학생')
const attendanceRate = computed(() => attendanceStats.value?.attendanceRate ?? null)
const visibleScores = computed(() => mySubmissions.value.filter(item => typeof item.totalScore === 'number'))
const averageScore = computed(() => {
  if (!visibleScores.value.length) return null
  const total = visibleScores.value.reduce((sum, item) => sum + item.totalScore, 0)
  return Math.round((total / visibleScores.value.length) * 10) / 10
})
const completedHomeworks = computed(() => homeworks.value.filter(item => item.completion != null))
const averageCompletion = computed(() => {
  if (!completedHomeworks.value.length) return null
  const total = completedHomeworks.value.reduce((sum, item) => sum + (item.completion ?? 0), 0)
  return Math.round(total / completedHomeworks.value.length)
})
const recentHomeworks = computed(() => completedHomeworks.value.slice(0, 5))

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
    const [studentResponse, submissionsResponse, homeworksResponse] = await Promise.all([
      studentAPI.getStudent(studentId),
      submissionAPI.getStudentSubmissions(studentId),
      studentHomeworkAPI.getByStudentId(studentId),
    ])
    studentInfo.value = studentResponse.data
    mySubmissions.value = submissionsResponse.data.content || submissionsResponse.data
    homeworks.value = homeworksResponse.data.content || homeworksResponse.data

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
    <header class="student-page__header">
      <div>
        <p class="student-page__eyebrow">MY STUDY</p>
        <h1 class="student-page__title">{{ displayName }}님, 반가워요</h1>
        <p class="student-page__subtitle">
          {{ studentInfo?.academyName || 'KIM MATH' }}<span v-if="studentInfo?.className"> · {{ studentInfo.className }}</span>
        </p>
        <p v-if="studentInfo?.school || studentInfo?.grade" class="dashboard-student-meta">
          <span v-if="studentInfo?.school">{{ studentInfo.school }}</span>
          <span v-if="studentInfo?.grade">{{ studentInfo.grade }}</span>
        </p>
      </div>
      <div class="dashboard-header__actions">
        <button class="dashboard-preview-exit" @click="returnToLegacyUi">기존 UI</button>
        <button
          class="student-icon-button dashboard-notification-button"
          :aria-label="unreadNotificationCount ? `알림 ${unreadNotificationCount}개 확인하기` : '알림 확인하기'"
          @click="router.push('/student/notifications')"
        >
          <el-icon><Bell /></el-icon>
          <span
            v-if="unreadNotificationCount"
            class="dashboard-notification-button__badge"
            role="status"
            aria-atomic="true"
          >{{ unreadNotificationCount > 99 ? '99+' : unreadNotificationCount }}</span>
        </button>
        <button class="student-icon-button" aria-label="설정 열기" @click="router.push('/settings')">
          <el-icon><Setting /></el-icon>
        </button>
      </div>
    </header>

    <section class="student-hero dashboard-hero">
      <div class="dashboard-hero__content">
        <span class="student-hero__label">오늘의 학습</span>
        <h2 class="student-hero__title">수업 피드백을 확인해 볼까요?</h2>
        <p class="student-hero__description">선생님이 남긴 코멘트로 복습할 내용을 확인해 보세요.</p>
      </div>
      <el-button class="dashboard-hero__action" @click="router.push('/student/daily-feedback')">
        피드백 확인하기 <el-icon><ArrowRight /></el-icon>
      </el-button>
    </section>

    <section aria-labelledby="study-status-title">
      <div class="student-section-heading">
        <div>
          <p class="student-section-heading__eyebrow">한눈에 보기</p>
          <h2 id="study-status-title" class="student-section-heading__title">나의 학습 현황</h2>
        </div>
      </div>
      <div class="student-stat-grid dashboard-stat-grid">
        <button class="student-stat-card" @click="router.push('/student/exams')">
          <span class="student-stat-card__label">평균 점수</span>
          <strong class="student-stat-card__value">{{ averageScore ?? '-' }}<small v-if="averageScore !== null">점</small></strong>
          <span class="student-stat-card__hint">시험 결과 보기</span>
        </button>
        <div class="student-stat-card">
          <span class="student-stat-card__label">숙제 완성도</span>
          <strong class="student-stat-card__value">{{ averageCompletion ?? '-' }}<small v-if="averageCompletion !== null">%</small></strong>
          <span class="student-stat-card__hint">제출한 숙제 기준</span>
        </div>
        <button type="button" class="student-stat-card" @click="attendanceDialogVisible = true">
          <span class="student-stat-card__label">출석률</span>
          <strong class="student-stat-card__value">{{ attendanceRate ?? '-' }}<small v-if="attendanceRate !== null">%</small></strong>
          <span class="student-stat-card__hint">출석 상세 보기</span>
        </button>
      </div>
    </section>

    <button type="button" class="dashboard-video-entry" @click="router.push('/student/videos')">
      <span class="dashboard-video-entry__icon"><el-icon><VideoPlay /></el-icon></span>
      <span class="dashboard-video-entry__content">
        <small>LESSON VIDEO</small>
        <strong>수업 다시보기</strong>
        <span>지난 수업에서 놓친 부분을 이어서 학습하세요.</span>
      </span>
      <el-icon class="dashboard-video-entry__arrow"><ArrowRight /></el-icon>
    </button>

    <section aria-labelledby="homework-flow-title">
      <div class="student-section-heading">
        <div>
          <p class="student-section-heading__eyebrow">HOMEWORK</p>
          <h2 id="homework-flow-title" class="student-section-heading__title">최근 숙제 흐름</h2>
        </div>
      </div>

      <div v-if="recentHomeworks.length" class="student-surface homework-list">
        <div v-for="homework in recentHomeworks" :key="homework.id" class="homework-progress-row">
          <div class="homework-progress-row__header">
            <span>{{ homework.homeworkTitle }}</span>
            <strong>{{ homework.completion ?? 0 }}%</strong>
          </div>
          <div class="student-progress-track"><span :style="{ width: `${homework.completion ?? 0}%` }" /></div>
        </div>
      </div>
      <div v-else class="student-empty-state">
        <span class="student-empty-state__icon"><el-icon><Notebook /></el-icon></span>
        <h3>표시할 숙제 기록이 없어요</h3>
        <p>숙제를 제출하면 완성도 흐름이 이곳에 쌓여요.</p>
      </div>
    </section>

    <el-dialog v-model="attendanceDialogVisible" title="출석 현황" width="420px" class="student-dialog">
      <div v-if="attendanceStats" class="attendance-detail">
        <div class="attendance-detail__hero">
          <strong>{{ attendanceStats.attendanceRate }}%</strong>
          <span>전체 {{ attendanceStats.totalLessons }}회 수업</span>
        </div>
        <div class="attendance-detail__grid">
          <div><strong>{{ attendanceStats.presentCount }}</strong><span>출석</span></div>
          <div><strong>{{ attendanceStats.absentCount }}</strong><span>결석</span></div>
          <div><strong>{{ attendanceStats.lateCount }}</strong><span>지각</span></div>
          <div><strong>{{ attendanceStats.videoCount }}</strong><span>인강</span></div>
        </div>
      </div>
      <div v-else class="student-empty-state student-empty-state--compact"><p>출석 기록이 아직 없어요.</p></div>
    </el-dialog>
  </div>
</template>

<style scoped>
.dashboard-page { display: grid; min-width: 0; gap: 28px; }
.dashboard-header__actions { display: flex; align-items: center; gap: 8px; }
.dashboard-student-meta { display: flex; flex-wrap: wrap; gap: 6px; margin: 8px 0 0; }
.dashboard-student-meta span { padding: 4px 9px; border-radius: 999px; color: var(--student-primary-strong); background: var(--student-primary-soft); font-size: 11px; font-weight: 750; }
.dashboard-preview-exit { min-height: 44px; padding: 0 13px; border: 1px solid rgba(255, 255, 255, .55); border-radius: 999px; color: var(--student-primary); background: var(--student-surface); box-shadow: var(--student-shadow-soft); font: inherit; font-size: 12px; font-weight: 800; white-space: nowrap; cursor: pointer; }
.dashboard-notification-button { position: relative; }
.dashboard-notification-button__badge { position: absolute; top: -5px; right: -5px; display: grid; min-width: 20px; height: 20px; padding: 0 5px; place-items: center; border: 2px solid var(--student-surface); border-radius: 999px; color: #fff; background: var(--student-danger); font-size: 10px; font-weight: 800; line-height: 1; }
.dashboard-hero { display: grid; min-width: 0; gap: 22px; }
.dashboard-hero__content { display: grid; min-width: 0; gap: 8px; }
.dashboard-hero__content > * { min-width: 0; overflow-wrap: anywhere; }
.dashboard-hero__action { width: fit-content; min-width: 172px; color: var(--student-primary-strong); background: #fff; border: 0; }
.student-stat-card { text-align: left; }
button.student-stat-card { cursor: pointer; }
.student-stat-card__value small { margin-left: 3px; font-size: 14px; font-weight: 700; }
.dashboard-page .dashboard-stat-grid .student-stat-card:last-child { grid-column: 1 / -1; }
.dashboard-video-entry { display: grid; grid-template-columns: 48px minmax(0, 1fr) 24px; align-items: center; gap: 14px; width: 100%; min-width: 0; padding: 17px 18px; border: 1px solid var(--student-border); border-radius: var(--student-radius-lg); color: inherit; background: var(--student-surface); box-shadow: var(--student-shadow-soft); font: inherit; text-align: left; cursor: pointer; }
.dashboard-video-entry__icon { display: grid; width: 48px; height: 48px; place-items: center; border-radius: 15px; color: var(--student-primary); background: var(--student-primary-soft); font-size: 24px; }
.dashboard-video-entry__content { display: grid; min-width: 0; gap: 3px; }
.dashboard-video-entry__content small { color: var(--student-primary); font-size: 10px; font-weight: 800; letter-spacing: .07em; }
.dashboard-video-entry__content strong { color: var(--student-ink); font-size: 16px; font-weight: 800; }
.dashboard-video-entry__content span { color: var(--student-muted); font-size: 12px; line-height: 1.45; overflow-wrap: anywhere; }
.dashboard-video-entry__arrow { color: var(--student-muted); font-size: 18px; }
.dashboard-video-entry:focus-visible { outline: 3px solid color-mix(in srgb, var(--student-primary) 28%, transparent); outline-offset: 3px; }
.homework-list { overflow: hidden; }
.homework-progress-row { padding: 16px 18px; border-bottom: 1px solid var(--student-border); }
.homework-progress-row:last-child { border-bottom: 0; }
.homework-progress-row__header { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 10px; color: var(--student-ink); font-size: 14px; font-weight: 700; }
.homework-progress-row__header span { min-width: 0; overflow-wrap: anywhere; }
.homework-progress-row__header strong { flex: 0 0 auto; color: var(--student-primary); }
.attendance-detail { display: grid; gap: 18px; }
.attendance-detail__hero { display: grid; justify-items: center; gap: 3px; padding: 22px; border-radius: 18px; color: #fff; background: linear-gradient(135deg, var(--student-primary), #6d8df4); }
.attendance-detail__hero strong { font-size: 38px; line-height: 1.1; letter-spacing: -1px; }
.attendance-detail__hero span { font-size: 13px; opacity: .86; }
.attendance-detail__grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.attendance-detail__grid div { display: grid; justify-items: center; gap: 3px; padding: 16px; border: 1px solid var(--student-border); border-radius: 14px; background: var(--student-surface-soft); }
.attendance-detail__grid strong { color: var(--student-ink); font-size: 23px; }
.attendance-detail__grid span { color: var(--student-muted); font-size: 13px; }
@media (max-width: 420px) {
  .dashboard-page :deep(.student-page__header) { flex-direction: column; align-items: stretch; gap: 12px; }
  .dashboard-header__actions { order: -1; align-self: flex-end; }
  .dashboard-hero__action { width: 100%; min-width: 0; }
}
@media (min-width: 680px) { .dashboard-hero { grid-template-columns: 1fr auto; align-items: end; } }
@media (min-width: 720px) {
  .dashboard-page .dashboard-stat-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .dashboard-page .dashboard-stat-grid .student-stat-card:last-child { grid-column: auto; }
}
</style>
