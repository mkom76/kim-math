<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  authAPI,
  lessonAPI,
  studentAPI,
  studentHomeworkAPI,
  submissionAPI,
  videoProgressAPI,
  type AttendanceStats,
  type Student,
  type StudentHomework,
  type Submission,
  type VideoProgress,
} from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import StudentPageHeader from '@/components/student-v2/StudentPageHeader.vue'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const student = ref<Student | null>(null)
const submissions = ref<Submission[]>([])
const homeworks = ref<StudentHomework[]>([])
const videoProgress = ref<VideoProgress[]>([])
const attendance = ref<AttendanceStats | null>(null)

const visibleScores = computed(() =>
  submissions.value.filter((item) => typeof item.totalScore === 'number'),
)

const averageScore = computed(() => {
  if (!visibleScores.value.length) return null
  const total = visibleScores.value.reduce((sum, item) => sum + item.totalScore, 0)
  return Math.round((total / visibleScores.value.length) * 10) / 10
})

const completedHomework = computed(() => homeworks.value.filter((item) => item.completion != null))

const averageCompletion = computed(() => {
  if (!completedHomework.value.length) return null
  const total = completedHomework.value.reduce((sum, item) => sum + (item.completion ?? 0), 0)
  return Math.round(total / completedHomework.value.length)
})

const averageVideoProgress = computed(() => {
  if (!videoProgress.value.length) return null
  const total = videoProgress.value.reduce((sum, item) => sum + item.progressPercent, 0)
  return Math.round(total / videoProgress.value.length)
})

const recentSubmissions = computed(() =>
  [...submissions.value]
    .sort((a, b) => {
      const aTime = a.submittedAt ? new Date(a.submittedAt).getTime() : 0
      const bTime = b.submittedAt ? new Date(b.submittedAt).getTime() : 0
      return bTime - aTime
    })
    .slice(0, 5),
)

const recentHomeworks = computed(() =>
  [...homeworks.value].filter((item) => item.completion != null).slice(0, 5),
)

function formatDate(value?: string) {
  if (!value) return '-'
  return new Date(value).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
}

async function fetchStats() {
  loading.value = true
  try {
    const user = await authAPI.getCurrentUser()
    if (!user.data.userId) {
      router.push('/login')
      return
    }

    const studentId = user.data.userId
    const [studentRes, submissionsRes, homeworkRes, videoRes, attendanceRes] = await Promise.all([
      studentAPI.getStudent(studentId),
      submissionAPI.getStudentSubmissions(studentId),
      studentHomeworkAPI.getByStudentId(studentId),
      videoProgressAPI.getStudentProgress(studentId).catch(() => ({ data: [] as VideoProgress[] })),
      lessonAPI.getAttendanceStats(studentId).catch(() => null),
    ])

    student.value = studentRes.data
    submissions.value = submissionsRes.data.content || submissionsRes.data
    homeworks.value = homeworkRes.data.content || homeworkRes.data
    videoProgress.value = videoRes.data
    attendance.value = attendanceRes
  } catch {
    ElMessage.error('학습 통계를 불러오지 못했습니다')
  } finally {
    loading.value = false
  }
}

async function logout() {
  try {
    await authStore.logout()
    router.push('/login')
  } catch {
    ElMessage.error('로그아웃하지 못했습니다')
  }
}

onMounted(fetchStats)
</script>

<template>
  <div v-loading="loading" class="student-page">
    <StudentPageHeader
      eyebrow="MY LEARNING"
      title="내 학습"
      subtitle="최근 학습 흐름을 한눈에 확인해요."
    >
      <template #action>
        <el-button
          class="student-icon-button"
          aria-label="설정 열기"
          @click="router.push('/settings')"
        >
          <el-icon><Setting /></el-icon>
        </el-button>
      </template>
    </StudentPageHeader>

    <section class="profile-card">
      <el-avatar :size="64" class="profile-card__avatar"
        ><el-icon :size="28"><UserFilled /></el-icon
      ></el-avatar>
      <div class="profile-card__body">
        <h2>{{ student?.name || '학생' }}</h2>
        <p>{{ student?.academyName || '-' }} · {{ student?.className || '-' }}</p>
        <span>{{ student?.school || '-' }} {{ student?.grade || '' }}</span>
      </div>
    </section>

    <section class="student-stat-grid stats-grid">
      <div class="student-stat">
        <div class="student-stat__label">평균 점수</div>
        <div class="student-stat__value">
          {{ averageScore ?? '-' }}<small v-if="averageScore != null">점</small>
        </div>
        <div class="student-stat__hint">최근 {{ visibleScores.length }}회</div>
      </div>
      <div class="student-stat">
        <div class="student-stat__label">숙제 완성도</div>
        <div class="student-stat__value">
          {{ averageCompletion ?? '-' }}<small v-if="averageCompletion != null">%</small>
        </div>
        <div class="student-stat__hint">제출한 숙제 기준</div>
      </div>
      <div class="student-stat">
        <div class="student-stat__label">출석률</div>
        <div class="student-stat__value">
          {{ attendance?.attendanceRate ?? '-' }}<small v-if="attendance">%</small>
        </div>
        <div class="student-stat__hint">총 {{ attendance?.totalLessons ?? 0 }}회 수업</div>
      </div>
      <div class="student-stat">
        <div class="student-stat__label">영상 학습</div>
        <div class="student-stat__value">
          {{ averageVideoProgress ?? '-' }}<small v-if="averageVideoProgress != null">%</small>
        </div>
        <div class="student-stat__hint">등록 영상 평균</div>
      </div>
    </section>

    <section class="student-section">
      <div class="student-section__header">
        <div>
          <h2 class="student-section__title">최근 시험</h2>
          <p class="student-section__description">시험별 점수와 제출 날짜입니다.</p>
        </div>
      </div>

      <div class="student-surface">
        <div v-if="recentSubmissions.length" class="student-list">
          <button
            v-for="submission in recentSubmissions"
            :key="submission.id"
            class="student-list-row"
            type="button"
            @click="submission.testId && router.push(`/student/tests/${submission.testId}/result`)"
          >
            <span class="student-list-row__icon"
              ><el-icon><DataLine /></el-icon
            ></span>
            <span class="student-list-row__content">
              <span class="student-list-row__title">{{
                submission.testTitle || submission.test?.title || '시험'
              }}</span>
              <span class="student-list-row__meta">{{ formatDate(submission.submittedAt) }}</span>
            </span>
            <span class="student-list-row__trailing score-value"
              >{{ submission.totalScore ?? '-' }}점</span
            >
          </button>
        </div>
        <div v-else class="student-empty">
          <div class="student-empty__icon">
            <el-icon :size="28"><DataLine /></el-icon>
          </div>
          <div class="student-empty__title">아직 시험 기록이 없어요</div>
          <div class="student-empty__description">시험을 완료하면 점수 흐름이 이곳에 쌓입니다.</div>
        </div>
      </div>
    </section>

    <section class="student-section">
      <div class="student-section__header">
        <div>
          <h2 class="student-section__title">숙제 흐름</h2>
          <p class="student-section__description">최근 숙제의 완성도를 확인해요.</p>
        </div>
      </div>

      <div class="student-surface">
        <div v-if="recentHomeworks.length" class="student-list">
          <div v-for="homework in recentHomeworks" :key="homework.id" class="homework-progress-row">
            <div class="homework-progress-row__header">
              <span>{{ homework.homeworkTitle }}</span>
              <strong>{{ homework.completion ?? 0 }}%</strong>
            </div>
            <div class="student-progress-track">
              <span :style="{ width: `${homework.completion ?? 0}%` }" />
            </div>
          </div>
        </div>
        <div v-else class="student-empty">
          <div class="student-empty__title">표시할 숙제 기록이 없어요</div>
        </div>
      </div>
    </section>

    <section class="account-actions" aria-label="계정 메뉴">
      <button type="button" @click="router.push('/settings')">
        <el-icon><Setting /></el-icon><span>계정 설정</span><el-icon><ArrowRight /></el-icon>
      </button>
      <button type="button" class="account-actions__danger" @click="logout">
        <el-icon><SwitchButton /></el-icon><span>로그아웃</span><el-icon><ArrowRight /></el-icon>
      </button>
    </section>
  </div>
</template>

<style scoped>
.profile-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  border-radius: var(--student-radius-xl);
  background: linear-gradient(145deg, var(--student-surface), var(--student-primary-soft));
  box-shadow: var(--student-shadow);
}

.profile-card__avatar {
  flex: 0 0 auto;
  background: var(--student-primary);
  color: var(--student-surface);
}

.profile-card__body {
  min-width: 0;
}

.profile-card h2 {
  margin: 0;
  color: var(--student-ink);
  font-size: 21px;
  font-weight: 800;
}

.profile-card p,
.profile-card span {
  margin: 4px 0 0;
  color: var(--student-muted);
  font-size: 13px;
  font-weight: 550;
}

.student-stat__value small {
  margin-left: 2px;
  font-size: 13px;
  font-weight: 700;
}

.score-value {
  color: var(--student-primary);
  font-size: 18px;
  font-weight: 850;
}

.homework-progress-row {
  padding: 16px 18px;
  border-bottom: 1px solid var(--student-border);
}

.homework-progress-row:last-child {
  border-bottom: 0;
}

.homework-progress-row__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  color: var(--student-ink);
  font-size: 14px;
  font-weight: 700;
}

.homework-progress-row__header strong {
  color: var(--student-primary);
}

.account-actions {
  overflow: hidden;
  margin-top: 24px;
  border: 1px solid var(--student-border);
  border-radius: var(--student-radius-lg);
  background: var(--student-surface);
}

.account-actions button {
  display: grid;
  grid-template-columns: 24px 1fr 20px;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 56px;
  padding: 0 17px;
  border: 0;
  border-bottom: 1px solid var(--student-border);
  background: transparent;
  color: var(--student-text);
  font-size: 14px;
  font-weight: 700;
  text-align: left;
}

.account-actions button:last-child {
  border-bottom: 0;
}

.account-actions__danger {
  color: var(--student-danger) !important;
}
</style>
