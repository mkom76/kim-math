<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Delete, Plus, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import { dueAnnouncements, formatTimer, type TimerAnnouncement } from '@/utils/examTimer'

type TimerState = 'setup' | 'running' | 'paused' | 'finished'

const route = useRoute()
const durationMinutes = ref(60)
const examTitle = ref(typeof route.query.title === 'string' ? route.query.title : '시험')
const announcements = ref<TimerAnnouncement[]>([
  { id: 1, minutesBefore: 15, message: '15분 남았습니다. 서술형 답안을 작성해주세요.' },
])
const state = ref<TimerState>('setup')
const remainingSeconds = ref(durationMinutes.value * 60)
const previousSeconds = ref(remainingSeconds.value + 1)
const activeMessage = ref('')
const darkMode = ref(false)
const endAt = ref(0)
let nextAnnouncementId = 2
let intervalId: number | undefined

const timerText = computed(() => formatTimer(remainingSeconds.value))
const isDisplayMode = computed(() => state.value !== 'setup')
const stateLabel = computed(() => {
  if (state.value === 'paused') return '일시정지'
  if (state.value === 'finished') return '시험 종료'
  return '남은 시간'
})

function stopTicker() {
  if (intervalId !== undefined) {
    window.clearInterval(intervalId)
    intervalId = undefined
  }
}

function tick() {
  if (state.value !== 'running') return

  const nextRemaining = Math.max(0, Math.ceil((endAt.value - Date.now()) / 1000))
  const due = dueAnnouncements(previousSeconds.value, nextRemaining, announcements.value)
  const latestAnnouncement = due[due.length - 1]
  if (latestAnnouncement) activeMessage.value = latestAnnouncement.message.trim()
  previousSeconds.value = nextRemaining
  remainingSeconds.value = nextRemaining

  if (nextRemaining === 0) {
    state.value = 'finished'
    activeMessage.value = '시험이 종료되었습니다.'
    stopTicker()
  }
}

function start() {
  const seconds = Math.max(1, Math.round(durationMinutes.value * 60))
  remainingSeconds.value = seconds
  previousSeconds.value = seconds + 1
  activeMessage.value = ''
  state.value = 'running'
  endAt.value = Date.now() + seconds * 1000
  tick()
  stopTicker()
  intervalId = window.setInterval(tick, 250)
}

function pause() {
  tick()
  state.value = 'paused'
  stopTicker()
}

function resume() {
  previousSeconds.value = remainingSeconds.value
  endAt.value = Date.now() + remainingSeconds.value * 1000
  state.value = 'running'
  intervalId = window.setInterval(tick, 250)
}

function reset() {
  stopTicker()
  state.value = 'setup'
  remainingSeconds.value = durationMinutes.value * 60
  activeMessage.value = ''
  if (document.fullscreenElement) void document.exitFullscreen()
}

function addAnnouncement() {
  announcements.value.push({ id: nextAnnouncementId++, minutesBefore: 5, message: '' })
}

function removeAnnouncement(id: number) {
  announcements.value = announcements.value.filter((announcement) => announcement.id !== id)
}

async function toggleFullscreen() {
  if (document.fullscreenElement) await document.exitFullscreen()
  else await document.documentElement.requestFullscreen()
}

onBeforeUnmount(stopTicker)
</script>

<template>
  <section class="exam-timer" :class="{ 'exam-timer--display': isDisplayMode, 'exam-timer--dark': isDisplayMode && darkMode }">
    <template v-if="!isDisplayMode">
      <div class="setup-card">
        <header class="setup-header">
          <div>
            <p class="eyebrow">교사용 도구</p>
            <h1>시험 타이머</h1>
            <p>시험 중 화면에 띄울 시간과 안내 문구를 설정하세요.</p>
          </div>
          <el-icon class="header-icon"><Timer /></el-icon>
        </header>

        <div class="form-grid">
          <label>
            <span>시험 이름</span>
            <el-input v-model="examTitle" maxlength="30" placeholder="예: 중간고사" />
          </label>
          <label>
            <span>시험 시간</span>
            <el-input-number v-model="durationMinutes" :min="1" :max="600" controls-position="right" />
            <small>분</small>
          </label>
        </div>

        <div class="announcement-section">
          <div class="section-heading">
            <div>
              <h2>시간 안내 문구</h2>
              <p>설정한 시간이 되었을 때 시계 아래에 크게 표시됩니다.</p>
            </div>
            <el-button :icon="Plus" @click="addAnnouncement">문구 추가</el-button>
          </div>

          <div v-if="announcements.length" class="announcement-list">
            <div v-for="announcement in announcements" :key="announcement.id" class="announcement-row">
              <div class="minute-input">
                <el-input-number v-model="announcement.minutesBefore" :min="0" :max="600" controls-position="right" />
                <span>분 전</span>
              </div>
              <el-input v-model="announcement.message" maxlength="100" show-word-limit placeholder="표시할 안내 문구" />
              <el-button circle plain :icon="Delete" aria-label="안내 문구 삭제" @click="removeAnnouncement(announcement.id)" />
            </div>
          </div>
          <div v-else class="empty-message">등록된 안내 문구가 없습니다.</div>
        </div>

        <el-button class="start-button" type="primary" size="large" :disabled="!examTitle.trim()" @click="start">
          타이머 시작
        </el-button>
      </div>
    </template>

    <template v-else>
      <div class="display-toolbar">
        <button type="button" aria-label="설정으로 돌아가기" @click="reset"><el-icon><ArrowLeft /></el-icon></button>
        <button type="button" :aria-label="darkMode ? '주간 모드로 전환' : '야간 모드로 전환'" @click="darkMode = !darkMode">
          <el-icon><Sunny v-if="darkMode" /><Moon v-else /></el-icon>
        </button>
        <button type="button" aria-label="전체 화면 전환" @click="toggleFullscreen"><el-icon><FullScreen /></el-icon></button>
      </div>

      <main class="clock-panel" :class="{ 'clock-panel--finished': state === 'finished' }">
        <p class="exam-title">{{ examTitle }}</p>
        <p class="state-label">{{ stateLabel }}</p>
        <div class="digital-clock" role="timer" aria-live="off">{{ timerText }}</div>
        <div class="message-slot" aria-live="polite">
          <p v-if="activeMessage">{{ activeMessage }}</p>
        </div>
      </main>

      <div v-if="state !== 'finished'" class="timer-controls">
        <el-button v-if="state === 'running'" size="large" :icon="VideoPause" @click="pause">일시정지</el-button>
        <el-button v-else type="primary" size="large" :icon="VideoPlay" @click="resume">계속하기</el-button>
      </div>
      <div v-else class="timer-controls">
        <el-button size="large" @click="reset">새 타이머 설정</el-button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.exam-timer {
  min-height: calc(100vh - 108px);
  min-height: calc(100dvh - 108px);
  display: grid;
  place-items: center;
  color: #172033;
}

.setup-card {
  width: min(900px, 100%);
  padding: 42px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 18px;
  box-shadow: 0 12px 34px rgba(23, 32, 51, 0.07);
}

.setup-header, .section-heading, .announcement-row, .minute-input {
  display: flex;
  align-items: center;
}

.setup-header { justify-content: space-between; margin-bottom: 36px; }
.setup-header h1 { margin: 2px 0 6px; font-size: 32px; font-weight: 750; }
.setup-header p, .section-heading p { color: #7a8495; }
.eyebrow { color: #409eff !important; font-size: 13px; font-weight: 700; letter-spacing: .08em; }
.header-icon { padding: 16px; box-sizing: content-box; border-radius: 14px; background: #ecf5ff; color: #409eff; font-size: 34px; }
.form-grid { display: grid; grid-template-columns: 1fr 260px; gap: 24px; }
.form-grid label > span { display: block; margin-bottom: 8px; font-weight: 650; }
.form-grid label:last-child { position: relative; }
.form-grid label:last-child :deep(.el-input-number) { width: calc(100% - 38px); }
.form-grid small { margin-left: 10px; color: #606266; }
.announcement-section { margin: 36px 0; padding-top: 30px; border-top: 1px solid #ebeef5; }
.section-heading { justify-content: space-between; margin-bottom: 18px; }
.section-heading h2 { font-size: 19px; font-weight: 700; }
.section-heading p { margin-top: 3px; font-size: 14px; }
.announcement-list { display: grid; gap: 12px; }
.announcement-row { gap: 12px; }
.minute-input { flex: 0 0 190px; gap: 9px; }
.minute-input :deep(.el-input-number) { width: 130px; }
.empty-message { padding: 25px; border-radius: 10px; background: #f7f8fa; color: #909399; text-align: center; }
.start-button { width: 100%; height: 48px; font-size: 16px; font-weight: 700; }

.exam-timer--display {
  position: fixed;
  inset: 0;
  z-index: 3000;
  min-height: 100vh;
  min-height: 100dvh;
  padding: 28px;
  background: #f8fafc;
}

.display-toolbar { position: absolute; top: 24px; right: 28px; display: flex; gap: 8px; }
.display-toolbar button { display: grid; place-items: center; width: 42px; height: 42px; border: 1px solid #dfe4ea; border-radius: 50%; background: #fff; color: #5f6b7a; cursor: pointer; font-size: 18px; }
.clock-panel { width: min(1200px, 94vw); text-align: center; }
.exam-title { margin-bottom: 14px; color: #4c596b; font-size: clamp(20px, 2vw, 30px); font-weight: 650; }
.state-label { color: #8a94a3; font-size: clamp(16px, 1.4vw, 22px); letter-spacing: .12em; }
.digital-clock { margin: 10px 0 18px; color: #151b26; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: clamp(92px, 19vw, 260px); font-weight: 650; font-variant-numeric: tabular-nums; letter-spacing: -.07em; line-height: 1; }
.message-slot { min-height: 92px; display: grid; place-items: center; }
.message-slot p { padding: 16px 32px; border: 2px solid #d8e300; border-radius: 8px; background: #f4ff5e; color: #171a12; font-size: clamp(22px, 3vw, 42px); font-weight: 750; box-shadow: 0 8px 28px rgba(173, 186, 0, .22); }
.clock-panel--finished .digital-clock { color: #d64b4b; }
.clock-panel--finished .message-slot p { border-color: #d64b4b; }
.timer-controls { position: absolute; bottom: 30px; left: 50%; transform: translateX(-50%); }
.timer-controls :deep(.el-button) { min-width: 140px; }

.exam-timer--dark { background: #10131a; color: #f2f4f8; }
.exam-timer--dark .display-toolbar button { border-color: #333a48; background: #1c212b; color: #d9dee8; }
.exam-timer--dark .exam-title { color: #d2d7e1; }
.exam-timer--dark .state-label { color: #8e98a9; }
.exam-timer--dark .digital-clock { color: #f7f8fa; }
.exam-timer--dark .message-slot p { border-color: #e4ef19; background: #f4ff5e; color: #10130d; box-shadow: 0 0 30px rgba(244, 255, 94, .16); }
.exam-timer--dark .clock-panel--finished .digital-clock { color: #ff7373; }
.exam-timer--dark .timer-controls :deep(.el-button) { border-color: #3b4352; background: #1c212b; color: #e9edf4; }

@media (max-width: 700px) {
  .setup-card { padding: 25px 20px; }
  .form-grid { grid-template-columns: 1fr; }
  .announcement-row { align-items: stretch; flex-wrap: wrap; }
  .minute-input { flex-basis: calc(100% - 52px); }
  .announcement-row > :deep(.el-input) { order: 3; width: 100%; }
  .header-icon { display: none; }
  .display-toolbar { top: 14px; right: 14px; }
  .digital-clock { font-size: clamp(72px, 25vw, 140px); }
}
</style>
