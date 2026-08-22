<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { studentNotificationAPI } from '@/api/client'
import type { StudentNotification } from '@/api/client'
import { navigateToStudentNotification } from '@/utils/notificationNavigation'

type Filter = 'all' | 'unread'

const router = useRouter()
const loading = ref(false)
const markingAll = ref(false)
const notifications = ref<StudentNotification[]>([])
const activeFilter = ref<Filter>('all')

const unreadCount = computed(() => notifications.value.filter((item) => !item.readAt).length)
const filteredNotifications = computed(() =>
  activeFilter.value === 'unread'
    ? notifications.value.filter((item) => !item.readAt)
    : notifications.value,
)

const loadNotifications = async () => {
  loading.value = true
  try {
    const response = await studentNotificationAPI.getInbox()
    notifications.value = response.data
  } catch (error) {
    console.error('Failed to load notifications:', error)
    ElMessage.error('알림을 불러오지 못했습니다')
  } finally {
    loading.value = false
  }
}

const formatTimestamp = (value: string) => {
  const date = new Date(value)
  const today = new Date()
  const sameDay =
    date.getFullYear() === today.getFullYear() &&
    date.getMonth() === today.getMonth() &&
    date.getDate() === today.getDate()

  return new Intl.DateTimeFormat(
    'ko-KR',
    sameDay
      ? { hour: 'numeric', minute: '2-digit' }
      : { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' },
  ).format(date)
}

const iconFor = (type: string) => {
  if (type === 'FEEDBACK') return 'ChatDotRound'
  if (type === 'TEST') return 'DocumentChecked'
  if (type === 'CLINIC') return 'FirstAidKit'
  if (type === 'VIDEO') return 'VideoPlay'
  return 'Bell'
}

const openNotification = async (notification: StudentNotification) => {
  if (!notification.readAt) {
    try {
      const response = await studentNotificationAPI.markRead(notification.id)
      Object.assign(notification, response.data)
      window.dispatchEvent(new CustomEvent('student-notifications-changed'))
    } catch {
      ElMessage.warning('읽음 처리는 잠시 후 다시 시도해주세요')
    }
  }

  await navigateToStudentNotification(router, notification.targetPath)
}

const markAllRead = async () => {
  if (!unreadCount.value || markingAll.value) return
  markingAll.value = true
  try {
    await studentNotificationAPI.markAllRead()
    const readAt = new Date().toISOString()
    notifications.value.forEach((item) => {
      if (!item.readAt) item.readAt = readAt
    })
    window.dispatchEvent(new CustomEvent('student-notifications-changed'))
    ElMessage.success('모든 알림을 확인했어요')
  } catch {
    ElMessage.error('전체 읽음 처리에 실패했습니다')
  } finally {
    markingAll.value = false
  }
}

onMounted(loadNotifications)
</script>

<template>
  <div class="student-view student-page notifications-page" v-loading="loading">
    <header class="student-page__header notifications-header">
      <div class="notifications-header__title">
        <button
          class="student-icon-button"
          aria-label="이전 화면으로 돌아가기"
          @click="router.back()"
        >
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <div>
          <p class="student-page__eyebrow">INBOX</p>
          <h1 class="student-page__title">알림</h1>
        </div>
      </div>
      <button
        class="notifications-read-all"
        :disabled="!unreadCount || markingAll"
        @click="markAllRead"
      >
        모두 읽음
      </button>
    </header>

    <section class="notifications-summary" aria-labelledby="notification-summary-title">
      <div>
        <span class="notifications-summary__icon"
          ><el-icon><Bell /></el-icon
        ></span>
        <div>
          <h2 id="notification-summary-title">새로운 소식</h2>
          <p v-if="unreadCount">
            <strong>{{ unreadCount }}개</strong>의 읽지 않은 알림이 있어요.
          </p>
          <p v-else>새로 확인할 알림이 없어요.</p>
        </div>
      </div>
    </section>

    <div class="notifications-filters" role="group" aria-label="알림 필터">
      <button :class="{ active: activeFilter === 'all' }" @click="activeFilter = 'all'">
        전체 <span>{{ notifications.length }}</span>
      </button>
      <button :class="{ active: activeFilter === 'unread' }" @click="activeFilter = 'unread'">
        읽지 않음 <span>{{ unreadCount }}</span>
      </button>
    </div>

    <section aria-live="polite" aria-atomic="false">
      <div v-if="filteredNotifications.length" class="student-surface notifications-list">
        <button
          v-for="notification in filteredNotifications"
          :key="notification.id"
          class="notification-row"
          :class="{ unread: !notification.readAt }"
          @click="openNotification(notification)"
        >
          <span class="notification-row__icon">
            <el-icon><component :is="iconFor(notification.type)" /></el-icon>
          </span>
          <span class="notification-row__content">
            <span class="notification-row__meta">
              <span v-if="!notification.readAt" class="notification-row__new">새 알림</span>
              <time :datetime="notification.createdAt">{{
                formatTimestamp(notification.createdAt)
              }}</time>
            </span>
            <strong>{{ notification.title }}</strong>
            <span class="notification-row__body">{{ notification.body }}</span>
          </span>
          <el-icon v-if="notification.targetPath" class="notification-row__arrow"
            ><ArrowRight
          /></el-icon>
        </button>
      </div>

      <div v-else class="student-empty-state notifications-empty">
        <span class="student-empty-state__icon"
          ><el-icon><Bell /></el-icon
        ></span>
        <h3>
          {{ activeFilter === 'unread' ? '모든 알림을 확인했어요' : '아직 도착한 알림이 없어요' }}
        </h3>
        <p>
          {{
            activeFilter === 'unread'
              ? '새 알림이 오면 여기에 표시할게요.'
              : '피드백과 학습 소식이 도착하면 모아둘게요.'
          }}
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.notifications-page {
  display: grid;
  width: 100%;
  max-width: 760px;
  min-width: 0;
  gap: var(--student-space-5);
  margin: 0 auto;
  padding: var(--student-space-5) var(--student-space-4) var(--student-space-8);
}
.notifications-page .student-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 0;
}
.notifications-page .student-page__eyebrow {
  margin: 0 0 5px;
  color: var(--student-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.notifications-page .student-page__title {
  margin: 0;
  color: var(--student-ink);
  font-size: clamp(24px, 7vw, 30px);
  font-weight: 800;
  line-height: 1.22;
}
.notifications-page .student-icon-button {
  display: grid;
  width: 48px;
  min-width: 48px;
  height: 48px;
  padding: 0;
  place-items: center;
  border: 1px solid var(--student-border);
  border-radius: 15px;
  color: var(--student-ink);
  background: var(--student-surface);
  box-shadow: 0 5px 18px rgba(28, 46, 78, 0.06);
  cursor: pointer;
}
.notifications-page .student-surface {
  overflow: hidden;
  border: 1px solid var(--student-border);
  border-radius: var(--student-radius-lg);
  background: var(--student-surface);
  box-shadow: 0 5px 18px rgba(28, 46, 78, 0.06);
}
.notifications-page .student-empty-state {
  display: grid;
  min-height: 230px;
  justify-items: center;
  align-content: center;
  padding: 30px 20px;
  color: var(--student-muted);
  text-align: center;
}
.notifications-page .student-empty-state__icon {
  display: grid;
  width: 58px;
  height: 58px;
  margin-bottom: 12px;
  place-items: center;
  border-radius: 19px;
  color: var(--student-primary);
  background: var(--student-primary-soft);
  font-size: 25px;
}
.notifications-page .student-empty-state h3 {
  margin: 0;
  color: var(--student-ink);
}
.notifications-page .student-empty-state p {
  margin: 7px 0 0;
  font-size: 13px;
  line-height: 1.55;
}
.notifications-header {
  align-items: center;
}
.notifications-header__title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.notifications-read-all {
  min-height: 44px;
  padding: 0 2px;
  border: 0;
  color: var(--student-primary);
  background: transparent;
  font: inherit;
  font-size: 14px;
  font-weight: 750;
  cursor: pointer;
}
.notifications-read-all:disabled {
  color: var(--student-muted);
  cursor: default;
  opacity: 0.55;
}
.notifications-summary {
  padding: 18px;
  border-radius: var(--student-radius-lg);
  color: var(--student-surface);
  background: linear-gradient(135deg, var(--student-primary), var(--student-blue-500));
  box-shadow: var(--student-shadow);
}
.notifications-summary > div {
  display: flex;
  align-items: center;
  gap: 14px;
}
.notifications-summary__icon {
  display: grid;
  flex: 0 0 46px;
  height: 46px;
  place-items: center;
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.16);
  font-size: 23px;
}
.notifications-summary h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
}
.notifications-summary p {
  margin: 4px 0 0;
  font-size: 13px;
  opacity: 0.88;
}
.notifications-summary strong {
  color: var(--student-surface);
}
.notifications-filters {
  display: flex;
  gap: 8px;
}
.notifications-filters button {
  min-height: 44px;
  padding: 0 15px;
  border: 1px solid var(--student-border);
  border-radius: 999px;
  color: var(--student-muted);
  background: var(--student-surface);
  font: inherit;
  font-size: 13px;
  font-weight: 750;
  cursor: pointer;
}
.notifications-filters button.active {
  border-color: var(--student-primary);
  color: var(--student-primary);
  background: var(--student-primary-soft);
}
.notifications-filters span {
  margin-left: 3px;
}
.notifications-list {
  overflow: hidden;
}
.notification-row {
  position: relative;
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
  width: 100%;
  min-height: 108px;
  padding: 18px;
  border: 0;
  border-bottom: 1px solid var(--student-border);
  color: inherit;
  background: var(--student-surface);
  text-align: left;
  cursor: pointer;
}
.notification-row:last-child {
  border-bottom: 0;
}
.notification-row.unread {
  background: linear-gradient(90deg, var(--student-primary-soft), var(--student-surface) 72%);
}
.notification-row.unread::before {
  position: absolute;
  top: 20px;
  left: 0;
  width: 3px;
  height: 28px;
  border-radius: 0 3px 3px 0;
  background: var(--student-primary);
  content: '';
}
.notification-row__icon {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  border-radius: 15px;
  color: var(--student-primary);
  background: var(--student-primary-soft);
  font-size: 21px;
}
.notification-row__content {
  display: grid;
  min-width: 0;
  gap: 5px;
}
.notification-row__content > strong {
  overflow: hidden;
  color: var(--student-ink);
  font-size: 15px;
  font-weight: 800;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notification-row__meta {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--student-muted);
  font-size: 11px;
}
.notification-row__new {
  color: var(--student-primary);
  font-weight: 800;
}
.notification-row__body {
  display: -webkit-box;
  overflow: hidden;
  color: var(--student-muted);
  font-size: 13px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.notification-row__arrow {
  align-self: center;
  color: var(--student-muted);
}
.notifications-empty {
  min-height: 280px;
}
@media (max-width: 420px) {
  .notifications-header__title {
    gap: 9px;
  }
  .notification-row {
    padding: 16px 14px;
  }
}
</style>
