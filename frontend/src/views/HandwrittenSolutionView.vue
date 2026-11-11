<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  Camera,
  Download,
  EditPen,
  FullScreen,
  Picture,
  RefreshRight,
  UploadFilled,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import sampleUrl from '@/assets/handwriting-solution-sample.png'

const router = useRouter()
const auth = useAuthStore()
const fileInput = ref<HTMLInputElement | null>(null)
const photo = ref('')
const fileName = ref('')
const rotation = ref(0)
const question = ref('')
const error = ref('')
const dragging = ref(false)
const sampleVisible = ref(false)
const tab = ref<'image' | 'text'>('image')
const expanded = ref(false)
const grade = ref('고1')
const depth = ref('핵심 위주')
const sampleQuestion = '유리함수 f(x)=(2x+1)/(x−1)의 그래프를 그리고, 두 점근선의 교점을 구하시오.'
const backPath = computed(() => (auth.role === 'STUDENT' ? '/student/more' : '/students'))
const photoStyle = computed(() => ({
  transform: `rotate(${rotation.value}deg)`,
  maxWidth: rotation.value % 180 ? '55%' : '100%',
}))

function clearPhoto() {
  if (photo.value) URL.revokeObjectURL(photo.value)
  photo.value = ''
  fileName.value = ''
  rotation.value = 0
}
function reset() {
  clearPhoto()
  question.value = ''
  error.value = ''
  sampleVisible.value = false
  tab.value = 'image'
  expanded.value = false
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
function showSample() {
  reset()
  question.value = sampleQuestion
  sampleVisible.value = true
}
function editQuestion() {
  sampleVisible.value = false
  expanded.value = false
}
function onDrop(event: DragEvent) {
  selectFile(event.dataTransfer?.files[0])
}
function onEscape(event: KeyboardEvent) {
  if (event.key === 'Escape') expanded.value = false
}
onMounted(() => window.addEventListener('keydown', onEscape))
onBeforeUnmount(() => {
  clearPhoto()
  window.removeEventListener('keydown', onEscape)
})
</script>

<template>
  <section class="handwriting-notebook">
    <div class="notebook-topline">
      <button type="button" class="back-button" @click="router.push(backPath)">
        <el-icon><ArrowLeft /></el-icon>돌아가기
      </button>
      <span class="prototype-badge">샘플 체험 · 준비 중인 기능</span>
    </div>
    <div class="notebook-intro">
      <div>
        <p class="notebook-eyebrow">KIM MATH · 선생님 풀이노트</p>
        <h1>사진 한 장이,<br /><em>이해되는 풀이로.</em></h1>
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
        >문제 확인
      </li>
      <li :class="{ complete: sampleVisible }">
        <span>{{ sampleVisible ? '✓' : '3' }}</span
        >해설 노트
      </li>
    </ol>
    <p class="demo-notice" role="note">
      <i />지금은 샘플 체험 단계예요. 새 사진의 자동 풀이는 준비 중이며, 사진은 이 화면에서만
      보여요.
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
          <p class="photo-help">
            <el-icon><Camera /></el-icon>수식과 조건이 잘 보이도록, 한 문제만 담아주세요.
          </p>
          <p v-if="error" class="photo-error" role="alert">{{ error }}</p>
        </section>
        <section class="notebook-card input-card">
          <div class="notebook-card-title">
            <h2><span>02</span>문제와 풀이 설정</h2>
            <small>한 번 확인해요</small>
          </div>
          <label class="question-label" for="notebook-question"
            >문제 내용<small>자동 판독 연결 전에는 직접 입력할 수 있어요.</small></label
          >
          <textarea
            id="notebook-question"
            v-model="question"
            maxlength="6000"
            placeholder="문제의 수식과 조건을 적어보세요."
            @input="editQuestion"
          />
          <div class="notebook-options">
            <label
              >학년<select v-model="grade">
                <option>중학생</option>
                <option>고1</option>
                <option>고2</option>
                <option>고3</option>
              </select></label
            >
            <fieldset>
              <legend>설명 깊이</legend>
              <div class="depth-options">
                <button
                  v-for="option in ['핵심 위주', '차근차근']"
                  :key="option"
                  type="button"
                  :aria-pressed="depth === option"
                  :class="{ selected: depth === option }"
                  @click="depth = option"
                >
                  {{ option }}
                </button>
              </div>
            </fieldset>
          </div>
          <button type="button" class="automatic-button" disabled>
            <el-icon><EditPen /></el-icon>새 문제 자동 풀이 · 준비 중
          </button>
          <p class="automatic-help">입력한 문제를 분석하거나 외부로 전송하지 않습니다.</p>
          <button type="button" class="sample-button" @click="showSample">
            준비된 샘플 해설 보기<el-icon><ArrowRight /></el-icon>
          </button>
        </section>
      </div>
      <div class="output-stack">
        <section class="notebook-card result-card">
          <div class="notebook-card-title">
            <h2><span>03</span>나의 해설 노트</h2>
            <small>{{ sampleVisible ? '사전 제작 샘플' : '미리보기' }}</small>
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
              :disabled="!sampleVisible"
              @click="expanded = true"
            >
              <el-icon><FullScreen /></el-icon>
            </button>
          </div>
          <p v-if="sampleVisible" class="sample-disclaimer">
            아래 예제의 사전 제작 해설입니다. 새로 올린 사진을 분석한 결과가 아니에요.
          </p>
          <div v-if="tab === 'text'" class="solution-text">
            <template v-if="sampleVisible"
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
            v-else-if="sampleVisible"
            type="button"
            class="sample-image-button"
            aria-label="샘플 해설 확대"
            @click="expanded = true"
          >
            <img
              :src="sampleUrl"
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
                : '별도 모델 훈련 없이 만든 해설 시안'
            }}</span
            ><a v-if="sampleVisible" :href="sampleUrl" download="김수학_샘플해설.png"
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
      사진과 입력 내용은 이 화면을 벗어나거나 새로고침하면 사라져요. 샘플 이미지의 필체는 원본과
      완전히 같지는 않습니다.
    </p>
    <el-dialog
      v-model="expanded"
      title="샘플 해설 크게 보기"
      width="min(850px, 95vw)"
      align-center
      destroy-on-close
      ><img class="expanded-solution" :src="sampleUrl" alt="확대한 샘플 손글씨 해설"
    /></el-dialog>
  </section>
</template>

<style scoped>
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
</style>
