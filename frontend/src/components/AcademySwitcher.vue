<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { ArrowDown, Check } from '@element-plus/icons-vue'

const authStore = useAuthStore()

async function onSwitch(academyId: number | string | number[]) {
  const id = typeof academyId === 'number' ? academyId : Number(academyId)
  if (!id || id === authStore.activeAcademyId) return
  await authStore.switchAcademy(id)
  // Hard reload — safest way to reset all view-level caches and re-fetch with new tenant context
  window.location.reload()
}
</script>

<template>
  <div v-if="authStore.role === 'TEACHER' && authStore.memberships.length > 0">
    <!-- Single membership: show plain text -->
    <span
      v-if="authStore.memberships.length === 1"
      class="academy-switcher single"
    >
      {{ authStore.activeAcademy?.academyName ?? authStore.memberships[0].academyName }}
      <el-tag v-if="authStore.isAdmin" size="small" type="warning" style="margin-left: 6px;">관리자</el-tag>
    </span>

    <!-- Multiple memberships: dropdown -->
    <el-dropdown
      v-else
      trigger="click"
      @command="onSwitch"
    >
      <span class="academy-switcher clickable">
        {{ authStore.activeAcademy?.academyName ?? '학원 선택' }}
        <el-tag v-if="authStore.isAdmin" size="small" type="warning" style="margin-left: 6px;">관리자</el-tag>
        <el-icon style="margin-left: 4px;"><ArrowDown /></el-icon>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="m in authStore.memberships"
            :key="m.academyId"
            :command="m.academyId"
            :disabled="m.academyId === authStore.activeAcademyId"
          >
            <el-icon v-if="m.academyId === authStore.activeAcademyId"><Check /></el-icon>
            <span style="margin-left: 4px;">{{ m.academyName }}</span>
            <el-tag
              v-if="m.role === 'ACADEMY_ADMIN'"
              size="small"
              type="warning"
              style="margin-left: 6px;"
            >관리자</el-tag>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<style scoped>
.academy-switcher {
  display: inline-flex;
  align-items: center;
  font-size: 14px;
  color: #303133;
  padding: 4px 8px;
  border-radius: 4px;
}

.academy-switcher.clickable {
  cursor: pointer;
  background-color: #f5f7fa;
}

.academy-switcher.clickable:hover {
  background-color: #ecf5ff;
}

.academy-switcher.single {
  background-color: transparent;
}
</style>
