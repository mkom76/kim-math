<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  authAPI,
  lessonAPI,
  studentHomeworkAPI,
  studentVideoAPI,
  submissionAPI,
  videoProgressAPI,
  type AttendanceStats,
  type StudentHomework,
  type StudentLessonVideos,
  type Submission,
  type VideoProgress,
} from '@/api/client'
import StudentPageHeader from '@/components/student-v2/StudentPageHeader.vue'

type StatisticsKind = 'tests' | 'homework' | 'attendance' | 'videos'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submissions = ref<Submission[]>([])
const homeworks = ref<StudentHomework[]>([])
const attendance = ref<AttendanceStats | null>(null)
const lessonsWithVideos = ref<StudentLessonVideos[]>([])
const videoProgress = ref<VideoProgress[]>([])

const kind = computed(() => route.meta.studentStatsKind as StatisticsKind)
const pageCopy: Record<StatisticsKind, { eyebrow: string; title: string; description: string }> = {
  tests: {
    eyebrow: 'TEST REPORT',
    title: '시험 통계',
    description: '응시 기록과 점수 흐름을 확인해요.',
  },
  homework: {
    eyebrow: 'HOMEWORK REPORT',
    title: '숙제 통계',
    description: '완성도와 오답 흐름을 확인해요.',
  },
  attendance: {
    eyebrow: 'ATTENDANCE REPORT',
    title: '출석 통계',
    description: '수업 참여 기록을 한눈에 확인해요.',
  },
  videos: {
    eyebrow: 'VIDEO REPORT',
    title: '영상 통계',
    description: '수업 영상 학습 진도를 확인해요.',
  },
}
const currentCopy = computed(() => pageCopy[kind.value])

const visibleScores = computed(() =>
  submissions.value.filter((item) => typeof item.totalScore === 'number'),
)
const sortedSubmissions = computed(() =>
  [...submissions.value].sort((a, b) => dateValue(b.submittedAt) - dateValue(a.submittedAt)),
)
const sortedScoredSubmissions = computed(() =>
  sortedSubmissions.value.filter((item) => typeof item.totalScore === 'number'),
)
const averageScore = computed(() =>
  average(
    visibleScores.value.map((item) => item.totalScore),
    1,
  ),
)
const maxScore = computed(() =>
  extreme(
    visibleScores.value.map((item) => item.totalScore),
    'max',
  ),
)
const minScore = computed(() =>
  extreme(
    visibleScores.value.map((item) => item.totalScore),
    'min',
  ),
)

const submittedHomeworks = computed(() =>
  homeworks.value
    .filter((item) => item.incorrectCount != null)
    .sort((a, b) => dateValue(b.dueDate) - dateValue(a.dueDate)),
)
const averageCompletion = computed(() =>
  average(
    submittedHomeworks.value.map((item) => item.completion ?? 0),
    1,
  ),
)
const incompleteHomeworkCount = computed(
  () => submittedHomeworks.value.filter((item) => (item.completion ?? 0) < 100).length,
)
const totalIncorrectCount = computed(() =>
  submittedHomeworks.value.reduce((sum, item) => sum + (item.incorrectCount ?? 0), 0),
)
const minCompletion = computed(() =>
  extreme(
    submittedHomeworks.value.map((item) => item.completion ?? 0),
    'min',
  ),
)

const progressMap = computed(() => new Map(videoProgress.value.map((item) => [item.videoId, item])))
const videoRecords = computed(() =>
  lessonsWithVideos.value.flatMap((lesson) =>
    lesson.videos.map((video) => ({
      video,
      lessonDate: lesson.lessonDate,
      className: lesson.className,
      progress: progressMap.value.get(video.id),
    })),
  ),
)
const averageVideoProgress = computed(() =>
  average(
    videoRecords.value.map((item) => item.progress?.progressPercent ?? 0),
    0,
  ),
)
const completedVideoCount = computed(
  () => videoRecords.value.filter((item) => item.progress?.completed).length,
)
const watchingVideoCount = computed(
  () =>
    videoRecords.value.filter((item) => {
      const progress = item.progress?.progressPercent ?? 0
      return progress > 0 && !item.progress?.completed
    }).length,
)

function dateValue(value?: string) {
  return value ? new Date(value).getTime() : 0
}

function average(values: number[], fractionDigits: number) {
  if (!values.length) return null
  const result = values.reduce((sum, value) => sum + value, 0) / values.length
  return Number(result.toFixed(fractionDigits))
}

function extreme(values: number[], mode: 'min' | 'max') {
  if (!values.length) return null
  return mode === 'min' ? Math.min(...values) : Math.max(...values)
}

function clampPercent(value?: number | null) {
  return Math.min(100, Math.max(0, value ?? 0))
}

function formatDate(value?: string) {
  if (!value) return '-'
  return new Date(value).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

function videoProgressLabel(progress?: VideoProgress) {
  if (progress?.completed) return '완료'
  if (progress && progress.progressPercent > 0) return `${progress.progressPercent}% 시청`
  return '시청 전'
}

async function fetchStatistics() {
  loading.value = true
  try {
    const userResponse = await authAPI.getCurrentUser()
    const studentId = userResponse.data.userId
    if (!studentId) {
      await router.replace('/login')
      return
    }

    if (kind.value === 'tests') {
      const response = await submissionAPI.getStudentSubmissions(studentId)
      submissions.value = response.data.content || response.data
    } else if (kind.value === 'homework') {
      const response = await studentHomeworkAPI.getByStudentId(studentId)
      homeworks.value = response.data.content || response.data
    } else if (kind.value === 'attendance') {
      attendance.value = await lessonAPI.getAttendanceStats(studentId)
    } else {
      const [videosResponse, progressResponse] = await Promise.all([
        studentVideoAPI.getVideos(studentId),
        videoProgressAPI
          .getStudentProgress(studentId)
          .catch(() => ({ data: [] as VideoProgress[] })),
      ])
      lessonsWithVideos.value = videosResponse.data
      videoProgress.value = progressResponse.data
    }
  } catch {
    ElMessage.error(`${currentCopy.value.title}를 불러오지 못했습니다`)
  } finally {
    loading.value = false
  }
}

onMounted(fetchStatistics)
</script>

<template>
  <div class="student-page statistics-page" v-loading="loading">
    <StudentPageHeader
      :eyebrow="currentCopy.eyebrow"
      :title="currentCopy.title"
      :subtitle="currentCopy.description"
    >
      <template #action>
        <button
          type="button"
          class="student-icon-button"
          aria-label="더보기로 돌아가기"
          @click="router.push('/student/more')"
        >
          <el-icon><ArrowLeft /></el-icon>
        </button>
      </template>
    </StudentPageHeader>

    <template v-if="kind === 'tests'">
      <section class="statistics-summary-grid" aria-label="시험 통계 요약">
        <div class="statistics-summary-card">
          <span>총 응시</span><strong>{{ submissions.length }}<small>회</small></strong>
        </div>
        <div class="statistics-summary-card">
          <span>평균 점수</span
          ><strong>{{ averageScore ?? '-' }}<small v-if="averageScore != null">점</small></strong>
        </div>
        <div class="statistics-summary-card">
          <span>최고 점수</span
          ><strong>{{ maxScore ?? '-' }}<small v-if="maxScore != null">점</small></strong>
        </div>
        <div class="statistics-summary-card">
          <span>최저 점수</span
          ><strong>{{ minScore ?? '-' }}<small v-if="minScore != null">점</small></strong>
        </div>
      </section>

      <section class="statistics-section" aria-labelledby="test-history-title">
        <div class="student-section-heading">
          <div>
            <p class="student-section-heading__eyebrow">SCORE FLOW</p>
            <h2 id="test-history-title" class="student-section-heading__title">점수 흐름</h2>
          </div>
        </div>
        <div
          v-if="sortedSubmissions.length && visibleScores.length"
          class="student-surface statistics-list"
        >
          <button
            v-for="submission in sortedScoredSubmissions"
            :key="submission.id"
            type="button"
            class="statistics-score-row"
            @click="submission.testId && router.push(`/student/tests/${submission.testId}/result`)"
          >
            <span class="statistics-row-title"
              ><strong>{{ submission.testTitle || submission.test?.title || '시험' }}</strong
              ><small>{{ formatDate(submission.submittedAt) }}</small></span
            >
            <span class="statistics-score-chart">
              <span><i :style="{ width: `${clampPercent(submission.totalScore)}%` }" /></span>
              <i
                v-if="submission.classAverage != null"
                :style="{ left: `${clampPercent(submission.classAverage)}%` }"
              />
            </span>
            <span class="statistics-score-value"
              ><strong>{{ submission.totalScore ?? '-' }}점</strong
              ><small v-if="submission.classAverage != null"
                >평균 {{ Math.round(submission.classAverage) }}</small
              ></span
            >
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
        <div
          v-else-if="submissions.length"
          class="student-empty-state student-empty-state--compact"
        >
          <p>점수 통계가 비공개되어 있어요.</p>
        </div>
        <div v-else class="student-empty-state">
          <span class="student-empty-state__icon"
            ><el-icon><DataLine /></el-icon
          ></span>
          <h3>응시 기록이 없어요</h3>
          <p>시험을 완료하면 점수 흐름이 쌓여요.</p>
        </div>
      </section>
    </template>

    <template v-else-if="kind === 'homework'">
      <section class="statistics-summary-grid" aria-label="숙제 통계 요약">
        <div class="statistics-summary-card">
          <span>평균 완성도</span
          ><strong
            >{{ averageCompletion ?? '-' }}<small v-if="averageCompletion != null">%</small></strong
          >
        </div>
        <div class="statistics-summary-card">
          <span>미완성 숙제</span><strong>{{ incompleteHomeworkCount }}<small>개</small></strong>
        </div>
        <div class="statistics-summary-card">
          <span>총 오답</span><strong>{{ totalIncorrectCount }}<small>개</small></strong>
        </div>
        <div class="statistics-summary-card">
          <span>최저 완성도</span
          ><strong>{{ minCompletion ?? '-' }}<small v-if="minCompletion != null">%</small></strong>
        </div>
      </section>

      <section class="statistics-section" aria-labelledby="homework-history-title">
        <div class="student-section-heading">
          <div>
            <p class="student-section-heading__eyebrow">HOMEWORK FLOW</p>
            <h2 id="homework-history-title" class="student-section-heading__title">숙제 기록</h2>
          </div>
        </div>
        <div v-if="submittedHomeworks.length" class="student-surface statistics-list">
          <div
            v-for="homework in submittedHomeworks"
            :key="homework.id"
            class="statistics-homework-row"
          >
            <span class="statistics-row-title"
              ><strong>{{ homework.homeworkTitle }}</strong
              ><small
                >{{ formatDate(homework.dueDate) }} · 오답 {{ homework.incorrectCount ?? 0 }} ·
                미풀이 {{ homework.unsolvedCount ?? 0 }}</small
              ></span
            >
            <span class="statistics-homework-progress"
              ><span><i :style="{ width: `${clampPercent(homework.completion)}%` }" /></span
              ><strong>{{ homework.completion ?? 0 }}%</strong></span
            >
          </div>
        </div>
        <div v-else class="student-empty-state">
          <span class="student-empty-state__icon"
            ><el-icon><Notebook /></el-icon
          ></span>
          <h3>숙제 기록이 없어요</h3>
          <p>숙제를 제출하면 완성도와 오답이 쌓여요.</p>
        </div>
      </section>
    </template>

    <template v-else-if="kind === 'attendance'">
      <section v-if="attendance" class="attendance-report">
        <div class="attendance-report__hero">
          <span>전체 출석률</span><strong>{{ attendance.attendanceRate }}%</strong
          ><small>총 {{ attendance.totalLessons }}회 수업</small>
        </div>
        <div class="attendance-report__grid">
          <div class="is-present">
            <strong>{{ attendance.presentCount }}</strong
            ><span>출석</span>
          </div>
          <div class="is-absent">
            <strong>{{ attendance.absentCount }}</strong
            ><span>결석</span>
          </div>
          <div class="is-late">
            <strong>{{ attendance.lateCount }}</strong
            ><span>지각</span>
          </div>
          <div>
            <strong>{{ attendance.earlyLeaveCount }}</strong
            ><span>조퇴</span>
          </div>
          <div class="is-video">
            <strong>{{ attendance.videoCount }}</strong
            ><span>인강</span>
          </div>
          <div>
            <strong>{{ attendance.uncheckedCount }}</strong
            ><span>미체크</span>
          </div>
        </div>
      </section>
      <div v-else class="student-empty-state">
        <span class="student-empty-state__icon"
          ><el-icon><Calendar /></el-icon
        ></span>
        <h3>출석 기록이 없어요</h3>
        <p>수업 출석을 확인하면 이곳에 반영돼요.</p>
      </div>
    </template>

    <template v-else>
      <section class="statistics-summary-grid" aria-label="영상 통계 요약">
        <div class="statistics-summary-card">
          <span>등록 영상</span><strong>{{ videoRecords.length }}<small>개</small></strong>
        </div>
        <div class="statistics-summary-card">
          <span>평균 진도</span
          ><strong
            >{{ averageVideoProgress ?? '-'
            }}<small v-if="averageVideoProgress != null">%</small></strong
          >
        </div>
        <div class="statistics-summary-card">
          <span>학습 완료</span><strong>{{ completedVideoCount }}<small>개</small></strong>
        </div>
        <div class="statistics-summary-card">
          <span>시청 중</span><strong>{{ watchingVideoCount }}<small>개</small></strong>
        </div>
      </section>

      <section class="statistics-section" aria-labelledby="video-history-title">
        <div class="student-section-heading">
          <div>
            <p class="student-section-heading__eyebrow">VIDEO PROGRESS</p>
            <h2 id="video-history-title" class="student-section-heading__title">영상별 진도</h2>
          </div>
        </div>
        <div v-if="videoRecords.length" class="student-surface statistics-list">
          <button
            v-for="record in videoRecords"
            :key="record.video.id"
            type="button"
            class="statistics-video-row"
            @click="router.push('/student/videos')"
          >
            <span class="statistics-video-row__icon"
              ><el-icon><VideoPlay /></el-icon
            ></span>
            <span class="statistics-row-title"
              ><strong>{{ record.video.title }}</strong
              ><small>{{ formatDate(record.lessonDate) }} · {{ record.className }}</small></span
            >
            <span class="statistics-video-row__progress"
              ><strong>{{ videoProgressLabel(record.progress) }}</strong
              ><span
                ><i
                  :style="{ width: `${clampPercent(record.progress?.progressPercent)}%` }" /></span
            ></span>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
        <div v-else class="student-empty-state">
          <span class="student-empty-state__icon"
            ><el-icon><VideoPlay /></el-icon
          ></span>
          <h3>등록된 영상이 없어요</h3>
          <p>수업 영상이 등록되면 학습 진도를 볼 수 있어요.</p>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.statistics-page {
  display: grid;
  min-width: 0;
  gap: 28px;
}
.statistics-header {
  margin-bottom: 0;
}
.statistics-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.statistics-summary-card {
  display: grid;
  min-width: 0;
  min-height: 112px;
  align-content: space-between;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--student-border);
  border-radius: var(--student-radius-lg);
  background: var(--student-surface);
  box-shadow: var(--student-shadow-soft);
}
.statistics-summary-card > span {
  color: var(--student-muted);
  font-size: 12px;
  font-weight: 700;
}
.statistics-summary-card > strong {
  color: var(--student-ink);
  font-size: clamp(25px, 8vw, 32px);
  font-weight: 850;
  line-height: 1;
}
.statistics-summary-card > strong small {
  margin-left: 3px;
  color: var(--student-muted);
  font-size: 12px;
  font-weight: 700;
}
.statistics-section {
  display: grid;
  gap: 0;
}
.statistics-list {
  overflow: hidden;
}
.statistics-score-row {
  display: grid;
  grid-template-columns: minmax(90px, 1.2fr) minmax(90px, 1.5fr) 68px 18px;
  align-items: center;
  gap: 11px;
  width: 100%;
  min-width: 0;
  min-height: 74px;
  padding: 11px 15px;
  border: 0;
  border-bottom: 1px solid var(--student-border);
  color: inherit;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.statistics-score-row:last-child,
.statistics-homework-row:last-child,
.statistics-video-row:last-child {
  border-bottom: 0;
}
.statistics-score-row:hover,
.statistics-video-row:hover {
  background: var(--student-surface-hover);
}
.statistics-score-row:focus-visible,
.statistics-video-row:focus-visible {
  position: relative;
  outline: 3px solid rgba(36, 87, 214, 0.25);
  outline-offset: -3px;
}
.statistics-row-title {
  display: grid;
  min-width: 0;
  gap: 3px;
}
.statistics-row-title strong {
  overflow: hidden;
  color: var(--student-ink);
  font-size: 13px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.statistics-row-title small {
  overflow: hidden;
  color: var(--student-muted);
  font-size: 10px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.statistics-score-chart {
  position: relative;
  min-width: 0;
}
.statistics-score-chart > span,
.statistics-homework-progress > span,
.statistics-video-row__progress > span {
  display: block;
  height: 8px;
  overflow: hidden;
  border-radius: var(--student-radius-pill);
  background: var(--student-slate-75);
}
.statistics-score-chart > span > i,
.statistics-homework-progress > span > i,
.statistics-video-row__progress > span > i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--student-primary), var(--student-blue-500));
}
.statistics-score-chart > i {
  position: absolute;
  top: -4px;
  width: 3px;
  height: 16px;
  border: 1px solid var(--student-surface);
  border-radius: 2px;
  background: var(--student-success);
}
.statistics-score-value {
  display: grid;
  gap: 2px;
  text-align: right;
  white-space: nowrap;
}
.statistics-score-value strong {
  color: var(--student-primary);
  font-size: 13px;
  font-weight: 850;
}
.statistics-score-value small {
  color: var(--student-muted);
  font-size: 9px;
}
.statistics-homework-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(120px, 0.8fr);
  align-items: center;
  gap: 16px;
  min-height: 74px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--student-border);
}
.statistics-homework-progress {
  display: grid;
  grid-template-columns: minmax(70px, 1fr) 42px;
  align-items: center;
  gap: 9px;
}
.statistics-homework-progress strong {
  color: var(--student-primary);
  font-size: 12px;
  text-align: right;
}
.attendance-report {
  display: grid;
  gap: 14px;
}
.attendance-report__hero {
  display: grid;
  justify-items: center;
  gap: 5px;
  padding: 32px 20px;
  border-radius: var(--student-radius-xl);
  color: var(--student-surface);
  background: linear-gradient(135deg, var(--student-primary-strong), var(--student-blue-500));
  box-shadow: 0 18px 36px rgba(36, 87, 214, 0.2);
}
.attendance-report__hero span {
  font-size: 13px;
  font-weight: 700;
  opacity: 0.82;
}
.attendance-report__hero strong {
  font-size: clamp(42px, 14vw, 58px);
  font-weight: 880;
  line-height: 1;
  letter-spacing: -0.04em;
}
.attendance-report__hero small {
  font-size: 12px;
  opacity: 0.82;
}
.attendance-report__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.attendance-report__grid div {
  display: grid;
  min-height: 100px;
  place-content: center;
  justify-items: center;
  gap: 5px;
  border: 1px solid var(--student-border);
  border-radius: 17px;
  background: var(--student-surface);
  box-shadow: var(--student-shadow-soft);
}
.attendance-report__grid strong {
  color: var(--student-ink);
  font-size: 26px;
  font-weight: 850;
}
.attendance-report__grid span {
  color: var(--student-muted);
  font-size: 12px;
  font-weight: 700;
}
.attendance-report__grid .is-present strong {
  color: var(--student-success);
}
.attendance-report__grid .is-absent strong {
  color: var(--student-danger);
}
.attendance-report__grid .is-late strong {
  color: var(--student-warning);
}
.attendance-report__grid .is-video strong {
  color: var(--student-primary);
}
.statistics-video-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) minmax(90px, 120px) 18px;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-width: 0;
  min-height: 76px;
  padding: 12px 15px;
  border: 0;
  border-bottom: 1px solid var(--student-border);
  color: inherit;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.statistics-video-row__icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 13px;
  color: var(--student-violet-700);
  background: var(--student-violet-100);
  font-size: 20px;
}
.statistics-video-row__progress {
  display: grid;
  min-width: 0;
  gap: 7px;
}
.statistics-video-row__progress strong {
  overflow: hidden;
  color: var(--student-primary);
  font-size: 11px;
  font-weight: 800;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 430px) {
  .statistics-score-row {
    grid-template-columns: minmax(76px, 1fr) minmax(64px, 0.9fr) 50px 15px;
    gap: 7px;
    padding: 10px 11px;
  }
  .statistics-score-value small {
    display: none;
  }
  .statistics-homework-row {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .attendance-report__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .statistics-video-row {
    grid-template-columns: 38px minmax(0, 1fr) 17px;
    gap: 10px;
    padding: 11px 12px;
  }
  .statistics-video-row__icon {
    width: 38px;
    height: 38px;
  }
  .statistics-video-row__progress {
    grid-column: 2 / -1;
  }
}
@media (min-width: 720px) {
  .statistics-summary-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
</style>
