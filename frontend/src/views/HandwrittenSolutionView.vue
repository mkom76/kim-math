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
  UploadFilled,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import {
  solutionNotebookAPI,
  type NotebookConfig,
  type NotebookJob,
  type NotebookSolution,
} from '@/api/solutionNotebook'
import sampleUrl from '@/assets/handwriting-solution-sample.png'

const router = useRouter(),
  auth = useAuthStore()
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
const resultImage = ref(''),
  resultSolution = ref<NotebookSolution | null>(null),
  resultMessage = ref('')
const sampleQuestion = '유리함수 f(x)=(2x+1)/(x−1)의 그래프를 그리고, 두 점근선의 교점을 구하시오.'
const backPath = computed(() => (auth.role === 'STUDENT' ? '/student/more' : '/students'))
const photoStyle = computed(() => ({
  transform: `rotate(${rotation.value}deg)`,
  maxWidth: rotation.value % 180 ? '55%' : '100%',
}))
const displayedImage = computed(() => (sampleVisible.value ? sampleUrl : resultImage.value))
const hasResult = computed(() => sampleVisible.value || resultSolution.value !== null)
const phases: Record<string, string> = {
  QUEUED: '모의 작업 대기',
  READING: '모의 문제 읽기',
  SOLVING: '모의 풀이 작성',
  VERIFYING: '모의 풀이 검토',
  RENDERING: '모의 이미지 준비',
  CHECKING_IMAGE: '모의 이미지 검토',
}
let epoch = 0,
  controller: AbortController | null = null,
  expiryTimer: ReturnType<typeof setTimeout> | undefined
const jobs = new Set<string>()
function reason(e: unknown) {
  const v = e as { response?: { data?: { message?: string } }; message?: string }
  return v.response?.data?.message || v.message || '처리하지 못했어요. 다시 시도해주세요.'
}
function removeJob(id: string) {
  jobs.delete(id)
  void solutionNotebookAPI.remove(id).catch(() => undefined)
}
function clearResult() {
  if (resultImage.value) URL.revokeObjectURL(resultImage.value)
  resultImage.value = ''
  resultSolution.value = null
  resultMessage.value = ''
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
  const until = Date.now() + 300000
  while (
    ['QUEUED', 'READING', 'SOLVING', 'VERIFYING', 'RENDERING', 'CHECKING_IMAGE'].includes(job.state)
  ) {
    if (n !== epoch) throw new Error('취소된 작업입니다.')
    if (Date.now() > until) {
      removeJob(job.id)
      throw new Error('모의 처리 시간이 초과됐어요. 다시 시도해주세요.')
    }
    busy.value = phases[job.state] || '모의 처리 중'
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
    job = await solutionNotebookAPI.status(job.id, signal)
    if (job.mode !== 'mock') throw new Error('허용되지 않은 API 모드입니다.')
  }
  if (job.state === 'FAILED') throw new Error(job.message)
  return job
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
  try {
    const c = await solutionNotebookAPI.config()
    if (c.mode === 'mock') config.value = c
  } catch {
    config.value = null
  }
})
onBeforeUnmount(() => {
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
      <span class="prototype-badge">모의 API · 외부 전송 없음</span>
    </div>
    <div class="notebook-intro">
      <div>
        <p class="notebook-eyebrow">KIM MATH · 선생님 풀이노트</p>
        <h1>수희쌤 스타일로,<br /><em>손글씨풀이</em></h1>
        <p class="intro-copy">선생님의 풀이 방식과 손글씨를 참고한 해설을 미리 만나보세요.</p>
      </div>
      <button type="button" class="sample-link" @click="showSample">
        샘플 먼저 보기<el-icon><ArrowRight /></el-icon>
      </button>
    </div>
    <ol class="notebook-progress" aria-label="풀이 진행 단계">
      <li :class="{ complete: photo || sampleVisible }">
        <span>{{ photo || sampleVisible ? '✓' : '1' }}</span
        >문제 사진
      </li>
      <li :class="{ complete: question }">
        <span>{{ question ? '✓' : '2' }}</span
        >자동 풀이
      </li>
      <li :class="{ complete: hasResult }">
        <span>{{ hasResult ? '✓' : '3' }}</span
        >해설 노트
      </li>
    </ol>
    <p class="demo-notice" role="note">
      <i />모의 API 체험입니다. 내 사진은 미리보기만 가능하며 아직 분석하지 않습니다. 자동 처리
      과정은 아래의 예제로 따로 체험할 수 있어요.
    </p>
    <div class="notebook-columns">
      <div class="input-stack">
        <section class="notebook-card">
          <div class="notebook-card-title">
            <h2><span>01</span>어떤 문제가 궁금한가요?</h2>
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
              ><el-icon><UploadFilled /></el-icon></span
            ><strong>문제 사진을 여기에 놓아주세요</strong><span>또는 클릭해서 사진 선택</span
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
          <p class="photo-help">
            <el-icon><Camera /></el-icon>수식과 조건이 잘 보이도록, 한 문제만 담아주세요.
          </p>
          <button v-if="photo" type="button" class="automatic-button" disabled>
            <el-icon><EditPen /></el-icon>내 사진 풀이 · 실제 연결 준비 중
          </button>
          <p v-if="photo" class="photo-help">
            이 사진은 아직 읽지 않았어요. 실제 연결 후에는 사진에서 문제를 읽고 바로 풀이를
            작성합니다.
          </p>
          <button
            type="button"
            class="sample-button mock-read-button"
            :disabled="!!busy || !config?.enabled"
            @click="readProblem"
          >
            {{ busy || '예제로 자동 풀이 체험' }}
          </button>
          <p class="photo-help">
            예제 체험은 선택한 사진과 별개로, 준비된 유리함수 문제를 자동으로 읽고 해설을
            보여줍니다.
          </p>
          <button v-if="busy" type="button" class="sample-button cancel-job" @click="cancel">
            작업 취소
          </button>
          <p v-if="!config?.enabled" class="photo-help">
            모의 API 서버 연결 전입니다. 상단의 샘플 먼저 보기를 이용해주세요.
          </p>
          <p class="automatic-help">
            예제 자동 풀이는 하루 {{ config?.dailySolutionLimit || 3 }}회 체험할 수 있어요.
          </p>
          <p v-if="error" class="photo-error" role="alert">{{ error }}</p>
        </section>
      </div>
      <div class="output-stack">
        <section class="notebook-card result-card">
          <div class="notebook-card-title">
            <h2><span>02</span>나의 해설 노트</h2>
            <small>{{
              sampleVisible ? '사전 제작 샘플' : resultSolution ? '모의 API 결과' : '미리보기'
            }}</small>
          </div>
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
          <div v-if="busy" class="mock-progress" role="status">
            <el-icon class="mock-spinner"><RefreshRight /></el-icon>
            <h3>{{ busy }}</h3>
            <p>고정된 예제로 처리 흐름을 확인하고 있어요.</p>
          </div>
          <div v-else-if="tab === 'text'" class="solution-text">
            <template v-if="resultSolution"
              ><small>모의 API · 예제 문제</small>
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
            aria-label="샘플 해설 확대"
            @click="expanded = true"
          >
            <img
              :src="displayedImage"
              alt="유리함수의 점근선과 그래프를 설명하는 사전 제작 손글씨 해설"
            />
          </button>
          <div v-else class="notebook-placeholder">
            <el-icon><Picture /></el-icon>
            <h3>익숙한 필체로, 핵심이 보이게.</h3>
            <p>조건 정리부터 그림과 결론까지.<br />선생님 스타일의 샘플을 먼저 살펴보세요.</p>
            <button type="button" @click="showSample">
              샘플 열기<el-icon><ArrowRight /></el-icon>
            </button>
          </div>
          <div class="notebook-result-footer">
            <span>{{
              sampleVisible
                ? '식 변형·점근선·절편을 사전 확인한 예제'
                : resultSolution
                  ? '모의 API · 예제용 사전 제작 이미지'
                  : '별도 모델 훈련 없이 만든 해설 시안'
            }}</span
            ><a v-if="displayedImage" :href="displayedImage" download="김수학_모의해설.png"
              ><el-icon><Download /></el-icon>이미지 저장</a
            >
          </div>
        </section>
        <div class="teacher-style-note">
          <span>f(x)</span>
          <div>
            <strong>선생님의 풀이 습관을 담았어요</strong>
            <p>조건은 짧게 · 그림으로 이해 · 핵심은 색으로</p>
          </div>
          <i /><i /><i />
        </div>
      </div>
    </div>
    <p class="notebook-privacy">
      선택한 사진은 브라우저 안에만 있어요. 서버의 모의 작업은 약 10분 후 삭제되며 새로고침 시 이
      화면의 진행 상태는 사라져요. 실제 AI 연결과 학생 사진 전송은 별도 확인 후 진행합니다.
    </p>
    <el-dialog
      v-model="expanded"
      title="샘플 해설 크게 보기"
      width="min(850px, 95vw)"
      align-center
      destroy-on-close
      ><img class="expanded-solution" :src="displayedImage" alt="확대한 샘플 손글씨 해설"
    /></el-dialog>
  </section>
</template>

<style scoped>
.photo-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}
.photo-actions button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  flex: 1;
  min-height: 46px;
  padding: 10px;
  border: 1px solid #dce3d6;
  border-radius: 10px;
  background: #fff;
  color: #40583b;
  font: inherit;
  cursor: pointer;
}
.photo-actions .camera-button {
  background: #eff4e9;
}
.photo-actions button:focus-visible {
  outline: 2px solid #496641;
  outline-offset: 3px;
}
@media (max-width: 480px) {
  .photo-actions {
    flex-direction: column;
  }
}
.paste-help,
.photo-status {
  font-size: 12px;
  line-height: 1.7;
  color: #727c6c;
  overflow-wrap: anywhere;
}
.photo-status:empty {
  display: none;
}
.handwriting-notebook {
  --note-ink: #2b3c33;
  --note-muted: #879280;
  --note-border: #e2e7dd;
  max-width: 1200px;
  margin: 0 auto;
  padding: 26px 25px 40px;
  color: var(--note-ink);
  background: #f8f9f5;
  border-radius: 18px;
  font-size: 14px;
  min-width: 0;
}
.handwriting-notebook * {
  box-sizing: border-box;
}
.handwriting-notebook button,
.handwriting-notebook textarea,
.handwriting-notebook select {
  font: inherit;
}
.handwriting-notebook button {
  cursor: pointer;
  touch-action: manipulation;
}
.handwriting-notebook button:focus-visible,
.handwriting-notebook a:focus-visible {
  outline: 3px solid #92a880;
  outline-offset: 3px;
}
.handwriting-notebook button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.notebook-topline {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}
.back-button {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px !important;
  color: #84917d;
  background: none;
  border: 0;
  padding: 4px 0;
}
.prototype-badge {
  border: 1px solid var(--note-border);
  border-radius: 20px;
  padding: 5px 10px;
  font-size: 10px;
  color: #8e9b83;
}
.notebook-intro {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin: 27px 0;
}
.notebook-eyebrow {
  font-size: 10px;
  letter-spacing: 1.5px;
  color: #86977d;
}
.notebook-intro h1 {
  font-size: 35px;
  line-height: 1.45;
  letter-spacing: -1.5px;
  margin: 12px 0;
  font-weight: 750;
  color: #263b2e;
}
.notebook-intro h1 em {
  font-style: normal;
  color: #496b46;
}
.intro-copy {
  font-size: 12px;
  color: #89927f;
  line-height: 1.8;
}
.sample-link {
  display: flex;
  gap: 13px;
  align-items: center;
  background: none;
  border: 0;
  border-bottom: 1px solid #cbd5c1;
  padding: 7px 0;
  color: #667d57;
  font-size: 11px !important;
  white-space: nowrap;
}
.notebook-progress {
  display: flex;
  align-items: center;
  gap: 34px;
  list-style: none;
  padding: 16px 20px;
  background: #fff;
  border: 1px solid var(--note-border);
  border-radius: 10px;
}
.notebook-progress li {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 11px;
  color: #a4ad9b;
}
.notebook-progress li span {
  width: 21px;
  height: 21px;
  display: grid;
  place-items: center;
  border: 1px solid #dce4d2;
  border-radius: 50%;
  font-size: 10px;
}
.notebook-progress li.complete {
  color: #63804c;
}
.notebook-progress li.complete span {
  background: #ebf1e3;
}
.demo-notice {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 10px;
  line-height: 1.8;
  color: #8b987c;
  margin: 16px 0 21px;
}
.demo-notice i {
  height: 5px;
  width: 5px;
  border-radius: 50%;
  background: #a7b592;
  flex-shrink: 0;
}
.notebook-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 22px;
  align-items: start;
}
.input-stack,
.output-stack {
  display: grid;
  gap: 18px;
  min-width: 0;
}
.notebook-card {
  border: 1px solid var(--note-border);
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
}
.notebook-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 21px 23px;
}
.notebook-card-title h2 {
  font-size: 14px;
  letter-spacing: -0.5px;
  margin: 0;
  display: flex;
  gap: 10px;
  align-items: center;
  color: #33432e;
}
.notebook-card-title h2 > span {
  font-family: Georgia, serif;
  font-style: italic;
  color: #aab79d;
  font-size: 13px;
  font-weight: 400;
}
.notebook-card-title > small {
  font-size: 9px;
  color: #a6af9b;
  white-space: nowrap;
}
.file-input {
  display: none;
}
.photo-dropzone {
  width: calc(100% - 46px);
  margin: 0 23px;
  height: 230px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: #fafcf6;
  border: 1px dashed #c9d5bf;
  border-radius: 9px;
  color: #738b5e;
}
.photo-dropzone.dragging {
  background: #e9f1de;
  border-color: #74935a;
}
.photo-dropzone strong {
  font-size: 12px;
  font-weight: 500;
}
.photo-dropzone > span:not(.photo-upload-icon) {
  font-size: 10px;
  color: #9cae8a;
}
.photo-dropzone > small {
  font-size: 9px;
  color: #afbaa1;
  margin-top: 10px;
}
.photo-upload-icon {
  height: 56px;
  width: 54px;
  border: 1px solid #e0e8d5;
  background: #edf3e4;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-size: 28px;
  margin-bottom: 9px;
  transform: rotate(-5deg);
}
.photo-help {
  display: flex;
  gap: 7px;
  align-items: center;
  font-size: 10px;
  color: #a2ad94;
  margin: 16px 23px 21px;
  line-height: 1.7;
}
.photo-error {
  color: #b5624c;
  background: #fff0e9;
  margin: 12px 23px;
  padding: 10px;
  border-radius: 6px;
  font-size: 11px;
}
.photo-preview {
  margin: 0 23px;
  border: 1px solid var(--note-border);
  border-radius: 8px;
  overflow: hidden;
}
.photo-stage {
  height: 240px;
  display: grid;
  place-items: center;
  background: #fafbf7;
  overflow: hidden;
}
.photo-stage img {
  max-height: 225px;
  object-fit: contain;
  transition: transform 0.2s;
}
.photo-toolbar {
  display: flex;
  gap: 7px;
  align-items: center;
  padding: 8px 10px;
  border-top: 1px solid var(--note-border);
}
.photo-toolbar > span {
  font-size: 10px;
  color: #819473;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.photo-toolbar button {
  display: grid;
  place-items: center;
  padding: 6px;
  background: none;
  border: 0;
  color: #7f946b;
}
.sample-problem {
  margin: 0 23px;
  background: #f8fbf2;
  border: 1px solid #e7eedc;
  border-radius: 8px;
  padding: 20px;
}
.sample-problem small {
  font-size: 10px;
  color: #94a77c;
}
.sample-problem p {
  font-size: 13px;
  line-height: 2;
  margin: 13px 0;
}
.sample-problem button {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 0;
  border: 0;
  background: none;
  color: #8ca06f;
  font-size: 10px !important;
}
.input-card {
  padding-bottom: 20px;
}
.question-label {
  font-size: 11px;
  display: block;
  margin: 0 23px 9px;
  color: #788d65;
}
.question-label small {
  display: block;
  font-size: 9px;
  color: #a3b08f;
  margin-top: 5px;
}
.input-card textarea {
  margin: 0 23px;
  width: calc(100% - 46px);
  min-height: 115px;
  resize: vertical;
  border: 1px solid #dfe6d4;
  background: #fcfdf9;
  border-radius: 6px;
  padding: 11px;
  color: #556d42;
  font-size: 12px;
  line-height: 1.9;
  outline: none;
}
.input-card textarea:focus {
  border-color: #8eaa71;
}
.input-card textarea::placeholder {
  color: #b1bea0;
}
.notebook-options {
  display: flex;
  gap: 16px;
  margin: 17px 23px 19px;
}
.notebook-options > label {
  width: 32%;
  font-size: 11px;
  color: #81976b;
}
.notebook-options select {
  display: block;
  margin-top: 7px;
  width: 100%;
  height: 35px;
  background: #fff;
  border: 1px solid #dfe7d3;
  border-radius: 6px;
  padding: 0 7px;
  font-size: 12px;
  color: #708851;
}
.notebook-options fieldset {
  border: 0;
  margin: 0;
  padding: 0;
  flex: 1;
}
.notebook-options legend {
  font-size: 11px;
  color: #81976b;
  padding: 0;
  margin-bottom: 7px;
}
.depth-options {
  display: flex;
  border: 1px solid #dfe7d3;
  border-radius: 6px;
  height: 35px;
  padding: 3px;
  gap: 3px;
}
.depth-options button {
  flex: 1;
  border: 0;
  border-radius: 4px;
  background: #fff;
  color: #a4b18d;
  font-size: 10px;
}
.depth-options button.selected {
  background: #edf3e2;
  color: #708b50;
}
.automatic-button,
.sample-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  width: calc(100% - 46px);
  margin: 0 23px;
  border: 0;
  border-radius: 7px;
  padding: 12px;
  background: #456342;
  color: #fff;
  font-size: 12px !important;
}
.automatic-help {
  font-size: 9px;
  color: #a3b28f;
  text-align: center;
  margin: 10px 15px 17px;
}
.sample-button {
  background: #f4f7ed;
  color: #758e55;
  border: 1px solid #e0e8d2;
}
.notebook-tabs {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e8eedf;
  padding: 0 23px;
  height: 37px;
}
.notebook-tabs > div {
  display: flex;
  gap: 18px;
  height: 100%;
}
.notebook-tabs button {
  border: 0;
  background: none;
  font-size: 10px;
  color: #a5b194;
  padding: 0 0 10px;
  position: relative;
}
.notebook-tabs button.active {
  color: #708d50;
}
.notebook-tabs button.active:after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: #829c62;
}
.notebook-tabs > button {
  font-size: 16px;
  color: #8ea66e;
}
.sample-disclaimer {
  font-size: 10px;
  line-height: 1.7;
  color: #a18a61;
  background: #faf6ed;
  padding: 10px 16px;
  margin: 0;
}
.notebook-placeholder {
  height: 440px;
  background: #fafcf6;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  text-align: center;
  padding: 25px;
}
.notebook-placeholder > .el-icon {
  font-size: 40px;
  color: #c2cfae;
}
.notebook-placeholder h3 {
  font-size: 15px;
  font-weight: 500;
  color: #81996b;
  line-height: 1.7;
}
.notebook-placeholder p {
  font-size: 11px;
  color: #a0b08a;
  line-height: 1.9;
}
.notebook-placeholder button {
  border: 1px solid #dce7cb;
  background: #fff;
  color: #7e9963;
  border-radius: 6px;
  padding: 10px 18px;
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 11px;
}
.sample-image-button {
  display: block;
  width: 100%;
  border: 0;
  background: #fafcf7;
  padding: 18px;
}
.sample-image-button img {
  width: 100%;
  display: block;
  border: 1px solid #e9eedf;
}
.notebook-result-footer {
  padding: 15px 19px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  background: #fdfefb;
  border-top: 1px solid #e6edda;
  font-size: 9px;
  color: #a3b48a;
  line-height: 1.8;
}
.notebook-result-footer a {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 10px;
  color: #7e9b5c;
  text-decoration: none;
  white-space: nowrap;
}
.teacher-style-note {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 17px;
  background: #edf3e4;
  border: 1px solid #dfe9d1;
  border-radius: 9px;
}
.teacher-style-note > span {
  font-family: Georgia, serif;
  font-style: italic;
  font-size: 24px;
  color: #9ab67b;
  transform: rotate(-8deg);
}
.teacher-style-note strong {
  font-size: 10px;
  color: #809c61;
  font-weight: 500;
}
.teacher-style-note p {
  font-size: 9px;
  color: #a1b58a;
  margin: 5px 0 0;
}
.teacher-style-note i {
  height: 16px;
  width: 7px;
  background: #597348;
  border-radius: 5px;
  transform: rotate(13deg);
  opacity: 0.5;
}
.teacher-style-note i:first-of-type {
  margin-left: auto;
}
.teacher-style-note i:nth-of-type(2) {
  background: #c98970;
}
.teacher-style-note i:nth-of-type(3) {
  background: #ccdb86;
}
.notebook-privacy {
  font-size: 10px;
  color: #a8b697;
  line-height: 1.9;
  margin: 23px 0 0;
  padding-top: 16px;
  border-top: 1px solid #e0e9d5;
}
.solution-text {
  min-height: 450px;
  padding: 23px;
}
.solution-text > small {
  color: #a3b68a;
  font-size: 10px;
}
.solution-text h3 {
  font-size: 17px;
  font-weight: 500;
  margin-bottom: 25px;
  color: #667f4d;
}
.solution-text p {
  display: flex;
  gap: 12px;
  line-height: 2;
  font-size: 12px;
  margin: 20px 0;
  color: #7f9469;
}
.solution-text b {
  color: #a0b786;
}
.sample-answer {
  background: #f0f6e6;
  color: #879f6c;
  padding: 15px;
  font-size: 11px;
  border-radius: 6px;
  line-height: 1.9;
}
.sample-answer strong {
  font-size: 17px;
  margin-left: 7px;
}
.empty-text {
  padding-top: 140px;
  text-align: center;
  font-size: 12px;
  color: #acbe95;
  line-height: 1.9;
}
.expanded-solution {
  display: block;
  width: 100%;
  height: auto;
}
@media (max-width: 850px) {
  .handwriting-notebook {
    padding: 20px 17px 30px;
  }
  .notebook-columns {
    grid-template-columns: 1fr;
  }
  .notebook-intro h1 {
    font-size: 30px;
  }
  .notebook-intro {
    align-items: flex-start;
    gap: 15px;
  }
  .sample-link {
    margin-top: 34px;
  }
  .teacher-style-note i {
    display: none;
  }
  .notebook-placeholder {
    height: 300px;
  }
}
@media (max-width: 480px) {
  .handwriting-notebook {
    border-radius: 0;
    padding: 18px 14px 25px;
  }
  .notebook-intro {
    display: block;
  }
  .sample-link {
    margin-top: 12px;
  }
  .notebook-eyebrow {
    font-size: 9px;
  }
  .intro-copy {
    font-size: 11px;
  }
  .notebook-progress {
    gap: 22px;
    padding: 14px;
  }
  .notebook-progress li {
    font-size: 10px;
    gap: 6px;
  }
  .notebook-card-title {
    padding: 20px;
  }
  .notebook-card-title h2 {
    font-size: 13px;
  }
  .prototype-badge {
    font-size: 9px;
  }
  .demo-notice {
    font-size: 9px;
  }
  .notebook-result-footer {
    flex-wrap: wrap;
  }
  .notebook-privacy {
    font-size: 9px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .photo-stage img {
    transition: none;
  }
}

.mock-read-button {
  margin-bottom: 20px;
}
.confirm-question {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 15px 23px;
  color: #6c8555;
  font-size: 11px;
}
.mock-scenario {
  display: block;
  margin: 17px 23px;
  color: #7b9066;
  font-size: 11px;
}
.mock-scenario select {
  display: block;
  width: 100%;
  margin-top: 7px;
  border: 1px solid #dfe7d3;
  border-radius: 6px;
  padding: 9px;
  background: #fff;
  color: #708851;
  font-size: 12px;
}
.mock-progress {
  min-height: 350px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  background: #f9fcf4;
  color: #7c9662;
}
.mock-progress h3 {
  font-size: 15px;
  font-weight: 500;
}
.mock-progress p {
  font-size: 11px;
  color: #9bad88;
}
.mock-spinner {
  font-size: 29px;
  animation: mock-spin 1.5s linear infinite;
}
.cancel-job {
  margin-top: 12px;
}
@keyframes mock-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .mock-spinner {
    animation: none;
  }
}
</style>
