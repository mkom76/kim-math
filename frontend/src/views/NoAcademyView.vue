<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const router = useRouter()
const authStore = useAuthStore()

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div style="min-height: 80vh; display: flex; align-items: center; justify-content: center;">
    <el-card shadow="always" style="max-width: 480px; border-radius: 16px;">
      <div style="padding: 32px; text-align: center;">
        <h2 style="margin-bottom: 16px;">소속된 학원이 없습니다</h2>
        <p style="color: #909399; margin-bottom: 24px;">
          학원 관리자에게 문의하여 등록을 요청하세요.
        </p>
        <el-button type="primary" @click="handleLogout">로그아웃</el-button>
      </div>
    </el-card>
  </div>
</template>
