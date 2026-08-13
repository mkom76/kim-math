<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { feedbackPromptTemplateAPI, authAPI, type FeedbackPromptTemplate } from '../api/client'

const loading = ref(false)
const saving = ref(false)
const resetting = ref(false)
const currentUser = ref<any>(null)

const template = ref<FeedbackPromptTemplate>({
  teacherId: 0,
  systemPrompt: '',
  fewShotCount: 3,
  isActive: true
})

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

const resetToDefault = async () => {
  resetting.value = true
  try {
    const defaults = await feedbackPromptTemplateAPI.getDefault()
    template.value.systemPrompt = defaults.systemPrompt
    template.value.fewShotCount = defaults.fewShotCount
  } catch (error) {
    ElMessage.error('기본 프롬프트를 불러오는데 실패했습니다')
  } finally {
    resetting.value = false
  }
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
            active-text="커스텀 프롬프트 사용"
            inactive-text="기본 프롬프트 사용"
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
          <el-button :loading="resetting" @click="resetToDefault">기본값으로 초기화</el-button>
          <el-button type="primary" :loading="saving" @click="saveTemplate">저장</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>
