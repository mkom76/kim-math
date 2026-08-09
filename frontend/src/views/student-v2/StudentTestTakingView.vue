<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { authAPI, testAPI, submissionAPI } from '@/api/client'
import type { Test, Question, AuthResponse } from '@/api/client'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const currentUser = ref<AuthResponse>({})
const test = ref<Test | null>(null)
const questions = ref<Question[]>([])
const answers = ref<Record<number, string>>({})

const openVideo = (url?: string | null) => {
  if (!url) return
  window.open(url, '_blank', 'noopener')
}

const testId = computed(() => Number(route.params.id))

const fetchTestData = async () => {
  loading.value = true
  try {
    // Fetch current user
    const userResponse = await authAPI.getCurrentUser()
    currentUser.value = userResponse.data

    // Fetch test info
    const testResponse = await testAPI.getTest(testId.value)
    test.value = testResponse.data

    // Fetch test questions
    const questionsResponse = await testAPI.getTestQuestions(testId.value)
    questions.value = questionsResponse.data

    // Initialize answers object
    questions.value.forEach(q => {
      answers.value[q.number] = ''
    })
  } catch (error) {
    console.error('Failed to fetch test data:', error)
    ElMessage.error('시험 정보를 불러오는데 실패했습니다')
    router.push('/student/dashboard')
  } finally {
    loading.value = false
  }
}

const allQuestionsAnswered = computed(() => {
  return questions.value.every(q => {
    if (q.questionType === 'ESSAY') return true
    return answers.value[q.number]?.trim() !== ''
  })
})

const answeredCount = computed(() => questions.value.filter(question => {
  if (question.questionType === 'ESSAY') return true
  return answers.value[question.number]?.trim() !== ''
}).length)

const answerProgress = computed(() => questions.value.length
  ? Math.round((answeredCount.value / questions.value.length) * 100)
  : 0)

const handleSubmit = async () => {
  if (!allQuestionsAnswered.value) {
    ElMessage.warning('모든 문제에 답을 입력해주세요')
    return
  }

  try {
    await ElMessageBox.confirm(
      '제출한 후에는 수정할 수 없습니다. 제출하시겠습니까?',
      '시험 제출',
      {
        confirmButtonText: '제출',
        cancelButtonText: '취소',
        type: 'warning',
      }
    )

    submitting.value = true

    // Backend expects Map<Integer, String> (question number -> answer)
    await submissionAPI.submitMyAnswers(testId.value, answers.value)

    ElMessage.success('시험이 성공적으로 제출되었습니다')
    router.push('/student/dashboard')
  } catch (error: unknown) {
    if (error !== 'cancel') {
      console.error('Failed to submit test:', error)
      ElMessage.error('시험 제출에 실패했습니다')
    }
  } finally {
    submitting.value = false
  }
}

const handleCancel = async () => {
  try {
    await ElMessageBox.confirm(
      '작성한 답안이 저장되지 않습니다. 취소하시겠습니까?',
      '시험 취소',
      {
        confirmButtonText: '확인',
        cancelButtonText: '계속 작성',
        type: 'warning',
      }
    )
    router.push('/student/dashboard')
  } catch {
    // User clicked cancel - do nothing
  }
}

onMounted(() => {
  fetchTestData()
})
</script>

<template>
  <div class="student-page student-page--wide test-taking-page" v-loading="loading">
    <header class="test-header">
      <button class="student-icon-button" aria-label="시험 나가기" @click="handleCancel"><el-icon><Close /></el-icon></button>
      <div class="test-header__copy">
        <p>{{ currentUser.name }} · {{ answeredCount }}/{{ questions.length }} 답변</p>
        <h1>{{ test?.title || '시험 불러오는 중' }}</h1>
      </div>
      <span class="test-header__progress">{{ answerProgress }}%</span>
    </header>

    <div class="student-progress" role="progressbar" :aria-valuenow="answerProgress" aria-valuemin="0" aria-valuemax="100">
      <span :style="{ width: `${answerProgress}%` }" />
    </div>

    <div class="test-guide">
      <el-icon><InfoFilled /></el-icon>
      <span>시험지의 문제를 풀고 아래에 답만 입력해 주세요.</span>
    </div>

    <main class="question-list">
      <article v-for="question in questions" :key="question.id" class="question-card">
        <div class="question-card__header">
          <span class="question-number">{{ question.number }}</span>
          <div class="question-meta">
            <strong>{{ question.questionType === 'OBJECTIVE' ? '객관식' : question.questionType === 'SUBJECTIVE' ? '주관식' : '서술형' }}</strong>
            <span>{{ question.points }}점<span v-if="question.textbookProblem?.topic"> · {{ question.textbookProblem.topic }}</span></span>
          </div>
          <button v-if="question.textbookProblem?.videoLink" class="question-video" @click="openVideo(question.textbookProblem.videoLink)">
            <el-icon><VideoPlay /></el-icon><span>해설</span>
          </button>
        </div>

        <el-radio-group v-if="question.questionType === 'OBJECTIVE'" v-model="answers[question.number]" class="answer-options" :aria-label="`${question.number}번 답`">
          <el-radio-button v-for="choice in 5" :key="choice" :label="String(choice)">{{ choice }}</el-radio-button>
        </el-radio-group>

        <el-input
          v-else-if="question.questionType === 'SUBJECTIVE'"
          v-model="answers[question.number]"
          placeholder="정답을 입력하세요"
          size="large"
          inputmode="text"
          :aria-label="`${question.number}번 정답`"
        />

        <div v-else class="essay-guide">
          <span class="essay-guide__icon"><el-icon><Memo /></el-icon></span>
          <div><strong>답안지에 직접 작성해 주세요</strong><p>이 문제는 선생님이 확인한 뒤 점수가 반영됩니다.</p></div>
        </div>
      </article>
    </main>

    <div class="student-sticky-action test-submit">
      <div v-if="!allQuestionsAnswered" class="test-submit__status">
        <el-icon><WarningFilled /></el-icon> 답하지 않은 문제가 있어요
      </div>
      <el-button type="primary" size="large" :loading="submitting" :disabled="!allQuestionsAnswered" @click="handleSubmit">
        {{ allQuestionsAnswered ? '답안 제출하기' : `${questions.length - answeredCount}문제 더 풀기` }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.test-taking-page { display: grid; gap: 18px; padding-bottom: calc(124px + env(safe-area-inset-bottom)); }
.test-header { display: grid; grid-template-columns: 46px 1fr auto; align-items: center; gap: 11px; }
.test-header__copy { min-width: 0; }
.test-header__copy p { margin: 0 0 3px; color: var(--student-muted); font-size: 12px; font-weight: 650; }
.test-header__copy h1 { overflow: hidden; margin: 0; color: var(--student-ink); font-size: 18px; font-weight: 800; letter-spacing: -.35px; text-overflow: ellipsis; white-space: nowrap; }
.test-header__progress { color: var(--student-primary); font-size: 14px; font-weight: 800; }
.test-guide { display: flex; align-items: flex-start; gap: 9px; padding: 12px 14px; border-radius: 13px; color: #3c4a68; background: #edf2ff; font-size: 13px; line-height: 1.5; }
.test-guide .el-icon { flex: 0 0 auto; margin-top: 2px; color: var(--student-primary); }
.question-list { display: grid; gap: 14px; }
.question-card { display: grid; gap: 18px; padding: 18px; border: 1px solid var(--student-border); border-radius: 19px; background: var(--student-surface); box-shadow: var(--student-shadow-sm); }
.question-card__header { display: flex; align-items: center; gap: 12px; }
.question-number { display: grid; flex: 0 0 42px; height: 42px; place-items: center; border-radius: 13px; color: #fff; background: var(--student-primary); font-size: 17px; font-weight: 850; }
.question-meta { display: grid; flex: 1; gap: 2px; min-width: 0; }
.question-meta strong { color: var(--student-ink); font-size: 15px; }
.question-meta span { overflow: hidden; color: var(--student-muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.question-video { display: flex; min-height: 44px; align-items: center; gap: 5px; padding: 0 9px; border: 0; border-radius: 10px; color: var(--student-primary); background: var(--student-primary-soft); font: inherit; font-size: 12px; font-weight: 750; cursor: pointer; }
.answer-options { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; width: 100%; }
.answer-options :deep(.el-radio-button) { width: 100%; }
.answer-options :deep(.el-radio-button__inner) { display: grid; width: 100%; min-width: 0; height: 50px; padding: 0; place-items: center; border: 1px solid var(--student-border) !important; border-radius: 13px !important; box-shadow: none !important; font-size: 16px; font-weight: 800; }
.answer-options :deep(.el-radio-button.is-active .el-radio-button__inner) { border-color: var(--student-primary) !important; color: #fff; background: var(--student-primary); }
.essay-guide { display: flex; align-items: center; gap: 12px; padding: 15px; border: 1px dashed #c7d2e3; border-radius: 14px; background: var(--student-surface-soft); }
.essay-guide__icon { display: grid; flex: 0 0 38px; height: 38px; place-items: center; border-radius: 11px; color: var(--student-primary); background: var(--student-primary-soft); font-size: 19px; }
.essay-guide strong { color: var(--student-ink); font-size: 14px; }
.essay-guide p { margin: 3px 0 0; color: var(--student-muted); font-size: 12px; line-height: 1.45; }
.test-submit { display: grid; gap: 9px; }
.test-submit__status { display: flex; align-items: center; justify-content: center; gap: 6px; color: var(--student-warning); font-size: 12px; font-weight: 700; }
@media (min-width: 720px) { .question-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
