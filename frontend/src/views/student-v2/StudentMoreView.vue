<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { Component } from 'vue'
import { Bell, Calendar, DataLine, Notebook, Setting, VideoPlay } from '@element-plus/icons-vue'
import StudentPageHeader from '@/components/student-v2/StudentPageHeader.vue'

const router = useRouter()

interface MoreMenuItem {
  title: string
  description: string
  path: string
  icon: Component
  tone: string
}

const statisticMenus: MoreMenuItem[] = [
  {
    title: '시험 통계',
    description: '점수 · 응시 기록 · 반 평균',
    path: '/student/statistics/tests',
    icon: DataLine,
    tone: 'blue',
  },
  {
    title: '숙제 통계',
    description: '완성도 · 오답 · 미완성 숙제',
    path: '/student/statistics/homework',
    icon: Notebook,
    tone: 'amber',
  },
  {
    title: '출석 통계',
    description: '출석률 · 결석 · 지각 · 조퇴',
    path: '/student/statistics/attendance',
    icon: Calendar,
    tone: 'green',
  },
  {
    title: '영상 통계',
    description: '평균 진도 · 완료 · 시청 중',
    path: '/student/statistics/videos',
    icon: VideoPlay,
    tone: 'violet',
  },
]

const serviceMenus: MoreMenuItem[] = [
  {
    title: '알림',
    description: '새 소식과 학습 안내 확인',
    path: '/student/notifications',
    icon: Bell,
    tone: 'red',
  },
  {
    title: '설정',
    description: 'PIN · 개인정보 · 새 UI 의견',
    path: '/settings',
    icon: Setting,
    tone: 'gray',
  },
]
</script>

<template>
  <div class="student-page more-page">
    <StudentPageHeader
      eyebrow="ALL MENU"
      title="더보기"
      subtitle="학습 기록과 서비스 메뉴를 모아봤어요."
    />

    <section class="more-section" aria-labelledby="more-statistics-title">
      <div class="more-section__heading">
        <h2 id="more-statistics-title">통계</h2>
        <span>나의 학습 기록</span>
      </div>
      <div class="more-menu-surface">
        <button
          v-for="menu in statisticMenus"
          :key="menu.path"
          type="button"
          class="more-menu-row"
          @click="router.push(menu.path)"
        >
          <span class="more-menu-row__icon" :class="`is-${menu.tone}`">
            <el-icon><component :is="menu.icon" /></el-icon>
          </span>
          <span class="more-menu-row__content">
            <strong>{{ menu.title }}</strong>
            <small>{{ menu.description }}</small>
          </span>
          <el-icon class="more-menu-row__arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </section>

    <section class="more-section" aria-labelledby="more-service-title">
      <div class="more-section__heading">
        <h2 id="more-service-title">서비스</h2>
        <span>알림과 계정 관리</span>
      </div>
      <div class="more-menu-surface">
        <button
          v-for="menu in serviceMenus"
          :key="menu.path"
          type="button"
          class="more-menu-row"
          @click="router.push(menu.path)"
        >
          <span class="more-menu-row__icon" :class="`is-${menu.tone}`">
            <el-icon><component :is="menu.icon" /></el-icon>
          </span>
          <span class="more-menu-row__content">
            <strong>{{ menu.title }}</strong>
            <small>{{ menu.description }}</small>
          </span>
          <el-icon class="more-menu-row__arrow"><ArrowRight /></el-icon>
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.more-page {
  display: grid;
  min-width: 0;
  gap: 28px;
}
.more-header {
  margin-bottom: -2px;
}
.more-section {
  display: grid;
  gap: 11px;
}
.more-section__heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 0 2px;
}
.more-section__heading h2 {
  margin: 0;
  color: var(--student-ink);
  font-size: 17px;
  font-weight: 850;
  letter-spacing: -0.02em;
}
.more-section__heading span {
  color: var(--student-muted);
  font-size: 11px;
  font-weight: 650;
}
.more-menu-surface {
  overflow: hidden;
  border: 1px solid var(--student-border);
  border-radius: var(--student-radius-lg);
  background: var(--student-surface);
  box-shadow: var(--student-shadow-soft);
}
.more-menu-row {
  display: grid;
  grid-template-columns: 27px minmax(78px, 0.7fr) minmax(0, 1.3fr) 16px;
  align-items: center;
  gap: 9px;
  width: 100%;
  min-width: 0;
  min-height: 44px;
  padding: 4px 13px;
  border: 0;
  border-bottom: 1px solid var(--student-border);
  color: inherit;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
  touch-action: manipulation;
  -webkit-tap-highlight-color: transparent;
  transition:
    background-color 0.1s ease,
    box-shadow 0.1s ease;
}
.more-menu-row:last-child {
  border-bottom: 0;
}
.more-menu-row:hover {
  background: var(--student-surface-hover);
  box-shadow: inset 3px 0 var(--student-primary);
}
.more-menu-row:active {
  background: var(--student-surface-pressed);
  box-shadow: inset 3px 0 var(--student-muted);
  transition-duration: 0s;
}
.more-menu-row:focus-visible {
  position: relative;
  outline: 3px solid rgba(36, 87, 214, 0.25);
  outline-offset: -3px;
}
.more-menu-row__icon {
  display: grid;
  width: 25px;
  height: 25px;
  place-items: center;
  border-radius: 8px;
  font-size: 14px;
}
.more-menu-row__icon.is-blue {
  color: var(--student-primary);
  background: var(--student-primary-soft);
}
.more-menu-row__icon.is-amber {
  color: var(--student-warning);
  background: var(--student-warning-soft);
}
.more-menu-row__icon.is-green {
  color: var(--student-success);
  background: var(--student-success-soft);
}
.more-menu-row__icon.is-violet {
  color: var(--student-violet-700);
  background: var(--student-violet-100);
}
.more-menu-row__icon.is-red {
  color: var(--student-danger);
  background: var(--student-danger-soft);
}
.more-menu-row__icon.is-gray {
  color: var(--student-text-subtle);
  background: var(--student-slate-75);
}
.more-menu-row__content {
  display: contents;
}
.more-menu-row__content strong {
  overflow: hidden;
  color: var(--student-ink);
  font-size: 14px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.more-menu-row__content small {
  overflow: hidden;
  color: var(--student-muted);
  font-size: 10px;
  font-weight: 550;
  line-height: 1.35;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.more-menu-row__arrow {
  color: var(--student-text-disabled);
  font-size: var(--student-font-body);
}
@media (max-width: 350px) {
  .more-menu-row {
    grid-template-columns: 25px minmax(70px, 0.8fr) minmax(0, 1.2fr) 14px;
    gap: 7px;
    min-height: 44px;
    padding: 4px 10px;
  }
  .more-menu-row__icon {
    width: 24px;
    height: 24px;
    font-size: 13px;
  }
  .more-menu-row__content strong {
    font-size: 13px;
  }
  .more-menu-row__content small {
    font-size: 9px;
  }
}
</style>
