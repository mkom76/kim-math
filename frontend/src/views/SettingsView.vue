<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePinChange } from '@/composables/usePinChange'
import { useStudentUiMode } from '@/composables/useStudentUiMode'

const router = useRouter()
const authStore = useAuthStore()
const { enablePreview, hasSeenPreview } = useStudentUiMode()

const { loading, pinForm, pinLength, handleChangePIN } = usePinChange()

const startPreview = async () => {
  enablePreview()
  await router.replace('/student/dashboard')
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

    <el-card
      v-if="authStore.role === 'STUDENT'"
      shadow="never"
      style="margin-bottom: 24px"
    >
      <div style="display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap">
        <div style="min-width: 0">
          <div style="display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; color: #303133">
            <el-icon color="#409eff"><MagicStick /></el-icon>
            새로운 학생 화면
          </div>
          <div style="margin-top: 5px; color: #909399; font-size: 13px">
            새 디자인을 체험하고 언제든 기존 화면으로 돌아올 수 있습니다.
          </div>
        </div>
        <span class="preview-settings-action">
          <el-button size="small" plain type="primary" @click="startPreview">
            새 UI 체험하기
          </el-button>
          <span v-if="!hasSeenPreview" class="preview-settings-action__dot" aria-hidden="true" />
        </span>
      </div>
    </el-card>

    <!-- AI Feedback Prompt Settings -->
    <el-card
      v-if="authStore.role === 'TEACHER'"
      data-test="feedback-prompt-settings"
      shadow="never"
      style="margin-bottom: 24px; cursor: pointer"
      @click="router.push('/settings/feedback-prompt')"
    >
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

    <el-card shadow="never" style="margin-bottom: 24px">
      <template #header>
        <div style="font-size: 18px; font-weight: 600; color: #303133">개인정보 및 지원</div>
      </template>
      <div style="display: flex; justify-content: space-between; align-items: center; gap: 16px">
        <div>
          <div style="font-weight: 600">개인정보처리방침</div>
          <div style="margin-top: 4px; color: #909399; font-size: 13px">
            수집 정보, 이용 목적, 보유 기간과 삭제 요청 방법을 확인합니다.
          </div>
        </div>
        <el-button @click="router.push('/privacy')">보기</el-button>
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
              :maxlength="pinLength"
              show-password
            />
          </el-form-item>

          <el-form-item label="새 PIN">
            <el-input
              v-model="pinForm.newPin"
              type="password"
              :placeholder="`새 PIN을 입력하세요 (${pinLength}자리)`"
              :maxlength="pinLength"
              show-password
            />
          </el-form-item>

          <el-form-item label="새 PIN 확인">
            <el-input
              v-model="pinForm.confirmPin"
              type="password"
              placeholder="새 PIN을 다시 입력하세요"
              :maxlength="pinLength"
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

<style scoped>
.preview-settings-action {
  position: relative;
  display: inline-flex;
}

.preview-settings-action__dot {
  position: absolute;
  z-index: 1;
  top: 4px;
  right: 5px;
  width: 8px;
  height: 8px;
  border: 1.5px solid #fff;
  border-radius: 50%;
  background: #f56c6c;
  pointer-events: none;
}
</style>
