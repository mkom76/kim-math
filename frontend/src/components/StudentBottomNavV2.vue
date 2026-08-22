<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { House, DocumentChecked, Service, MoreFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

interface Tab {
  label: string
  path: string
  icon: Component
  /** Routes that belong to this tab (highlight when one matches). */
  matchPrefix: string[]
}

const tabs: Tab[] = [
  {
    label: '홈',
    path: '/student/dashboard',
    icon: House,
    matchPrefix: ['/student/dashboard', '/student/daily-feedback', '/student/videos'],
  },
  {
    label: '시험',
    path: '/student/exams',
    icon: DocumentChecked,
    matchPrefix: ['/student/exams', '/student/tests'],
  },
  { label: '클리닉', path: '/student/clinic', icon: Service, matchPrefix: ['/student/clinic'] },
  {
    label: '더보기',
    path: '/student/more',
    icon: MoreFilled,
    matchPrefix: [
      '/student/more',
      '/student/statistics',
      '/student/stats',
      '/student/notifications',
    ],
  },
]

const activeIndex = computed(() => {
  const path = route.path
  return tabs.findIndex((t) => t.matchPrefix.some((p) => path.startsWith(p)))
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
      :aria-current="i === activeIndex ? 'page' : undefined"
      :aria-label="`${tab.label} 화면으로 이동`"
      @click="go(tab)"
    >
      <span class="icon-wrap">
        <el-icon :size="23" class="icon">
          <component :is="tab.icon" />
        </el-icon>
      </span>
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
  z-index: var(--student-z-navigation);
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  min-height: calc(72px + env(safe-area-inset-bottom));
  padding: 6px 8px env(safe-area-inset-bottom);
  border-top: 1px solid rgba(223, 229, 236, 0.92);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 -10px 28px rgba(28, 46, 78, 0.08);
  backdrop-filter: blur(20px);
}

.tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-width: 0;
  min-height: 60px;
  padding: 4px 2px;
  background: none;
  border: none;
  border-radius: 16px;
  color: var(--student-muted);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.15s;
  /* Generous tap target — keep minimum 44pt */
  -webkit-tap-highlight-color: transparent;
}

.tab:active {
  background: var(--student-background);
}

.tab.active {
  color: var(--student-primary);
}

.icon-wrap {
  display: grid;
  width: 44px;
  height: 32px;
  place-items: center;
  border-radius: 999px;
  transition:
    background-color 0.2s ease,
    transform 0.2s ease;
}

.tab.active .icon-wrap {
  background: var(--student-primary-soft);
  transform: translateY(-1px);
}

.icon {
  display: block;
}

.label {
  overflow: hidden;
  max-width: 100%;
  font-weight: 650;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
