<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { feedbackPromptTemplateAPI, authAPI, type FeedbackPromptTemplate } from '../api/client'

const loading = ref(false)
const saving = ref(false)
const currentUser = ref<any>(null)

const template = ref<FeedbackPromptTemplate>({
  teacherId: 0,
  systemPrompt: '',
  fewShotCount: 3,
  isActive: true
})

const DEFAULT_SYSTEM_PROMPT = `당신은 학원 선생님의 시험 피드백 작성을 돕는 AI 어시스턴트입니다.
학생의 시험 결과 데이터를 분석하여 학부모에게 전달할 피드백을 작성해주세요.

## 피드백 작성 가이드

아래 규칙을 참고하되, 학생 데이터에 맞게 자연스럽게 작성하세요.
여러 규칙이 동시에 해당되면 조합하여 하나의 통합 피드백을 작성합니다.

1. 정답률 80% 이상인 쉬운 문제를 틀렸을 때
   → "N번은 계산 실수를 한 것 같아 아쉽습니다."

2. 보통 난이도(정답률 40~80%)부터 오답이 많을 때
   → "개념은 잘 이해하고 있으나 응용문제에서 풀이가 막히는 것 같습니다."

3. 고난도 문제(정답률 20% 미만) 위주로 틀렸을 때
   → "N번, N번을 주어진 시간 안에 다 풀지 못했습니다. 다른 문제들을 빠르게 풀고 고난도 문제 풀이를 위한 시간을 확보할 수 있어야 합니다."

4. 서술형 점수가 0점일 때
   → "서술형 답안을 작성하지 않았습니다. 시험 시간을 전략적으로 운용하는 연습이 더 필요합니다."

5. 서술형에서 부분감점이 있을 때
   → 서술형 채점 코멘트를 바탕으로 구체적 피드백 작성

6. 85점 이상을 받았을 때
   → "시험을 잘 봤습니다." 를 포함하되, 틀린 문제가 있으면 해당 부분도 언급

## 작성 원칙
- 2~4문장으로 간결하게 작성
- 학부모가 읽는다는 점을 고려하여 정중한 존댓말 사용
- 구체적인 문제 번호를 언급하여 신뢰감 제공
- 격려와 개선점을 균형 있게 포함`

const fetchTemplate = async () => {
  loading.value = true
  try {
    const userRes = await authAPI.getCurrentUser()
    currentUser.value = userRes.data

    if (!currentUser.value?.userId) return

    template.value = await feedbackPromptTemplateAPI.getByTeacher(currentUser.value.userId)
    template.value.teacherId = currentUser.value.userId
  } catch (error) {
    ElMessage.error('프롬프트 설정을 불러오는데 실패했습니다')
  } finally {
    loading.value = false
  }
}

const saveTemplate = async () => {
  saving.value = true
  try {
    const saved = await feedbackPromptTemplateAPI.save(template.value.teacherId, template.value)
    template.value = saved
    ElMessage.success('저장되었습니다')
  } catch (error) {
    ElMessage.error('저장에 실패했습니다')
  } finally {
    saving.value = false
  }
}

const resetToDefault = () => {
  template.value.systemPrompt = DEFAULT_SYSTEM_PROMPT
  template.value.fewShotCount = 3
}

onMounted(() => {
  fetchTemplate()
})
</script>

<template>
  <div v-loading="loading" style="padding: 24px; max-width: 900px; margin: 0 auto">
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <h2 style="margin: 0; font-size: 20px">AI 피드백 프롬프트 설정</h2>
          <el-switch
            v-model="template.isActive"
            active-text="AI 활성화"
            inactive-text="비활성화"
          />
        </div>
      </template>

      <el-form label-position="top">
        <el-form-item label="시스템 프롬프트">
          <el-input
            v-model="template.systemPrompt"
            type="textarea"
            :rows="20"
            placeholder="AI에게 전달할 피드백 작성 가이드를 입력하세요..."
            style="font-family: monospace"
          />
        </el-form-item>

        <el-form-item label="Few-shot 예시 개수">
          <el-input-number
            v-model="template.fewShotCount"
            :min="0"
            :max="10"
            :step="1"
          />
          <span style="margin-left: 12px; color: #909399; font-size: 13px">
            이전에 작성한 피드백 중 최근 N개를 AI에게 예시로 제공합니다
          </span>
        </el-form-item>

        <div style="display: flex; gap: 12px; justify-content: flex-end; margin-top: 24px">
          <el-button @click="resetToDefault">기본값으로 초기화</el-button>
          <el-button type="primary" :loading="saving" @click="saveTemplate">저장</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>
