<script setup lang="ts">
import { RouterView } from 'vue-router'
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authAPI } from '@/api/client'
import type { AuthResponse } from '@/api/client'

const router = useRouter()
const route = useRoute()

const activeIndex = ref('/')
const currentUser = ref<AuthResponse>({})
const drawerVisible = ref(false)

const isLoginPage = computed(() => route.path === '/login')
const isTeacherRoute = computed(() => currentUser.value.role === 'TEACHER' && !isLoginPage.value)
const isStudentRoute = computed(() => currentUser.value.role === 'STUDENT')

const fetchCurrentUser = async () => {
  try {
    const response = await authAPI.getCurrentUser()
    currentUser.value = response.data
  } catch (error) {
    // User not logged in
  }
}

const handleSelect = (index: string) => {
  router.push(index)
  drawerVisible.value = false // Close drawer after navigation
}

const handleLogout = async () => {
  try {
    await authAPI.logout()
    currentUser.value = {}
    ElMessage.success('로그아웃 되었습니다')
    router.push('/login')
  } catch (error) {
    console.error('Logout failed:', error)
    ElMessage.error('로그아웃에 실패했습니다')
  }
}

onMounted(() => {
  fetchCurrentUser()
})

// Refetch user when route changes (e.g., after login)
watch(() => route.path, () => {
  fetchCurrentUser()
})
</script>

<template>
  <el-container style="min-height: 100vh; min-height: 100dvh; background-color: #f5f7fa; padding-top: env(safe-area-inset-top); padding-bottom: env(safe-area-inset-bottom)">
    <!-- Header (Teacher only) -->
    <el-header v-if="isTeacherRoute" style="background-color: #fff; border-bottom: 1px solid #dcdfe6; padding: 0 20px">
      <div style="display: flex; align-items: center; justify-content: space-between; height: 100%">
        <!-- Left: Hamburger Menu + Logo -->
        <div style="display: flex; align-items: center; gap: 16px">
          <el-button
            type="primary"
            circle
            @click="drawerVisible = true"
            style="width: 40px; height: 40px"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="6" x2="21" y2="6"></line>
              <line x1="3" y1="12" x2="21" y2="12"></line>
              <line x1="3" y1="18" x2="21" y2="18"></line>
            </svg>
          </el-button>

          <div style="display: flex; align-items: center; gap: 12px; cursor: pointer" @click="router.push('/')">
            <el-icon size="32" color="#409eff">
              <School />
            </el-icon>
            <div style="line-height: 1.2">
              <div style="font-size: 18px; font-weight: 700; background: linear-gradient(135deg, #409eff, #67c23a); -webkit-background-clip: text; -webkit-text-fill-color: transparent; letter-spacing: 0.5px">
                SUHUI
              </div>
              <div style="font-size: 13px; font-weight: 600; color: #606266; letter-spacing: 1px">
                SECRETARY
              </div>
            </div>
          </div>
        </div>

        <!-- Right: User Info + Logout -->
        <div style="display: flex; align-items: center; gap: 12px">
          <span style="color: #606266; font-size: 14px">{{ currentUser.name }}님</span>
          <el-button type="danger" size="small" @click="handleLogout">
            로그아웃
          </el-button>
        </div>
      </div>
    </el-header>

    <!-- Navigation Drawer -->
    <el-drawer
      v-model="drawerVisible"
      title="메뉴"
      direction="ltr"
      size="280px"
    >
      <el-menu
        :default-active="route.path"
        @select="handleSelect"
      >
        <el-menu-item index="/students">
          <el-icon><User /></el-icon>
          <span>학생 관리</span>
        </el-menu-item>

        <el-menu-item index="/tests">
          <el-icon><Document /></el-icon>
          <span>시험 관리</span>
        </el-menu-item>

        <el-menu-item index="/homeworks">
          <el-icon><Notebook /></el-icon>
          <span>숙제 관리</span>
        </el-menu-item>

        <el-menu-item index="/lessons">
          <el-icon><Calendar /></el-icon>
          <span>수업 관리</span>
        </el-menu-item>

        <el-menu-item index="/clinics">
          <el-icon><MagicStick /></el-icon>
          <span>클리닉 관리</span>
        </el-menu-item>

        <el-divider style="margin: 12px 0" />

        <el-menu-item index="/academies">
          <el-icon><OfficeBuilding /></el-icon>
          <span>학원 관리</span>
        </el-menu-item>

        <el-menu-item index="/academy-classes">
          <el-icon><Grid /></el-icon>
          <span>반 관리</span>
        </el-menu-item>

        <el-divider style="margin: 12px 0" />

        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>설정</span>
        </el-menu-item>
      </el-menu>
    </el-drawer>

    <!-- Main Content -->
    <el-main :style="{ padding: isLoginPage ? '0' : '24px', backgroundColor: isLoginPage ? '#fff' : '#f5f7fa' }">
      <RouterView />
    </el-main>
  </el-container>
</template>

<style scoped>
.el-menu-item.is-active {
  background-color: #ecf5ff !important;
  color: #409eff !important;
}

.el-header {
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

/* Drawer menu styling */
:deep(.el-menu) {
  border-right: none;
}

:deep(.el-drawer__header) {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #dcdfe6;
}

:deep(.el-drawer__body) {
  padding: 0;
}
</style>
