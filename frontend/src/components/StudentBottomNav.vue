<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { House, VideoPlay, Service, User } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

interface Tab {
  label: string
  path: string
  icon: any
  /** Routes that belong to this tab (highlight when one matches). */
  matchPrefix: string[]
}

const tabs: Tab[] = [
  { label: '홈',     path: '/student/dashboard',      icon: House,     matchPrefix: ['/student/dashboard', '/student/daily-feedback', '/student/tests'] },
  { label: '영상',    path: '/student/videos',         icon: VideoPlay, matchPrefix: ['/student/videos'] },
  { label: '클리닉',  path: '/student/clinic',         icon: Service,   matchPrefix: ['/student/clinic'] },
  { label: '내 정보', path: '/student/stats',          icon: User,      matchPrefix: ['/student/stats'] },
]

const activeIndex = computed(() => {
  const path = route.path
  return tabs.findIndex(t => t.matchPrefix.some(p => path.startsWith(p)))
})

function go(tab: Tab) {
  if (route.path !== tab.path) router.push(tab.path)
}
</script>

<template>
  <nav class="bottom-nav" role="navigation" aria-label="학생 메뉴">
    <button
      v-for="(tab, i) in tabs"
      :key="tab.path"
      class="tab"
      :class="{ active: i === activeIndex }"
      type="button"
      @click="go(tab)"
    >
      <el-icon :size="22" class="icon">
        <component :is="tab.icon" />
      </el-icon>
      <span class="label">{{ tab.label }}</span>
    </button>
  </nav>
</template>

<style scoped>
.bottom-nav {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: #fff;
  border-top: 1px solid #ebeef5;
  /* Reserve space for the home indicator on iOS / gesture nav on Android */
  padding-bottom: env(safe-area-inset-bottom);
  box-shadow: 0 -1px 4px rgba(0, 0, 0, 0.04);
}

.tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  height: 56px;
  background: none;
  border: none;
  color: #909399;
  font-size: 11px;
  cursor: pointer;
  transition: color 0.15s;
  /* Generous tap target — keep minimum 44pt */
  -webkit-tap-highlight-color: transparent;
}

.tab:active {
  background: #f5f7fa;
}

.tab.active {
  color: #409eff;
}

.icon {
  display: block;
}

.label {
  font-weight: 500;
}
</style>
