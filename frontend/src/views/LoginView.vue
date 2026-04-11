<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authAPI } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const activeTab = ref('student')
const loading = ref(false)

// Student login form
const studentForm = ref({
  studentId: undefined as number | undefined,
  pin: ''
})

// Teacher login form
const teacherForm = ref({
  username: '',
  pin: ''
})

const handleStudentLogin = async () => {
  if (!studentForm.value.studentId) {
    ElMessage.error('학생 ID를 입력해주세요')
    return
  }
  if (!studentForm.value.pin) {
    ElMessage.error('PIN을 입력해주세요')
    return
  }

  loading.value = true
  try {
    const response = await authAPI.studentLogin(studentForm.value.studentId, studentForm.value.pin)
    const { data } = response

    if (data.userId) {
      await authStore.loadCurrentUser()
      ElMessage.success(data.message || '로그인 성공')
      // Redirect to student dashboard
      router.push('/student/dashboard')
    }
  } catch (error: any) {
    const message = error.response?.data?.message || '로그인에 실패했습니다'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

const handleTeacherLogin = async () => {
  if (!teacherForm.value.username) {
    ElMessage.error('아이디를 입력해주세요')
    return
  }
  if (!teacherForm.value.pin) {
    ElMessage.error('PIN을 입력해주세요')
    return
  }

  loading.value = true
  try {
    const response = await authAPI.teacherLogin(teacherForm.value.username, teacherForm.value.pin)
    const { data } = response

    if (data.userId) {
      // Hydrate store from server (gives us memberships + active context)
      await authStore.loadCurrentUser()
      await authStore.ensureActiveAcademy()
      ElMessage.success(data.message || '로그인 성공')
      // Redirect to teacher home
      router.push('/')
    }
  } catch (error: any) {
    const message = error.response?.data?.message || '로그인에 실패했습니다'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div style="min-height: 80vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #f5f7fa, #ffffff)">
    <div style="max-width: 500px; width: 100%; padding: 16px">
      <el-card shadow="always" style="border-radius: 16px">
        <template #header>
          <div style="text-align: center; padding: 16px 0">
            <div style="display: inline-block; padding: 16px; background: linear-gradient(135deg, #409eff, #67c23a); border-radius: 16px; margin-bottom: 16px">
              <el-icon size="48" color="white">
                <Lock />
              </el-icon>
            </div>
            <h2 style="font-size: 28px; font-weight: 700; margin: 0; color: #303133">로그인</h2>
            <p style="color: #909399; margin-top: 8px; font-size: 14px">학원 관리 시스템</p>
          </div>
        </template>

        <el-tabs v-model="activeTab" style="margin-top: 16px">
          <!-- Student Login Tab -->
          <el-tab-pane label="학생 로그인" name="student">
            <el-form :model="studentForm" label-position="left" label-width="60px" style="margin-top: 24px">
              <el-form-item label="학생 ID">
                <el-input
                  v-model="studentForm.studentId"
                  :controls="false"
                  placeholder="학생 ID를 입력하세요"
                  style="width: 100%"
                  :min="1"
                />
              </el-form-item>
              <el-form-item label="PIN">
                <el-input
                  v-model="studentForm.pin"
                  type="password"
                  placeholder="4자리 PIN을 입력하세요"
                  maxlength="4"
                  show-password
                  @keyup.enter="handleStudentLogin"
                />
              </el-form-item>
              <el-form-item>
                <el-button
                  type="primary"
                  style="width: 100%"
                  :loading="loading"
                  @click="handleStudentLogin"
                >
                  로그인
                </el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <!-- Teacher Login Tab -->
          <el-tab-pane label="선생님 로그인" name="teacher">
            <el-form :model="teacherForm"  label-position="left" label-width="60px" style="margin-top: 24px">
              <el-form-item label="아이디">
                <el-input
                  v-model="teacherForm.username"
                  placeholder="아이디를 입력하세요"
                />
              </el-form-item>
              <el-form-item label="PIN">
                <el-input
                  v-model="teacherForm.pin"
                  type="password"
                  placeholder="6자리 PIN을 입력하세요"
                  maxlength="6"
                  show-password
                  @keyup.enter="handleTeacherLogin"
                />
              </el-form-item>
              <el-form-item>
                <el-button
                  type="success"
                  style="width: 100%"
                  :loading="loading"
                  @click="handleTeacherLogin"
                >
                  로그인
                </el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>
