<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authAPI } from '@/api/client'
import { useRouter } from 'vue-router'

const router = useRouter()

const loading = ref(false)
const pinForm = ref({
  currentPin: '',
  newPin: '',
  confirmPin: ''
})

const handleChangePIN = async () => {
  if (!pinForm.value.currentPin || !pinForm.value.newPin || !pinForm.value.confirmPin) {
    ElMessage.error('모든 필드를 입력해주세요')
    return
  }

  if (pinForm.value.newPin !== pinForm.value.confirmPin) {
    ElMessage.error('새 PIN이 일치하지 않습니다')
    return
  }

  if (pinForm.value.newPin.length < 4 || pinForm.value.newPin.length > 6) {
    ElMessage.error('PIN은 4~6자리여야 합니다')
    return
  }

  loading.value = true
  try {
    const response = await authAPI.changePin(pinForm.value.currentPin, pinForm.value.newPin)
    ElMessage.success(response.data.message || 'PIN이 성공적으로 변경되었습니다')

    // 폼 초기화
    pinForm.value = {
      currentPin: '',
      newPin: '',
      confirmPin: ''
    }
  } catch (error: any) {
    const message = error.response?.data?.message || 'PIN 변경에 실패했습니다'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <!-- Header -->
    <el-card shadow="never" style="margin-bottom: 24px">
      <div>
        <h1 style="margin: 0; font-size: 28px; font-weight: 600; color: #303133; display: flex; align-items: center; gap: 12px">
          <el-icon size="32" color="#409eff">
            <Setting />
          </el-icon>
          설정
        </h1>
        <p style="margin: 8px 0 0; color: #909399">계정 설정을 관리합니다</p>
      </div>
    </el-card>

    <!-- AI Feedback Prompt Settings -->
    <el-card shadow="never" style="margin-bottom: 24px; cursor: pointer" @click="router.push('/settings/feedback-prompt')">
      <div style="display: flex; justify-content: space-between; align-items: center">
        <div style="display: flex; align-items: center; gap: 12px">
          <el-icon size="24" color="#409eff"><MagicStick /></el-icon>
          <div>
            <div style="font-size: 16px; font-weight: 600; color: #303133">AI 피드백 프롬프트 설정</div>
            <div style="font-size: 13px; color: #909399; margin-top: 4px">AI 피드백 생성에 사용되는 프롬프트와 예시 개수를 설정합니다</div>
          </div>
        </div>
        <el-icon size="20" color="#c0c4cc"><ArrowRight /></el-icon>
      </div>
    </el-card>

    <!-- PIN Change Card -->
    <el-card shadow="never">
      <template #header>
        <div style="font-size: 18px; font-weight: 600; color: #303133">
          <el-icon style="margin-right: 8px"><Lock /></el-icon>
          PIN 변경
        </div>
      </template>

      <div style="max-width: 500px">
        <el-form :model="pinForm" label-width="120px" label-position="left">
          <el-form-item label="현재 PIN">
            <el-input
              v-model="pinForm.currentPin"
              type="password"
              placeholder="현재 PIN을 입력하세요"
              maxlength="6"
              show-password
            />
          </el-form-item>

          <el-form-item label="새 PIN">
            <el-input
              v-model="pinForm.newPin"
              type="password"
              placeholder="새 PIN을 입력하세요 (4~6자리)"
              maxlength="6"
              show-password
            />
          </el-form-item>

          <el-form-item label="새 PIN 확인">
            <el-input
              v-model="pinForm.confirmPin"
              type="password"
              placeholder="새 PIN을 다시 입력하세요"
              maxlength="6"
              show-password
              @keyup.enter="handleChangePIN"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              :loading="loading"
              @click="handleChangePIN"
              style="width: 100%"
            >
              PIN 변경
            </el-button>
          </el-form-item>
        </el-form>

        <el-alert
          type="info"
          :closable="false"
          style="margin-top: 16px"
        >
          <template #title>
            <div style="font-size: 13px">
              <strong>보안 안내:</strong><br>
              • PIN은 로그인 시 사용됩니다<br>
              • 학생: 4자리, 선생님: 6자리<br>
              • 주기적으로 변경하는 것을 권장합니다
            </div>
          </template>
        </el-alert>
      </div>
    </el-card>
  </div>
</template>
