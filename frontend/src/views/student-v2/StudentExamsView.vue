<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authAPI, lessonAPI, submissionAPI } from '@/api/client'
import type { Lesson, Submission } from '@/api/client'

interface StudentTest {
  id: number
  title: string
  className?: string
  lessonDate?: string
}

type ExamFilter = 'all' | 'untaken' | 'completed'

const router = useRouter()
const loading = ref(false)
const tests = ref<StudentTest[]>([])
const submissions = ref<Submission[]>([])
const activeFilter = ref<ExamFilter>('all')

const submissionByTestId = computed(() =>
  new Map(submissions.value.map(submission => [submission.testId, submission])),
)

const untakenTests = computed(() =>
  tests.value.filter(test => !submissionByTestId.value.has(test.id)),
)

const completedTests = computed(() =>
  tests.value.filter(test => submissionByTestId.value.has(test.id)),
)

const filteredTests = computed(() => {
  if (activeFilter.value === 'untaken') return untakenTests.value
  if (activeFilter.value === 'completed') return completedTests.value
  return tests.value
})

function getSubmission(testId: number) {
  return submissionByTestId.value.get(testId)
}

function getPendingEssayCount(testId: number) {
  return getSubmission(testId)?.pendingEssayCount ?? 0
}

function parseLocalDate(dateStr: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateStr)
  if (!match) return new Date(dateStr)
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
}

function formatLessonDate(dateStr?: string) {
  if (!dateStr) return '날짜 미정'
  return parseLocalDate(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}

function openTest(test: StudentTest) {
  router.push(getSubmission(test.id) ? `/student/tests/${test.id}/result` : `/student/tests/${test.id}`)
}

async function fetchExams() {
  loading.value = true
  try {
    const userResponse = await authAPI.getCurrentUser()
    if (!userResponse.data.userId) {
      await router.push('/login')
      return
    }

    const studentId = userResponse.data.userId
    const [lessonsResponse, submissionsResponse] = await Promise.all([
      lessonAPI.getLessonsByStudent(studentId),
      submissionAPI.getStudentSubmissions(studentId),
    ])

    const uniqueTests = new Map<number, StudentTest>()
    for (const lesson of lessonsResponse.data as Lesson[]) {
      if (lesson.testId == null || !lesson.testTitle) continue
      uniqueTests.set(lesson.testId, {
        id: lesson.testId,
        title: lesson.testTitle,
        className: lesson.className,
        lessonDate: lesson.lessonDate,
      })
    }
    tests.value = [...uniqueTests.values()]
    submissions.value = submissionsResponse.data.content || submissionsResponse.data
  } catch (error) {
    console.error('Failed to fetch student exams:', error)
    ElMessage.error('시험 정보를 불러오지 못했습니다')
  } finally {
    loading.value = false
  }
}

onMounted(fetchExams)
</script>

<template>
  <div class="student-page student-page--wide exams-page" v-loading="loading">
    <header class="student-page__header">
      <div>
        <p class="student-page__eyebrow">MY TESTS</p>
        <h1 class="student-page__title">시험</h1>
        <p class="student-page__subtitle">응시할 시험과 지난 결과를 한곳에서 확인해요.</p>
      </div>
      <span class="exams-header-icon"><el-icon><DocumentChecked /></el-icon></span>
    </header>

    <section class="exam-summary" aria-label="시험 현황">
      <button type="button" :class="{ 'is-active': activeFilter === 'untaken' }" @click="activeFilter = 'untaken'">
        <span>미응시 시험</span>
        <strong>{{ untakenTests.length }}<small>개</small></strong>
        <em>미응시만 보기 <el-icon><ArrowRight /></el-icon></em>
      </button>
      <button type="button" :class="{ 'is-active': activeFilter === 'completed' }" @click="activeFilter = 'completed'">
        <span>완료한 시험</span>
        <strong>{{ completedTests.length }}<small>개</small></strong>
        <em>완료 결과 보기 <el-icon><ArrowRight /></el-icon></em>
      </button>
    </section>

    <section aria-labelledby="exam-list-title">
      <div class="student-section-heading exam-list-heading">
        <div>
          <p class="student-section-heading__eyebrow">TEST LIST</p>
          <h2 id="exam-list-title" class="student-section-heading__title">시험 목록</h2>
        </div>
        <div class="exam-filters" aria-label="시험 상태 필터">
          <button type="button" :class="{ 'is-active': activeFilter === 'all' }" @click="activeFilter = 'all'">전체</button>
          <button type="button" :class="{ 'is-active': activeFilter === 'untaken' }" @click="activeFilter = 'untaken'">미응시</button>
          <button type="button" :class="{ 'is-active': activeFilter === 'completed' }" @click="activeFilter = 'completed'">완료</button>
        </div>
      </div>

      <div v-if="filteredTests.length" class="student-surface student-list">
        <button v-for="test in filteredTests" :key="test.id" class="student-list-row" type="button" @click="openTest(test)">
          <span class="student-list-row__icon" :class="{ 'is-complete': getSubmission(test.id) }">
            <el-icon><DocumentChecked v-if="getSubmission(test.id)" /><EditPen v-else /></el-icon>
          </span>
          <span class="student-list-row__content">
            <strong>{{ test.title }}</strong>
            <small>{{ formatLessonDate(test.lessonDate) }}<span v-if="test.className"> · {{ test.className }}</span></small>
            <span v-if="getPendingEssayCount(test.id)" class="exam-row__pending">
              서술형 {{ getPendingEssayCount(test.id) }}문제 채점 중
            </span>
          </span>
          <span class="student-list-row__trailing">
            <span v-if="getSubmission(test.id)?.totalScore != null" class="exam-row__score">
              {{ getSubmission(test.id)?.totalScore }}점
            </span>
            <span v-else class="student-pill student-pill--warning">미응시</span>
            <el-icon><ArrowRight /></el-icon>
          </span>
        </button>
      </div>
      <div v-else class="student-empty-state">
        <span class="student-empty-state__icon"><el-icon><Document /></el-icon></span>
        <h3>{{ activeFilter === 'untaken' ? '미응시 시험이 없어요' : activeFilter === 'completed' ? '완료한 시험이 없어요' : '등록된 시험이 없어요' }}</h3>
        <p>{{ activeFilter === 'untaken' ? '모든 시험을 완료했어요.' : '새 시험이 등록되면 이곳에서 확인할 수 있어요.' }}</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.exams-page { display: grid; min-width: 0; gap: 28px; }
.exams-header-icon { display: grid; flex: 0 0 48px; height: 48px; place-items: center; border-radius: 15px; color: var(--student-primary); background: var(--student-primary-soft); font-size: 24px; }
.exam-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.exam-summary button { display: grid; min-width: 0; min-height: 150px; padding: 18px; border: 1px solid rgba(36, 87, 214, .28); border-radius: var(--student-radius-lg); color: inherit; background: var(--student-surface); box-shadow: 0 7px 20px rgba(36, 87, 214, .09); font: inherit; text-align: left; cursor: pointer; transition: transform .16s ease, border-color .16s ease, box-shadow .16s ease, background-color .16s ease; }
.exam-summary button:hover { border-color: var(--student-primary); background: #f9fbff; box-shadow: 0 10px 24px rgba(36, 87, 214, .14); transform: translateY(-2px); }
.exam-summary button:active { transform: translateY(0) scale(.985); }
.exam-summary button:focus-visible { outline: 3px solid rgba(36, 87, 214, .25); outline-offset: 3px; }
.exam-summary button.is-active { border-color: var(--student-primary); background: #f7f9ff; box-shadow: 0 0 0 2px var(--student-primary-soft); }
.exam-summary span { color: var(--student-muted); font-size: 13px; font-weight: 700; }
.exam-summary strong { align-self: end; margin-top: 12px; color: var(--student-ink); font-size: 30px; font-weight: 850; line-height: 1; }
.exam-summary strong small { margin-left: 3px; font-size: 14px; }
.exam-summary em { display: flex; align-items: center; justify-content: space-between; gap: 6px; margin-top: 8px; color: var(--student-primary); font-size: 12px; font-style: normal; font-weight: 800; }
.exam-list-heading { align-items: center; }
.exam-filters { display: flex; gap: 5px; padding: 4px; border-radius: 999px; background: var(--student-primary-soft); }
.exam-filters button { min-height: 36px; padding: 0 12px; border: 0; border-radius: 999px; color: var(--student-muted); background: transparent; font: inherit; font-size: 12px; font-weight: 750; cursor: pointer; }
.exam-filters button.is-active { color: var(--student-primary-strong); background: #fff; box-shadow: 0 2px 8px rgba(28, 46, 78, .08); }
.exam-row__pending { width: fit-content; margin-top: 3px; color: var(--student-warning); font-size: 12px; font-weight: 700; }
.exam-row__score { color: var(--student-primary); font-size: 15px; font-weight: 800; white-space: nowrap; }
@media (max-width: 420px) {
  .exam-list-heading { align-items: flex-start; flex-direction: column; }
  .exam-filters { width: 100%; }
  .exam-filters button { flex: 1; }
  .exam-summary button { min-height: 136px; padding: 15px; }
}
</style>
