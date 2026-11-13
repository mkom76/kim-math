<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  Camera,
  Close,
  Download,
  EditPen,
  FullScreen,
  Picture,
  RefreshRight,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useStudentUiMode } from '@/composables/useStudentUiMode'
import {
  solutionNotebookAPI,
  type NotebookConfig,
  type NotebookLiveConfig,
  type NotebookJob,
  type NotebookSolution,
  type NotebookVerification,
} from '@/api/solutionNotebook'
import NotebookVerificationPanel from '@/components/NotebookVerificationPanel.vue'
import sampleUrl from '@/assets/handwriting-solution-sample.png'

const router = useRouter(),
  auth = useAuthStore()
const { mode: studentUiMode } = useStudentUiMode()
const fileInput = ref<HTMLInputElement | null>(null)
const cameraInput = ref<HTMLInputElement | null>(null)
const photo = ref(''),
  fileName = ref(''),
  rotation = ref(0),
  question = ref(''),
  error = ref('')
const dragging = ref(false),
  sampleVisible = ref(false),
  tab = ref<'image' | 'text'>('image'),
  expanded = ref(false)
const config = ref<NotebookConfig | null>(null),
  busy = ref(''),
  confirmed = ref(false),
  readJobId = ref('')
const trialConfig = ref({ ready: false, used: false, notice: '' })
const serviceConfig = ref<NotebookLiveConfig | null>(null)
const photoReady = computed(() =>
  serviceConfig.value?.enabled ? serviceConfig.value.ready : trialConfig.value.ready,
)
const dailyLimitReached = computed(
  () => !!serviceConfig.value?.enabled && serviceConfig.value.dailyLimitReached,
)
const trialConsent = ref(false)
const resultMode = ref<'mock' | 'live'>('mock')
const verification = ref<NotebookVerification | null>(null)
const resultImage = ref(''),
  resultSolution = ref<NotebookSolution | null>(null),
  resultMessage = ref('')
const sampleQuestion = '유리함수 f(x)=(2x+1)/(x−1)의 그래프를 그리고, 두 점근선의 교점을 구하시오.'
const backPath = computed(() =>
  auth.role === 'STUDENT'
    ? studentUiMode.value === 'v2'
      ? '/student/more'
      : '/settings'
    : '/students',
)
const photoStyle = computed(() => ({
  transform: `rotate(${rotation.value}deg)`,
  maxWidth: rotation.value % 180 ? '55%' : '100%',
}))
const displayedImage = computed(() => (sampleVisible.value ? sampleUrl : resultImage.value))
const phases: Record<string, string> = {
  QUEUED: '작업 대기',
  READING: '문제 읽기',
  SOLVING: '풀이 작성',
  VERIFYING: '풀이 검토',
  RENDERING: '이미지 준비',
  CHECKING_IMAGE: '이미지 검토',
}
let epoch = 0,
  controller: AbortController | null = null,
  expiryTimer: ReturnType<typeof setTimeout> | undefined
let quotaResetTimer: ReturnType<typeof setTimeout> | undefined
let disposed = false
function scheduleQuotaRefresh() {
  clearTimeout(quotaResetTimer)
  if (disposed || !serviceConfig.value?.enabled) return
  const day = 86400000,
    koreaOffset = 9 * 3600000,
    now = Date.now()
  const nextMidnight = (Math.floor((now + koreaOffset) / day) + 1) * day - koreaOffset
  quotaResetTimer = setTimeout(refreshQuotaAtMidnight, nextMidnight - now + 1000)
}
async function refreshQuotaAtMidnight() {
  if (disposed) return
  try {
    const c = await solutionNotebookAPI.liveConfig()
    if (disposed) return
    serviceConfig.value = c
    scheduleQuotaRefresh()
  } catch {
    if (!disposed) quotaResetTimer = setTimeout(refreshQuotaAtMidnight, 60000)
  }
}
const jobs = new Set<string>()
const liveJobs = new Set<string>()
const productionJobs = new Set<string>()
function reason(e: unknown) {
  const v = e as { response?: { data?: { message?: string } }; message?: string }
  return v.response?.data?.message || v.message || '처리하지 못했어요. 다시 시도해주세요.'
}
function removeJob(id: string) {
  jobs.delete(id)
  const remove = productionJobs.has(id)
    ? solutionNotebookAPI.liveRemove
    : liveJobs.has(id)
      ? solutionNotebookAPI.photoRemove
      : solutionNotebookAPI.remove
  productionJobs.delete(id)
  liveJobs.delete(id)
  void remove(id).catch(() => undefined)
}
function clearResult() {
  if (resultImage.value) URL.revokeObjectURL(resultImage.value)
  resultImage.value = ''
  resultSolution.value = null
  resultMessage.value = ''
  resultMode.value = 'mock'
  verification.value = null
  expanded.value = false
  clearTimeout(expiryTimer)
}
function cancel() {
  epoch++
  controller?.abort()
  controller = null
  for (const id of [...jobs]) removeJob(id)
  busy.value = ''
  readJobId.value = ''
  confirmed.value = false
}
function clearPhoto() {
  if (photo.value) URL.revokeObjectURL(photo.value)
  photo.value = ''
  fileName.value = ''
  rotation.value = 0
}
function reset() {
  cancel()
  clearPhoto()
  clearResult()
  question.value = ''
  error.value = ''
  sampleVisible.value = false
  tab.value = 'image'
  trialConsent.value = false
}
function selectFile(file?: File) {
  dragging.value = false
  if (!file) return
  error.value = ''
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    error.value = 'JPG, PNG, WEBP 사진을 선택해주세요.'
    return
  }
  if (file.size > 12 * 1024 * 1024) {
    error.value = '12MB 이하의 사진을 선택해주세요.'
    return
  }
  reset()
  photo.value = URL.createObjectURL(file)
  fileName.value = file.name
}
function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectFile(input.files?.[0])
  input.value = ''
}
function onPaste(event: ClipboardEvent) {
  // Leave ordinary text pasting and editing fields alone.
  const target = event.target
  if (
    target instanceof Element &&
    target.closest('input, textarea, [contenteditable]:not([contenteditable="false"])')
  )
    return
  const data = event.clipboardData
  const file =
    Array.from(data?.files ?? []).find((f) => f.type.startsWith('image/')) ??
    Array.from(data?.items ?? [])
      .find((item) => item.kind === 'file' && item.type.startsWith('image/'))
      ?.getAsFile()
  if (!file) return
  event.preventDefault()
  selectFile(file)
}
function showSample() {
  reset()
  question.value = sampleQuestion
  sampleVisible.value = true
}
function onDrop(event: DragEvent) {
  selectFile(event.dataTransfer?.files[0])
}
function onEscape(event: KeyboardEvent) {
  if (event.key === 'Escape') expanded.value = false
}
async function poll(job: NotebookJob, n: number): Promise<NotebookJob> {
  const mode = job.mode
  const until = Date.now() + 480000
  while (
    ['QUEUED', 'READING', 'SOLVING', 'VERIFYING', 'RENDERING', 'CHECKING_IMAGE'].includes(job.state)
  ) {
    if (n !== epoch) throw new Error('취소된 작업입니다.')
    if (Date.now() > until) {
      removeJob(job.id)
      throw new Error('처리 시간이 초과됐어요. 다시 시도해주세요.')
    }
    busy.value = phases[job.state] || '처리 중'
    const signal = controller!.signal
    await new Promise<void>((resolve, reject) => {
      const aborted = () => {
        clearTimeout(timer)
        reject(new Error('작업 취소'))
      }
      const timer = setTimeout(() => {
        signal.removeEventListener('abort', aborted)
        resolve()
      }, 300)
      if (signal.aborted) aborted()
      else signal.addEventListener('abort', aborted, { once: true })
    })
    job = await (
      productionJobs.has(job.id)
        ? solutionNotebookAPI.liveStatus
        : mode === 'live'
          ? solutionNotebookAPI.photoStatus
          : solutionNotebookAPI.status
    )(job.id, signal)
    if (job.mode !== mode) throw new Error('허용되지 않은 API 모드입니다.')
  }
  if (n !== epoch) throw new Error('취소된 작업입니다.')
  verification.value = job.verification ?? null
  if (job.state === 'FAILED') {
    expiryTimer = setTimeout(
      () => {
        verification.value = null
      },
      Math.max(0, Date.parse(job.expiresAt) - Date.now()),
    )
    throw new Error(job.message)
  }
  return job
}
async function photoBlob(source: string): Promise<Blob> {
  const image = new Image()
  image.src = source
  await image.decode()
  const scale = Math.min(1, 1600 / Math.max(image.naturalWidth, image.naturalHeight))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(image.naturalWidth * scale))
  canvas.height = Math.max(1, Math.round(image.naturalHeight * scale))
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('사진을 변환하지 못했어요.')
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(image, 0, 0, canvas.width, canvas.height)
  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => {
        if (!blob || blob.size > 4 * 1024 * 1024)
          reject(new Error('사진을 작게 잘라 다시 선택해주세요.'))
        else resolve(blob)
      },
      'image/jpeg',
      0.9,
    ),
  )
}
async function generatePhoto() {
  if (busy.value || !photo.value || !photoReady.value || !trialConsent.value) return
  const production = !!serviceConfig.value?.enabled
  const source = photo.value,
    turn = rotation.value
  cancel()
  clearResult()
  sampleVisible.value = false
  error.value = ''
  const n = epoch
  controller = new AbortController()
  busy.value = '사진 준비 중'
  try {
    const blob = await photoBlob(source)
    if (n !== epoch) return
    const started = await (production ? solutionNotebookAPI.livePhoto : solutionNotebookAPI.photo)(
      blob,
      turn,
    )
    if (production) productionJobs.add(started.id)
    liveJobs.add(started.id)
    if (n !== epoch) {
      removeJob(started.id)
      return
    }
    jobs.add(started.id)
    if (!production) {
      trialConfig.value.ready = false
      trialConfig.value.used = true
    }
    resultMode.value = 'live'
    const job = await poll(started, n)
    if (n !== epoch) return
    question.value = job.question
    resultMode.value = 'live'
    resultSolution.value = job.solution
    resultMessage.value = job.message
    if (job.imageAvailable && job.verified) {
      const blob = await (
        production ? solutionNotebookAPI.liveImage : solutionNotebookAPI.photoImage
      )(job.id)
      if (n !== epoch) return
      if (blob.type !== 'image/png') throw new Error('이미지 형식을 확인하지 못했어요.')
      resultImage.value = URL.createObjectURL(blob)
      tab.value = 'image'
    } else {
      error.value = job.message
      tab.value = 'text'
    }
    expiryTimer = setTimeout(
      () => {
        clearResult()
        error.value = '결과 보관 시간이 지났어요. 필요한 이미지는 미리 저장해주세요.'
      },
      Math.max(0, Date.parse(job.expiresAt) - Date.now()),
    )
  } catch (e) {
    if (n === epoch) error.value = reason(e)
  } finally {
    if (n === epoch) busy.value = ''
    try {
      if (production) serviceConfig.value = await solutionNotebookAPI.liveConfig()
      else trialConfig.value = await solutionNotebookAPI.trialConfig()
    } catch {
      if (serviceConfig.value) serviceConfig.value.ready = false
      trialConfig.value.ready = false
    }
  }
}
async function readProblem() {
  if (busy.value || !config.value?.enabled) return
  cancel()
  clearPhoto()
  clearResult()
  question.value = ''
  sampleVisible.value = false
  error.value = ''
  const n = epoch
  controller = new AbortController()
  busy.value = '모의 문제 읽기'
  try {
    // IMPORTANT: only fixture/scenario are sent; never photo.value, File, data URL or file name.
    const started = await solutionNotebookAPI.read(config.value.fixture, 'SUCCESS')
    if (n !== epoch) {
      removeJob(started.id)
      return
    }
    jobs.add(started.id)
    const job = await poll(started, n)
    if (n !== epoch) return
    readJobId.value = job.id
    question.value = job.question
    // Mock fixture only: continue automatically; this is not user confirmation of a photo.
    confirmed.value = true
    busy.value = ''
    await generate()
  } catch (e) {
    if (n === epoch) error.value = reason(e)
  } finally {
    if (n === epoch) busy.value = ''
  }
}
async function generate() {
  if (busy.value || !readJobId.value || !confirmed.value || !config.value?.enabled) return
  clearResult()
  sampleVisible.value = false
  error.value = ''
  const n = epoch
  controller = new AbortController()
  busy.value = '모의 풀이 작성'
  try {
    const started = await solutionNotebookAPI.solve({
      readJobId: readJobId.value,
      question: question.value,
      confirmed: confirmed.value,
      grade: '고1',
      depth: 'detailed',
      scenario: 'SUCCESS',
    })
    if (n !== epoch) {
      removeJob(started.id)
      return
    }
    jobs.add(started.id)
    const job = await poll(started, n)
    if (n !== epoch) return
    resultSolution.value = job.solution
    resultMessage.value = job.message
    if (job.imageAvailable && job.verified) {
      const blob = await solutionNotebookAPI.image(job.id)
      if (n !== epoch) return
      if (blob.type !== 'image/png') throw new Error('이미지 형식을 확인하지 못했어요.')
      resultImage.value = URL.createObjectURL(blob)
      tab.value = 'image'
    } else {
      error.value = job.message
      tab.value = 'text'
    }
    expiryTimer = setTimeout(
      () => {
        clearResult()
        error.value = '모의 결과가 만료됐어요. 다시 시작해주세요.'
      },
      Math.max(0, Date.parse(job.expiresAt) - Date.now()),
    )
  } catch (e) {
    if (n === epoch) error.value = reason(e)
  } finally {
    if (n === epoch) busy.value = ''
  }
}
onMounted(async () => {
  window.addEventListener('keydown', onEscape)
  window.addEventListener('paste', onPaste)
  void solutionNotebookAPI
    .liveConfig()
    .then((c) => {
      if (disposed) return
      serviceConfig.value = c
      scheduleQuotaRefresh()
    })
    .catch(() => undefined)
  void solutionNotebookAPI
    .trialConfig()
    .then((c) => {
      trialConfig.value = c
    })
    .catch(() => undefined)
  try {
    const c = await solutionNotebookAPI.config()
    if (c.mode === 'mock') config.value = c
  } catch {
    config.value = null
  }
})
onBeforeUnmount(() => {
  disposed = true
  clearTimeout(quotaResetTimer)
  cancel()
  clearPhoto()
  clearResult()
  window.removeEventListener('keydown', onEscape)
  window.removeEventListener('paste', onPaste)
})
</script>

<template>
  <section class="handwriting-notebook">
    <div class="notebook-topline">
      <button type="button" class="back-button" @click="router.push(backPath)">
        <el-icon><ArrowLeft /></el-icon>돌아가기
      </button>
      <span class="prototype-badge">{{
        serviceConfig?.enabled
          ? serviceConfig.preview
            ? '로컬 모의 검증'
            : '사진으로 풀이 받기'
          : trialConfig.ready || trialConfig.used || resultMode === 'live'
            ? '실제 API · 샘플 1건 시험'
            : '모의 API · 외부 전송 없음'
      }}</span>
    </div>
    <div class="notebook-intro">
      <div>
        <p class="notebook-eyebrow">수희쌤 스타일로, 손글씨풀이</p>
        <h1>막힌 문제,<br />사진으로 물어보세요.</h1>
        <p class="intro-copy">한 문제를 올리면 풀이를 차근차근 보여드려요.</p>
      </div>
    </div>

    <div class="notebook-columns">
      <div class="input-stack">
        <section class="notebook-card">
          <div class="notebook-card-title">
            <h2>
              <el-icon><Camera /></el-icon>한 문제만 담아주세요
            </h2>
            <small>문제 사진</small>
          </div>
          <input
            ref="fileInput"
            type="file"
            class="file-input"
            accept="image/jpeg,image/png,image/webp"
            aria-label="문제 사진 선택"
            @change="onFileChange"
          />
          <input
            ref="cameraInput"
            type="file"
            class="file-input"
            accept="image/jpeg,image/png,image/webp"
            capture="environment"
            aria-label="카메라로 문제 촬영"
            @change="onFileChange"
          />
          <div v-if="photo" class="photo-preview">
            <div class="photo-stage">
              <img
                :src="photo"
                alt="선택한 문제 사진"
                :style="photoStyle"
                @error="error = '사진을 표시하지 못했어요. 다른 사진을 선택해주세요.'"
              />
            </div>
            <div class="photo-toolbar">
              <span>{{ fileName }}</span
              ><button
                type="button"
                aria-label="사진 90도 회전"
                @click="rotation = (rotation + 90) % 360"
              >
                <el-icon><RefreshRight /></el-icon></button
              ><button type="button" aria-label="사진 삭제" @click="reset">
                <el-icon><Close /></el-icon>
              </button>
            </div>
          </div>
          <div v-else-if="sampleVisible" class="sample-problem">
            <small>예제 · 유리함수</small>
            <p>{{ sampleQuestion }}</p>
            <button type="button" @click="reset">
              내 문제로 바꾸기<el-icon><ArrowRight /></el-icon>
            </button>
          </div>
          <button
            v-else
            type="button"
            class="photo-dropzone"
            :class="{ dragging }"
            @click="fileInput?.click()"
            @dragover.prevent="dragging = true"
            @dragleave.prevent="dragging = false"
            @drop.prevent="onDrop"
          >
            <span class="photo-upload-icon"
              ><el-icon><Camera /></el-icon></span
            ><strong>수식과 조건이 잘 보이게</strong
            ><span>촬영하거나 저장된 사진을 선택하세요.</span
            ><small>JPG, PNG, WEBP · 최대 12MB</small>
          </button>
          <div class="photo-actions">
            <button type="button" class="camera-button" @click="cameraInput?.click()">
              <el-icon><Camera /></el-icon>카메라로 촬영
            </button>
            <button type="button" @click="fileInput?.click()">
              <el-icon><Picture /></el-icon>사진 선택
            </button>
          </div>
          <p class="paste-help">
            컴퓨터에서는 이미지를 복사한 뒤 이 화면에서 Ctrl+V / ⌘V로 붙여넣으세요.
          </p>
          <p class="photo-status" role="status" aria-live="polite">
            {{ fileName ? `사진을 불러왔어요: ${fileName}` : '' }}
          </p>
          <p class="demo-notice" role="note">
            <i />{{
              serviceConfig?.enabled
                ? serviceConfig.notice
                : trialConfig.ready
                  ? trialConfig.notice
                  : trialConfig.used
                    ? '승인된 실제 API 시험 1건을 사용했습니다. 추가 호출은 별도 확인이 필요합니다.'
                    : '내 사진은 미리보기만 가능합니다. 실제 API는 서버 키와 샘플 시험 설정이 준비되어야 사용할 수 있어요. 예제 체험은 별도로 이용할 수 있습니다.'
            }}
          </p>
          <div v-if="serviceConfig?.enabled" class="question-quota">
            <strong>하루 최대 {{ serviceConfig.dailyLimit }}번 질문할 수 있어요.</strong>
            <p>질문 횟수는 매일 밤 12시(한국 시간)에 초기화돼요.</p>
            <p v-if="serviceConfig.ready || dailyLimitReached">
              오늘 남은 질문 {{ serviceConfig.remainingToday }} / {{ serviceConfig.dailyLimit }}회
            </p>
          </div>
          <div v-if="dailyLimitReached" class="daily-limit-notice" role="status" aria-live="polite">
            <strong>오늘 질문 {{ serviceConfig?.dailyLimit }}번을 모두 사용했어요.</strong>
            <p>
              더 궁금한 문제가 있나요? 밤 12시(한국 시간)에 횟수가 초기화되면 다시 질문할 수 있어요.
              조금만 기다렸다가 다시 만나요!
            </p>
          </div>
          <p v-if="serviceConfig?.enabled" class="photo-help">
            결과는 10분 동안 볼 수 있어요. 요청 후 취소하거나 생성에 실패해도 횟수에 포함됩니다.
          </p>
          <label
            v-if="photo && serviceConfig?.enabled && serviceConfig.ready"
            class="confirm-question"
          >
            <input v-model="trialConsent" type="checkbox" :disabled="!!busy" />이름·얼굴·연락처가
            없는 문제 사진이며, 사진·문제·풀이와 선생님 필체 참고 2개의 OpenAI 전송 안내를
            확인했어요.
          </label>
          <label
            v-if="photo && !serviceConfig?.enabled && trialConfig.ready"
            class="confirm-question"
          >
            <input v-model="trialConsent" type="checkbox" :disabled="!!busy" />개인정보 없는
            샘플이며, 사진·풀이·필체 참고 2개의 OpenAI 전송과 예상 $0.08~$0.15 비용을 확인했어요.
            시험 예산은 $1이며 확정 과금 상한은 아닙니다.
          </label>
          <button
            v-if="photo"
            type="button"
            class="automatic-button"
            :disabled="!!busy || !photoReady || !trialConsent"
            @click="generatePhoto"
          >
            <el-icon><EditPen /></el-icon
            >{{
              busy ||
              (serviceConfig?.enabled
                ? serviceConfig.ready
                  ? '수희쌤 풀이 받기'
                  : dailyLimitReached
                    ? '밤 12시에 다시 질문할 수 있어요'
                    : '지금은 풀이를 받을 수 없어요'
                : trialConfig.ready
                  ? '수희쌤 풀이 받기 · 실제 API'
                  : trialConfig.used
                    ? '샘플 시험 1회 사용 완료'
                    : '내 사진 풀이 · 서버 연결 준비 중')
            }}
          </button>
          <p
            v-if="photo && !serviceConfig?.enabled && !trialConfig.ready && !trialConfig.used"
            class="photo-help"
          >
            이 사진은 아직 읽지 않았어요. 서버 설정 완료 후 문제를 읽고 바로 풀이를 작성합니다.
          </p>
          <button
            type="button"
            v-if="!serviceConfig?.enabled"
            class="sample-button mock-read-button"
            :disabled="!!busy || !config?.enabled"
            @click="readProblem"
          >
            {{ busy || '예제로 자동 풀이 체험' }}
          </button>
          <p v-if="!serviceConfig?.enabled" class="photo-help">
            예제 체험은 선택한 사진과 별개로, 준비된 유리함수 문제를 자동으로 읽고 해설을
            보여줍니다.
          </p>
          <button v-if="busy" type="button" class="sample-button cancel-job" @click="cancel">
            작업 취소
          </button>
          <p v-if="!serviceConfig?.enabled && !config?.enabled" class="photo-help">
            모의 API 서버 연결 전입니다. 상단의 샘플 먼저 보기를 이용해주세요.
          </p>
          <p v-if="!serviceConfig?.enabled" class="automatic-help">
            예제 자동 풀이는 하루 {{ config?.dailySolutionLimit || 3 }}회 체험할 수 있어요.
          </p>
          <p v-if="error" class="photo-error" role="alert">{{ error }}</p>
        </section>
      </div>
      <div class="output-stack">
        <NotebookVerificationPanel :report="verification" />
        <section class="notebook-card result-card">
          <div class="notebook-card-title">
            <h2>나의 해설 노트</h2>
            <button type="button" class="sample-link" @click="showSample">샘플 먼저 보기</button>
          </div>
          <p class="result-kind">
            {{
              sampleVisible
                ? '사전 제작 샘플'
                : resultSolution
                  ? resultMode === 'live'
                    ? serviceConfig?.enabled
                      ? '사진으로 만든 AI 해설'
                      : '실제 AI 시험 결과'
                    : '모의 API 결과'
                  : '미리보기'
            }}
          </p>
          <div class="notebook-tabs">
            <div>
              <button type="button" :class="{ active: tab === 'image' }" @click="tab = 'image'">
                손글씨 해설</button
              ><button type="button" :class="{ active: tab === 'text' }" @click="tab = 'text'">
                풀이 텍스트
              </button>
            </div>
            <button
              type="button"
              aria-label="해설 이미지 크게 보기"
              :disabled="!displayedImage"
              @click="expanded = true"
            >
              <el-icon><FullScreen /></el-icon>
            </button>
          </div>
          <p v-if="sampleVisible" class="sample-disclaimer">
            아래 예제의 사전 제작 해설입니다. 새로 올린 사진을 분석한 결과가 아니에요.
          </p>
          <p v-if="!sampleVisible && resultMessage" class="sample-disclaimer">
            {{ resultMessage }}
          </p>
          <details
            v-if="!busy && !sampleVisible && question && resultMode === 'live'"
            class="read-question"
          >
            <summary>읽은 문제 보기</summary>
            <p>{{ question }}</p>
          </details>
          <div v-if="busy" class="mock-progress" role="status">
            <el-icon class="mock-spinner"><RefreshRight /></el-icon>
            <h3>{{ busy }}</h3>
            <p>
              {{
                liveJobs.size
                  ? '문제 사진에서 풀이를 작성하고 검토하고 있어요.'
                  : '처리 흐름을 확인하고 있어요.'
              }}
            </p>
          </div>
          <div v-else-if="tab === 'text'" class="solution-text">
            <template v-if="resultSolution"
              ><small>{{
                resultMode === 'live' ? '실제 사진 · AI 풀이' : '모의 API · 예제 문제'
              }}</small>
              <h3>{{ resultSolution.title }}</h3>
              <p v-for="(step, index) in resultSolution.steps" :key="index">
                <b>{{ index + 1 }}</b
                >{{ step }}
              </p>
              <div class="sample-answer">
                답 <strong>{{ resultSolution.answer }}</strong>
              </div></template
            >
            <template v-else-if="sampleVisible"
              ><small>샘플 문제 · 유리함수</small>
              <h3>그래프를 읽기 쉬운 꼴로!</h3>
              <p><b>①</b>분자를 2(x−1)+3으로 바꿔요.</p>
              <p><b>②</b>f(x)=2+3/(x−1)로 정리해요.</p>
              <p>
                <b>③</b>점근선은 x=1, y=2예요.<br />그래프의 두 절편은 (−1/2, 0), (0, −1)이에요.
              </p>
              <div class="sample-answer">
                ∴ 두 점근선의 교점은 <strong>(1, 2)</strong>
              </div></template
            >
            <div v-else class="empty-text">
              샘플을 열면 풀이 과정을<br />텍스트로도 확인할 수 있어요.
            </div>
          </div>
          <button
            v-else-if="displayedImage"
            type="button"
            class="sample-image-button"
            aria-label="손글씨 해설 확대"
            @click="expanded = true"
          >
            <img
              :src="displayedImage"
              :alt="
                resultMode === 'live'
                  ? '문제 사진에서 생성한 AI 손글씨 해설'
                  : '유리함수의 점근선과 그래프를 설명하는 사전 제작 손글씨 해설'
              "
            />
          </button>
          <div v-else class="notebook-placeholder">
            <el-icon><Picture /></el-icon>
            <h3>조건 정리부터 마지막 답까지</h3>
            <p>조건 정리부터 계산과 결론까지.<br />선생님 스타일의 샘플을 먼저 살펴보세요.</p>
            <button type="button" @click="showSample">
              샘플 열기<el-icon><ArrowRight /></el-icon>
            </button>
          </div>
          <div class="notebook-result-footer">
            <span>{{
              sampleVisible
                ? '식 변형·점근선·절편을 사전 확인한 예제'
                : resultSolution
                  ? resultMode === 'live'
                    ? 'AI 해설 · 오류가 있을 수 있어요'
                    : '모의 API · 예제용 사전 제작 이미지'
                  : '별도 모델 훈련 없이 만든 해설 시안'
            }}</span
            ><a
              v-if="displayedImage"
              :href="displayedImage"
              :download="resultMode === 'live' ? '김수학_AI해설.png' : '김수학_모의해설.png'"
              ><el-icon><Download /></el-icon>이미지 저장</a
            >
          </div>
        </section>
      </div>
    </div>
    <p class="notebook-privacy">
      사진 선택만으로는 외부 전송하지 않습니다. 풀이 받기를 누르면 사진·문제·풀이와 필체 참고 2개를
      OpenAI로 전송합니다. 사진 안의 개인정보도 포함될 수 있으니 이름·얼굴·연락처를 제외해주세요.
      서비스는 사진·결과를 학습 기록으로 저장하지 않으며, 임시 결과는 요청 후 약 10분 안에
      삭제합니다. 취소·화면 종료 시 삭제를 요청합니다. 새로고침하면 진행 상태를 다시 불러올 수
      없으므로 완성된 이미지는 저장해주세요. 사용 한도 기록은 최근 35일의 계정별 횟수와 이번
      달·지난달의 전체 횟수만 남기도록 다음 이용 요청 시 정리합니다. OpenAI는 API 자료를 기본적으로
      모델 훈련에 사용하지 않지만 악용 감시 기록을 통상 최대 30일 보관할 수 있습니다.
      <router-link to="/privacy">개인정보처리방침</router-link>
    </p>
    <el-dialog
      v-model="expanded"
      title="손글씨 해설 크게 보기"
      width="min(850px, 95vw)"
      align-center
      destroy-on-close
      ><img class="expanded-solution" :src="displayedImage" alt="확대한 손글씨 해설"
    /></el-dialog>
  </section>
</template>

<style scoped src="@/assets/handwritten-solution.css"></style>
