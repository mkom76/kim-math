<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Bell, DataLine, EditPen, Setting, VideoPlay } from '@element-plus/icons-vue'
import {
  authAPI,
  lessonAPI,
  submissionAPI,
  studentAPI,
  studentHomeworkAPI,
  studentNotificationAPI,
} from '@/api/client'
import type {
  AttendanceStats,
  AuthResponse,
  Student,
  StudentHomework,
  Submission,
} from '@/api/client'
import StudentUiGuideDialog from '@/components/student-v2/StudentUiGuideDialog.vue'

import StudentPageHeader from '@/components/student-v2/StudentPageHeader.vue'
import StudentClassSwitcher from '@/components/StudentClassSwitcher.vue'

const router = useRouter()

const loading = ref(false)
const currentUser = ref<AuthResponse>({})
const studentInfo = ref<Student | null>(null)
const mySubmissions = ref<Submission[]>([])
const homeworks = ref<StudentHomework[]>([])
const attendanceStats = ref<AttendanceStats | null>(null)
const unreadNotificationCount = ref(0)
const loadError = ref(false)

const displayName = computed(() => currentUser.value.name || studentInfo.value?.name || '학생')
const attendanceRate = computed(() => attendanceStats.value?.attendanceRate ?? null)
function submissionTime(item: Submission) {
  const value = Date.parse(item.submittedAt || item.createdAt || '')
  return Number.isFinite(value) ? value : 0
}
const latestSubmission = computed(
  () =>
    [...mySubmissions.value].sort(
      (a, b) => submissionTime(b) - submissionTime(a) || (b.id ?? 0) - (a.id ?? 0),
    )[0],
)
const latestTestTitle = computed(() => {
  const latest = latestSubmission.value
  return latest ? latest.testTitle || latest.test?.title || '최근 시험' : '응시 기록이 없어요'
})
const latestScore = computed(() => {
  const latest = latestSubmission.value
  if (!latest) return '-'
  if (typeof latest.totalScore === 'number') return `${latest.totalScore}점`
  return latest.pendingEssayCount ? '채점 중' : '비공개'
})
const shortcuts = [
  { title: '수업 영상', path: '/student/videos', icon: VideoPlay },
  { title: '시험 통계', path: '/student/statistics/tests', icon: DataLine },
  { title: 'Q&A', path: '/student/solution-notebook', icon: EditPen },
]
function homeworkTime(item: StudentHomework) {
  const value = Date.parse(item.lessonDate || item.createdAt || item.dueDate || '')
  return Number.isFinite(value) ? value : 0
}
const latestHomework = computed(
  () =>
    [...homeworks.value].sort(
      (a, b) => homeworkTime(b) - homeworkTime(a) || (b.id ?? 0) - (a.id ?? 0),
    )[0],
)
const latestCompletion = computed(() => latestHomework.value?.completion ?? null)

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
  loadError.value = false
  mySubmissions.value = []
  homeworks.value = []
  attendanceStats.value = null
  studentInfo.value = null
  try {
    const userResponse = await authAPI.getCurrentUser()
    currentUser.value = userResponse.data
    if (!currentUser.value.userId) throw new Error('Missing student session')

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
    loadError.value = true
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
  <div class="student-page dashboard-page" v-loading="loading" :aria-busy="loading">
    <div class="dashboard-intro">
      <StudentPageHeader
        eyebrow="KIM MATH"
        title="나의 학습"
        :subtitle="`${displayName} · ${studentInfo?.academyName || '김수학'}`"
      >
        <template #action>
          <div class="dashboard-header-actions">
            <button
              type="button"
              class="student-icon-button notification-button"
              :aria-label="
                unreadNotificationCount
                  ? `알림 ${unreadNotificationCount}개 확인하기`
                  : '알림 확인하기'
              "
              @click="router.push('/student/notifications')"
            >
              <el-icon><Bell /></el-icon>
              <span v-if="unreadNotificationCount" class="notification-dot" aria-hidden="true" />
            </button>
            <button
              type="button"
              class="student-icon-button"
              aria-label="설정 열기"
              @click="router.push('/settings')"
            >
              <el-icon><Setting /></el-icon>
            </button>
          </div>
        </template>
      </StudentPageHeader>
      <StudentClassSwitcher variant="bar" :reload-on-switch="false" @switched="fetchDashboard" />
    </div>
    <div v-if="loadError" class="dashboard-error" role="alert">
      <p>학습 정보를 불러오지 못했어요.</p>
      <button type="button" @click="fetchDashboard">다시 불러오기</button>
    </div>
    <section class="feedback-card" aria-labelledby="feedback-heading">
      <span id="feedback-heading" class="feedback-label">수업 피드백</span>
      <p>선생님이 남긴 피드백을 확인해 보세요.</p>
      <RouterLink class="feedback-action" to="/student/daily-feedback">
        피드백 읽기 <el-icon><ArrowRight /></el-icon>
      </RouterLink>
    </section>
    <nav class="dashboard-shortcuts" aria-label="학습 바로가기">
      <RouterLink
        v-for="item in shortcuts"
        :key="item.path"
        :to="item.path"
        class="dashboard-shortcut"
      >
        <el-icon><component :is="item.icon" /></el-icon><span>{{ item.title }}</span>
      </RouterLink>
    </nav>
    <section aria-labelledby="study-status-title">
      <h2 id="study-status-title" class="statistics-heading">나의 통계</h2>
      <div class="statistics-list">
        <RouterLink to="/student/statistics/tests" class="statistics-row" data-testid="latest-test">
          <span class="statistics-copy"
            ><strong>시험 성적</strong>
            <small v-if="!loadError"
              >{{ latestSubmission ? '최근 시험 · ' : '' }}{{ latestTestTitle }}</small
            >
          </span>
          <span class="statistics-value"
            >{{ latestScore }} <el-icon><ArrowRight /></el-icon
          ></span>
        </RouterLink>
        <RouterLink
          to="/student/statistics/homework"
          class="statistics-row"
          data-testid="latest-homework"
        >
          <span class="statistics-copy"
            ><strong>숙제 완성도</strong>
            <small v-if="!loadError">{{
              latestHomework
                ? `최근 숙제 · ${latestHomework.homeworkTitle || '숙제'}`
                : '숙제 기록이 없어요'
            }}</small>
          </span>
          <span class="statistics-value"
            >{{ latestCompletion === null ? '-' : `${latestCompletion}%` }}
            <el-icon><ArrowRight /></el-icon
          ></span>
        </RouterLink>
        <RouterLink to="/student/statistics/attendance" class="statistics-row">
          <strong>출석</strong>
          <span class="statistics-value"
            >{{ attendanceRate === null ? '-' : `${attendanceRate}%` }}
            <el-icon><ArrowRight /></el-icon
          ></span>
        </RouterLink>
      </div>
    </section>
    <StudentUiGuideDialog auto-open />
  </div>
</template>

<style scoped>
.dashboard-page {
  display: grid;
  gap: var(--student-space-5);
}
.dashboard-intro {
  display: grid;
  gap: var(--student-space-3);
}
.dashboard-intro :deep(.student-page__header) {
  margin-bottom: 0;
}
.dashboard-intro :deep(.student-page__title) {
  font-size: 26px;
}
.dashboard-intro :deep(.student-page__eyebrow) {
  letter-spacing: 0.04em;
}
.dashboard-header-actions {
  display: flex;
  flex-shrink: 0;
  gap: var(--student-space-1);
}
.notification-button {
  position: relative;
}
.notification-dot {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--student-danger);
}
.feedback-card {
  padding: var(--student-space-5);
  background: var(--student-surface);
  border: 1px solid var(--student-border);
  border-radius: var(--student-radius-lg);
}
.feedback-label {
  display: inline-block;
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--student-primary-soft);
  color: var(--student-primary-strong);
  font-size: var(--student-font-sm);
  font-weight: 600;
}
.feedback-card p {
  margin: 12px 0 6px;
  color: var(--student-ink);
  font-size: var(--student-font-title-sm);
  font-weight: 700;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
.feedback-action {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: var(--student-space-3);
  min-height: 48px;
  padding: 10px 14px;
  margin-top: var(--student-space-4);
  border-radius: var(--student-radius-sm);
  color: var(--student-surface);
  background: var(--student-primary);
  font-weight: 600;
  text-decoration: none;
}
.feedback-action:hover {
  background: var(--student-primary-strong);
}
.dashboard-shortcuts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--student-space-2);
}
.dashboard-shortcut {
  display: grid;
  place-items: center;
  gap: var(--student-space-2);
  min-width: 0;
  min-height: 84px;
  padding: 14px 4px;
  border: 1px solid var(--student-border);
  border-radius: var(--student-radius-md);
  background: var(--student-surface);
  color: var(--student-text);
  text-decoration: none;
  font-size: var(--student-font-body);
}
.dashboard-shortcut .el-icon {
  color: var(--student-primary);
  font-size: 22px;
}
.dashboard-shortcut:hover {
  background: var(--student-surface-hover);
}
.statistics-heading {
  margin: 0 0 10px;
  color: var(--student-muted);
  font-size: var(--student-font-body);
  font-weight: 600;
}
.statistics-list {
  padding: 0 var(--student-space-4);
  background: var(--student-surface);
  border: 1px solid var(--student-border);
  border-radius: var(--student-radius-md);
}
.statistics-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--student-space-3);
  min-height: 64px;
  padding: var(--student-space-4) 0;
  color: var(--student-text);
  border-bottom: 1px solid var(--student-border);
  text-decoration: none;
}
.statistics-row:last-child {
  border-bottom: 0;
}
.statistics-row strong {
  font-size: var(--student-font-label);
  font-weight: 600;
}
.statistics-copy {
  display: grid;
  min-width: 0;
  gap: 4px;
}
.statistics-copy small {
  color: var(--student-muted);
  font-size: var(--student-font-md);
  overflow-wrap: anywhere;
}
.statistics-value {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  font-size: var(--student-font-body);
  font-variant-numeric: tabular-nums;
}
.statistics-value .el-icon {
  color: var(--student-muted);
  font-size: 13px;
}
.statistics-row:hover {
  color: var(--student-primary);
}

.dashboard-error {
  padding: var(--student-space-4);
  background: var(--student-danger-soft);
  color: var(--student-danger);
  border-radius: var(--student-radius-md);
}
.dashboard-error p {
  margin: 0 0 8px;
}
.dashboard-error button {
  min-height: 44px;
  padding: 8px 14px;
  border: 1px solid currentColor;
  border-radius: var(--student-radius-sm);
  color: inherit;
  background: var(--student-surface);
  font: inherit;
  cursor: pointer;
}
.dashboard-page a:focus-visible {
  outline: 3px solid var(--student-primary);
  outline-offset: 3px;
}
@media (max-width: 380px) {
  .dashboard-header-actions {
    gap: 0;
  }
  .dashboard-header-actions .student-icon-button {
    width: 44px !important;
    min-width: 44px !important;
  }
  .dashboard-intro :deep(.student-page__header) {
    gap: 4px;
  }
  .feedback-card {
    padding: var(--student-space-4);
  }
}
</style>
