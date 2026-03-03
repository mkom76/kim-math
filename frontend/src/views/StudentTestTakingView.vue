<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { authAPI, testAPI, submissionAPI } from '@/api/client'
import type { Test, Question, AuthResponse } from '@/api/client'
import { useBreakpoint } from '@/composables/useBreakpoint'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const currentUser = ref<AuthResponse>({})
const test = ref<Test | null>(null)
const questions = ref<Question[]>([])
const answers = ref<Record<number, string>>({})

const { isMobile } = useBreakpoint()
const containerPadding = computed(() => isMobile.value ? '12px' : '24px')
const badgeSize = computed(() => isMobile.value ? '40px' : '60px')
const badgeFontSize = computed(() => isMobile.value ? '14px' : '24px')
const testTitleFontSize = computed(() => isMobile.value ? '16px' : '24px')
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

    // Submit answers - backend expects Map<Integer, String> (question number -> answer)
    await submissionAPI.submitAnswers(
      currentUser.value.userId!,
      testId.value,
      answers.value
    )

    ElMessage.success('시험이 성공적으로 제출되었습니다')
    router.push('/student/dashboard')
  } catch (error: any) {
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
  <div class="student-view" :style="{ padding: containerPadding, maxWidth: isMobile ? '100%' : '900px', margin: '0 auto' }">
    <el-card v-loading="loading" shadow="never">
      <!-- Test Header -->
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <div>
            <h1 :style="{ margin: 0, fontSize: testTitleFontSize, fontWeight: 700 }">{{ test?.title }}</h1>
            <p :style="{ margin: '8px 0 0', color: '#909399', fontSize: isMobile ? '11px' : '14px' }">
              총 {{ questions.length }}문제
            </p>
          </div>
          <el-button type="info" plain @click="handleCancel">
            취소
          </el-button>
        </div>
      </template>

      <!-- Student Info -->
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 24px"
      >
        <template #title>
          <div :style="{ fontSize: isMobile ? '11px' : '14px' }">
            <strong>응시자:</strong> {{ currentUser.name }} |
            <strong>시험:</strong> {{ test?.title }}
          </div>
        </template>
      </el-alert>

      <!-- Questions -->
      <el-card
        v-for="question in questions"
        :key="question.id"
        shadow="hover"
        :style="{ marginBottom: isMobile ? '16px' : '24px' }"
      >
        <div :style="{ display: 'flex', alignItems: isMobile ? 'stretch' : 'start', flexDirection: isMobile ? 'column' : 'row', gap: isMobile ? '12px' : '16px' }">
          <div :style="{ flexShrink: 0, width: badgeSize, height: badgeSize, background: 'linear-gradient(135deg, #409eff, #67c23a)', borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'center' }">
            <span :style="{ color: 'white', fontSize: badgeFontSize, fontWeight: 700 }">{{ question.number }}</span>
          </div>
          <div style="flex: 1">
            <div style="margin-bottom: 12px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
              <el-tag type="info" size="small">{{ question.points }}점</el-tag>
              <el-tag
                v-if="question.questionType === 'OBJECTIVE'"
                type="success"
                size="small"
              >
                <el-icon style="margin-right: 4px"><Select /></el-icon>
                객관식
              </el-tag>
              <el-tag
                v-else-if="question.questionType === 'SUBJECTIVE'"
                type="warning"
                size="small"
              >
                <el-icon style="margin-right: 4px"><Edit /></el-icon>
                주관식
              </el-tag>
              <el-tag
                v-else
                type="danger"
                size="small"
              >
                <el-icon style="margin-right: 4px"><Memo /></el-icon>
                서술형
              </el-tag>
            </div>
            <!-- 객관식: 라디오 버튼 -->
            <el-radio-group
              v-if="question.questionType === 'OBJECTIVE'"
              v-model="answers[question.number]"
              style="width: 100%"
            >
              <el-radio-button label="1">1</el-radio-button>
              <el-radio-button label="2">2</el-radio-button>
              <el-radio-button label="3">3</el-radio-button>
              <el-radio-button label="4">4</el-radio-button>
              <el-radio-button label="5">5</el-radio-button>
            </el-radio-group>

            <!-- 주관식: 텍스트 입력 -->
            <el-input
              v-else-if="question.questionType === 'SUBJECTIVE'"
              v-model="answers[question.number]"
              placeholder="답을 입력하세요"
              size="large"
            >
              <template v-if="!isMobile" #prepend>답:</template>
            </el-input>

            <!-- 서술형: 서면 제출 안내 -->
            <div v-else style="border: 1px dashed #d9d9d9; border-radius: 8px; padding: 20px; background: #fafafa; text-align: center">
              <el-icon :size="isMobile ? 28 : 36" color="#c0c4cc" style="display: block; margin: 0 auto 10px"><Memo /></el-icon>
              <div :style="{ fontSize: isMobile ? '13px' : '15px', fontWeight: 600, color: '#606266', marginBottom: '6px' }">
                서면으로 제출해주세요
              </div>
              <div :style="{ fontSize: isMobile ? '11px' : '13px', color: '#909399' }">
                이 문제는 온라인 입력 없이 선생님께 직접 제출합니다
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- Submit Button -->
      <div style="margin-top: 40px; padding-top: 24px; border-top: 1px solid #dcdfe6; text-align: center">
        <el-alert
          v-if="!allQuestionsAnswered"
          type="warning"
          :closable="false"
          style="margin-bottom: 16px"
        >
          모든 문제에 답을 입력해주세요
        </el-alert>

        <el-button
          type="primary"
          size="large"
          :loading="submitting"
          :disabled="!allQuestionsAnswered"
          @click="handleSubmit"
          :style="{ minWidth: isMobile ? '100%' : '200px' }"
        >
          제출하기
        </el-button>
      </div>
    </el-card>
  </div>
</template>

