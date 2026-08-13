<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authAPI, lessonAPI, submissionAPI, studentAPI, studentNotificationAPI } from '@/api/client'
import type { AttendanceStats, AuthResponse, Lesson, Student, Submission } from '@/api/client'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useStudentUiMode } from '@/composables/useStudentUiMode'

interface DashboardTest {
  id: number
  title: string
  className?: string
  questionCount?: number
  lessonDate?: string
}

const router = useRouter()
const { isMobile } = useBreakpoint()
const { leavePreview } = useStudentUiMode()
const loading = ref(false)
const currentUser = ref<AuthResponse>({})
const studentInfo = ref<Student | null>(null)
const availableTests = ref<DashboardTest[]>([])
const mySubmissions = ref<Submission[]>([])
const attendanceStats = ref<AttendanceStats | null>(null)
const pastTestsDialogVisible = ref(false)
const attendanceDialogVisible = ref(false)
const unreadNotificationCount = ref(0)

const submittedTestIds = computed(() => new Set(mySubmissions.value.map(submission => submission.testId)))
const untakenTests = computed(() => availableTests.value.filter(test => !submittedTestIds.value.has(test.id)))
const primaryTest = computed(() => untakenTests.value[0] ?? null)
const displayName = computed(() => currentUser.value.name || studentInfo.value?.name || '학생')
const attendanceRate = computed(() => attendanceStats.value?.attendanceRate ?? null)

const getSubmissionForTest = (testId: number) => mySubmissions.value.find(submission => submission.testId === testId)
const getPendingEssayCount = (testId: number) => getSubmissionForTest(testId)?.pendingEssayCount ?? 0

const parseLocalDate = (dateStr: string) => {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateStr)
  if (!match) return new Date(dateStr)
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
}

const formatLessonDate = (dateStr?: string, options: Intl.DateTimeFormatOptions = { month: 'long', day: 'numeric' }) => {
  if (!dateStr) return '날짜 미정'
  return parseLocalDate(dateStr).toLocaleDateString('ko-KR', options)
}

const openTest = (test: DashboardTest) => {
  router.push(getSubmissionForTest(test.id) ? `/student/tests/${test.id}/result` : `/student/tests/${test.id}`)
}

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
    const [studentResponse, lessonsResponse, submissionsResponse] = await Promise.all([
      studentAPI.getStudent(studentId),
      lessonAPI.getLessonsByStudent(studentId),
      submissionAPI.getStudentSubmissions(studentId),
    ])
    studentInfo.value = studentResponse.data
    availableTests.value = (lessonsResponse.data as Lesson[])
      .filter((lesson): lesson is Lesson & { testId: number; testTitle: string } => lesson.testId != null && !!lesson.testTitle)
      .map(lesson => ({
        id: lesson.testId,
        title: lesson.testTitle,
        className: lesson.className,
        lessonDate: lesson.lessonDate,
      }))
    mySubmissions.value = submissionsResponse.data

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
        <span class="student-hero__label">{{ primaryTest ? '다음 시험' : '오늘의 학습' }}</span>
        <h2 class="student-hero__title">{{ primaryTest?.title || '오늘 수업을 가볍게 복습해 볼까요?' }}</h2>
        <p class="student-hero__description">
          <template v-if="primaryTest">
            {{ formatLessonDate(primaryTest.lessonDate) }}<span v-if="primaryTest.className"> · {{ primaryTest.className }}</span>
          </template>
          <template v-else>피드백과 수업 영상을 확인하고 학습 흐름을 이어가세요.</template>
        </p>
      </div>
      <el-button v-if="primaryTest" class="dashboard-hero__action" @click="openTest(primaryTest)">
        시험 시작하기 <el-icon><ArrowRight /></el-icon>
      </el-button>
      <el-button v-else class="dashboard-hero__action" @click="router.push('/student/daily-feedback')">
        피드백 확인하기 <el-icon><ArrowRight /></el-icon>
      </el-button>
    </section>

    <section aria-labelledby="study-status-title">
      <div class="student-section-heading">
        <div>
          <p class="student-section-heading__eyebrow">한눈에 보기</p>
          <h2 id="study-status-title" class="student-section-heading__title">나의 학습 현황</h2>
        </div>
        <button class="student-text-action" @click="router.push('/student/stats')">자세히</button>
      </div>
      <div class="student-stat-grid">
        <button class="student-stat-card" @click="primaryTest && openTest(primaryTest)">
          <span class="student-stat-card__label">미응시 시험</span>
          <strong class="student-stat-card__value">{{ untakenTests.length }}<small>개</small></strong>
          <span class="student-stat-card__hint">바로 이어서 풀기</span>
        </button>
        <button class="student-stat-card" @click="pastTestsDialogVisible = true">
          <span class="student-stat-card__label">완료한 시험</span>
          <strong class="student-stat-card__value">{{ mySubmissions.length }}<small>개</small></strong>
          <span class="student-stat-card__hint">지난 결과 보기</span>
        </button>
        <button class="student-stat-card" @click="attendanceDialogVisible = true">
          <span class="student-stat-card__label">출석률</span>
          <strong class="student-stat-card__value">{{ attendanceRate ?? '-' }}<small v-if="attendanceRate !== null">%</small></strong>
          <span class="student-stat-card__hint">출석 상세 보기</span>
        </button>
        <button class="student-stat-card" @click="router.push('/student/daily-feedback')">
          <span class="student-stat-card__label">학습 피드백</span>
          <strong class="student-stat-card__value student-stat-card__value--icon"><el-icon><ChatDotRound /></el-icon></strong>
          <span class="student-stat-card__hint">선생님 코멘트</span>
        </button>
      </div>
    </section>

    <section aria-labelledby="test-list-title">
      <div class="student-section-heading">
        <div>
          <p class="student-section-heading__eyebrow">TEST</p>
          <h2 id="test-list-title" class="student-section-heading__title">시험 목록</h2>
        </div>
        <span class="student-section-heading__count">{{ availableTests.length }}</span>
      </div>

      <div v-if="availableTests.length" class="student-surface student-list">
        <button v-for="test in availableTests" :key="test.id" class="student-list-row" @click="openTest(test)">
          <span class="student-list-row__icon" :class="{ 'is-complete': getSubmissionForTest(test.id) }">
            <el-icon><DocumentChecked v-if="getSubmissionForTest(test.id)" /><EditPen v-else /></el-icon>
          </span>
          <span class="student-list-row__content">
            <strong>{{ test.title }}</strong>
            <small>{{ formatLessonDate(test.lessonDate) }}<span v-if="test.className"> · {{ test.className }}</span></small>
            <span v-if="getPendingEssayCount(test.id)" class="dashboard-test-row__pending">
              서술형 {{ getPendingEssayCount(test.id) }}문제 채점 중
            </span>
          </span>
          <span class="student-list-row__trailing">
            <span v-if="getSubmissionForTest(test.id)?.totalScore != null" class="dashboard-test-row__score">
              {{ getSubmissionForTest(test.id)?.totalScore }}점
            </span>
            <span v-else class="student-pill student-pill--warning">미응시</span>
            <el-icon><ArrowRight /></el-icon>
          </span>
        </button>
      </div>
      <div v-else class="student-empty-state">
        <span class="student-empty-state__icon"><el-icon><Document /></el-icon></span>
        <h3>예정된 시험이 없어요</h3>
        <p>새 시험이 등록되면 이곳에서 바로 확인할 수 있어요.</p>
      </div>
    </section>

    <el-dialog v-model="pastTestsDialogVisible" title="지난 시험" :fullscreen="isMobile" width="680px" class="student-dialog">
      <div v-if="mySubmissions.length" class="student-surface student-list dialog-list">
        <button
          v-for="submission in mySubmissions"
          :key="submission.id"
          class="student-list-row"
          @click="submission.testId && router.push(`/student/tests/${submission.testId}/result`)"
        >
          <span class="student-list-row__icon is-complete"><el-icon><Trophy /></el-icon></span>
          <span class="student-list-row__content">
            <strong>{{ submission.testTitle || submission.test?.title || '시험 결과' }}</strong>
            <small>{{ submission.submittedAt ? new Date(submission.submittedAt).toLocaleDateString('ko-KR') : '-' }}</small>
          </span>
          <span class="student-list-row__trailing">
            <strong>{{ submission.totalScore == null ? '비공개' : `${submission.totalScore}점` }}</strong>
            <el-icon><ArrowRight /></el-icon>
          </span>
        </button>
      </div>
      <div v-else class="student-empty-state student-empty-state--compact"><h3>아직 완료한 시험이 없어요</h3></div>
    </el-dialog>

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
.dashboard-preview-exit { min-height: 44px; padding: 0 13px; border: 1px solid rgba(255, 255, 255, .55); border-radius: 999px; color: var(--student-primary); background: var(--student-surface); box-shadow: var(--student-shadow-soft); font: inherit; font-size: 12px; font-weight: 800; white-space: nowrap; cursor: pointer; }
.dashboard-notification-button { position: relative; }
.dashboard-notification-button__badge { position: absolute; top: -5px; right: -5px; display: grid; min-width: 20px; height: 20px; padding: 0 5px; place-items: center; border: 2px solid var(--student-surface); border-radius: 999px; color: #fff; background: var(--student-danger); font-size: 10px; font-weight: 800; line-height: 1; }
.dashboard-hero { display: grid; min-width: 0; gap: 22px; }
.dashboard-hero__content { display: grid; min-width: 0; gap: 8px; }
.dashboard-hero__content > * { min-width: 0; overflow-wrap: anywhere; }
.dashboard-hero__action { width: fit-content; min-width: 172px; color: var(--student-primary-strong); background: #fff; border: 0; }
.student-text-action { min-height: 44px; padding: 0 2px; border: 0; background: transparent; color: var(--student-primary); font: inherit; font-weight: 700; cursor: pointer; }
.student-section-heading__count { display: grid; place-items: center; min-width: 30px; height: 30px; padding: 0 9px; border-radius: 999px; color: var(--student-primary); background: var(--student-primary-soft); font-size: 13px; font-weight: 800; }
.student-stat-card { text-align: left; cursor: pointer; }
.student-stat-card__value small { margin-left: 3px; font-size: 14px; font-weight: 700; }
.student-stat-card__value--icon { display: flex; align-items: center; font-size: 27px; color: var(--student-primary); }
.dashboard-test-row__pending { width: fit-content; margin-top: 3px; color: var(--student-warning); font-size: 12px; font-weight: 700; }
.dashboard-test-row__score { color: var(--student-primary); font-size: 15px; font-weight: 800; white-space: nowrap; }
.dialog-list { margin-top: 4px; }
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
</style>
