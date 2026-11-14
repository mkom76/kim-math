<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TestSubmissionRoster } from '@/api/client'
import { omrAPI, type OmrMark, type OmrPage, type OmrScan } from '@/api/omr'

const props = defineProps<{ testId: number; roster: TestSubmissionRoster[] }>()
const emit = defineEmits<{ close: []; saved: [] }>()
type DraftMark = OmrMark & { needsReview: boolean; verified: boolean; multipleAnswers: boolean }
type DraftPage = Omit<OmrPage, 'marks'> & { marks: DraftMark[]; studentId: number | null; reviewed: boolean; saved: boolean; score?: number }
const scan = ref<OmrScan | null>(null)
const pages = ref<DraftPage[]>([])
const active = ref(0)
const focusedNumber = ref<number | null>(null)
const file = ref<File | null>(null)
const busy = ref(false)
const error = ref('')
const current = computed(() => pages.value[active.value])
const focused = computed(() => current.value?.marks.find(mark => mark.number === focusedNumber.value))
const savedCount = computed(() => pages.value.filter(page => page.saved).length)
const unresolved = computed(() => current.value?.marks.filter(mark => mark.needsReview && !mark.verified).length || 0)
const duplicate = computed(() => Boolean(current.value?.studentId && pages.value.some((page, index) => index !== active.value && page.studentId === current.value?.studentId)))
const studentSubmitted = computed(() => props.roster.find(row => row.studentId === current.value?.studentId)?.submitted)
const validAnswers = computed(() => current.value?.marks.every(mark => /^(?:[1-5](?:,[1-5]){0,4})?$/.test(mark.answer)))
const canSave = computed(() => Boolean(current.value?.recognized && current.value.studentId && current.value.reviewed
  && !current.value.saved && !unresolved.value && !duplicate.value && !studentSubmitted.value && validAnswers.value && !busy.value))
const blankCount = computed(() => current.value?.marks.filter(mark => !mark.answer).length || 0)

function message(cause: unknown, fallback: string) {
  return (cause as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback
}
function chooseFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] || null
  error.value = ''
}
async function upload() {
  if (busy.value || !file.value) return
  if (!/\.pdf$/i.test(file.value.name) || file.value.size > 8 * 1024 * 1024 || file.value.size === 0) {
    error.value = '8MB 이하의 스캔 PDF를 선택해 주세요.'
    return
  }
  busy.value = true; error.value = ''
  try {
    const result = (await omrAPI.scan(props.testId, file.value)).data
    scan.value = result
    pages.value = result.pages.map(page => ({ ...page, studentId: null, reviewed: false, saved: false,
      marks: page.marks.filter(mark => result.questions.some(q => q.number === mark.number)).map(mark => {
        const multipleAnswers = result.questions.find(q => q.number === mark.number)?.multipleAnswers || false
        return { ...mark, multipleAnswers, verified: false, needsReview: mark.status === 'UNCERTAIN' || (mark.status === 'MULTIPLE' && !multipleAnswers) }
      }),
    }))
    active.value = 0; focusedNumber.value = null; file.value = null
  } catch (cause) { error.value = message(cause, 'OMR을 인식하지 못했습니다. PDF를 확인하고 다시 시도해 주세요.') }
  finally { busy.value = false }
}
function invalidate() {
  if (current.value) current.value.reviewed = false
}
function switchPage() { focusedNumber.value = null; error.value = '' }
function focus(mark: DraftMark) { focusedNumber.value = mark.number }
async function save() {
  if (!canSave.value || !current.value || !scan.value) return
  busy.value = true; error.value = ''
  const page = current.value
  try {
    const student = props.roster.find(row => row.studentId === page.studentId)
    await ElMessageBox.confirm(`${student?.studentName} 학생의 ${page.number}쪽 답안을 저장하고 채점합니다. 무응답 ${blankCount.value}문항은 오답 처리됩니다.`, 'OMR 채점 확정', {
      confirmButtonText: '저장·채점', cancelButtonText: '취소', type: 'warning',
    })
    const result = await omrAPI.confirm(props.testId, page.studentId!, Object.fromEntries(page.marks.map(mark => [mark.number, mark.answer])), scan.value.expectedQuestions)
    page.saved = true; page.score = result.data.totalScore
    emit('saved')
    ElMessage.success(`${student?.studentName} 학생의 채점이 완료되었습니다.`)
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close') error.value = message(cause, '저장 결과를 확인하지 못했습니다. 시험의 응시 현황을 확인한 뒤 다시 시도해 주세요. 기존 답안은 덮어쓰지 않습니다.')
  } finally { busy.value = false }
}
async function discard(): Promise<boolean> {
  if (busy.value) return false
  if (pages.value.some(page => !page.saved)) {
    try { await ElMessageBox.confirm('아직 저장하지 않은 페이지의 학생 연결과 수정 내용이 사라집니다. 저장된 성적은 유지됩니다.', 'OMR 검토 종료', { confirmButtonText: '종료', cancelButtonText: '계속 검토', type: 'warning' }) }
    catch { return false }
  }
  return true
}
async function close() { if (await discard()) emit('close') }
async function reset() {
  if (await discard()) { scan.value = null; pages.value = []; file.value = null; active.value = 0; error.value = '' }
}
</script>

<template>
  <el-dialog :model-value="true" title="OMR 답안 입력·자동 채점" width="1200px" class="omr-dialog"
    :close-on-click-modal="false" :close-on-press-escape="false" :show-close="!busy" :before-close="close">
    <p class="guide">기존 2026 OMR 공통 양식 · 1~30번 객관식 · PDF 8MB / 30쪽 이하 · 300dpi 스캔 권장</p>
    <p class="guide">학생을 직접 연결하고 답안을 확인한 뒤 페이지별로 확정합니다. 기존 제출 답안은 덮어쓰지 않습니다. 스캔 파일은 영구 저장하지 않으며, 창을 닫으면 검토 내용이 사라집니다.</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="notice" />
    <section v-if="!scan" class="upload-panel" v-loading="busy" element-loading-text="페이지별 답안을 인식하고 있습니다…">
      <label for="omr-file">스캔 PDF 선택</label>
      <input id="omr-file" type="file" accept="application/pdf,.pdf" :disabled="busy" @change="chooseFile" />
      <el-button type="primary" :disabled="!file || busy" :loading="busy" @click="upload">답안 인식</el-button>
    </section>
    <template v-else-if="current">
      <div class="toolbar">
        <label for="omr-page">페이지</label>
        <select id="omr-page" v-model="active" :disabled="busy" @change="switchPage">
          <option v-for="(page, index) in pages" :key="page.number" :value="index">
            {{ page.number }}쪽 {{ page.saved ? '· 저장 완료' : !page.recognized ? '· 인식 실패' : '· 검토 대기' }}
          </option>
        </select>
        <span>{{ pages.length }}쪽 중 {{ savedCount }}쪽 저장</span>
        <el-button :disabled="busy" @click="reset">다른 PDF 올리기</el-button>
      </div>
      <div class="review-layout">
        <section class="preview-panel" aria-label="OMR 원본 확인">
          <div class="page-image">
            <img :src="current.preview" :alt="`${current.number}쪽 OMR 원본`" />
            <span v-if="focused" class="highlight" :style="{ left: `${focused.box.x * 100}%`, top: `${focused.box.y * 100}%`, width: `${focused.box.width * 100}%`, height: `${focused.box.height * 100}%` }" />
          </div>
          <div v-if="focused" class="zoom" :aria-label="`${focused.number}번 확대`" :style="{ backgroundImage: `url(${current.preview})`, backgroundSize: `${100 / focused.box.width}% auto`, backgroundPosition: `${focused.box.x / (1 - focused.box.width) * 100}% ${focused.box.y / (1 - focused.box.height) * 100}%`, aspectRatio: `${focused.box.width * 1600} / ${focused.box.height * 1132}` }" />
          <p class="guide">문항 번호를 누르면 해당 답란을 확대합니다. 답란은 왼쪽부터 ① ② ③ ④ ⑤입니다.</p>
        </section>
        <section class="answers-panel">
          <el-alert v-if="!current.recognized" :title="current.message" type="warning" :closable="false" show-icon />
          <template v-else>
            <label for="omr-student"><strong>이 페이지의 학생</strong></label>
            <select id="omr-student" v-model="current.studentId" :disabled="busy || current.saved" @change="invalidate">
              <option :value="null">성명란을 보고 학생 선택</option>
              <option v-for="row in roster" :key="row.studentId" :value="row.studentId" :disabled="row.submitted || pages.some((p, i) => i !== active && p.studentId === row.studentId)">
                {{ row.studentName }}{{ row.school ? ` · ${row.school}` : '' }}{{ row.grade ? ` · ${row.grade}` : '' }}{{ row.submitted ? ' (기존 답안 있음)' : '' }}
              </option>
            </select>
            <el-alert v-if="!current.saved && (duplicate || studentSubmitted)" title="중복 학생이거나 이미 제출된 학생입니다. 다른 학생을 선택해 주세요." type="warning" :closable="false" />
            <p class="guide">확인 필요 {{ unresolved }}문항 · 무응답 {{ blankCount }}문항. 시험에 등록된 문항만 표시·채점합니다.</p>
            <div class="answer-list">
              <div v-for="mark in current.marks" :key="mark.number" class="answer-row" :class="{ attention: mark.needsReview && !mark.verified }">
                <button type="button" class="question-button" :aria-label="`${mark.number}번 원본 확대`" @click="focus(mark)">{{ mark.number }}번</button>
                <input v-model="mark.answer" :aria-label="`${mark.number}번 답안`" :disabled="busy || current.saved" placeholder="무응답" maxlength="9" @input="invalidate" @focus="focus(mark)" />
                <span class="mark-status">{{ mark.status === 'UNCERTAIN' ? '흐림·지움 확인' : mark.status === 'MULTIPLE' ? (mark.multipleAnswers ? '복수정답 문항' : '복수 마킹 확인') : mark.status === 'BLANK' ? '무응답' : '' }}</span>
                <label v-if="mark.needsReview" class="verify"><input v-model="mark.verified" type="checkbox" :disabled="busy || current.saved" @change="invalidate" />확인</label>
              </div>
            </div>
            <p class="guide">답은 1~5로 입력합니다. 복수 마킹은 1,3처럼 입력하며, 단일정답 문항의 복수 마킹은 오답 처리됩니다. 무응답은 빈칸으로 둡니다.</p>
            <label v-if="!current.saved" class="final-review"><input v-model="current.reviewed" type="checkbox" :disabled="busy || unresolved > 0" /> 학생과 모든 답안을 원본과 대조했습니다. 무응답은 오답 처리합니다.</label>
            <el-alert v-if="current.saved" :title="`저장·채점 완료: ${current.score}점`" type="success" :closable="false" show-icon />
          </template>
        </section>
      </div>
    </template>
    <template #footer>
      <el-button :disabled="busy" @click="close">닫기</el-button>
      <el-button v-if="scan" type="primary" :loading="busy" :disabled="!canSave" @click="save">이 학생 저장·채점</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.guide { color: #606266; font-size: 13px; line-height: 1.6; margin: 8px 0 12px; }
.notice { margin-bottom: 12px; }
.upload-panel { display: flex; align-items: center; flex-wrap: wrap; gap: 16px; padding: 32px 8px; }
.toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin: 16px 0; }
select { min-height: 38px; border: 1px solid #c0c4cc; border-radius: 6px; padding: 6px 10px; background: white; max-width: 100%; color: #303133; }
.review-layout { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr); gap: 24px; }
.preview-panel { min-width: 0; }
.page-image { position: relative; border: 1px solid #dcdfe6; }
.page-image img { width: 100%; display: block; }
.highlight { position: absolute; border: 2px solid #409eff; background: #409eff30; pointer-events: none; }
.zoom { width: 100%; background-repeat: no-repeat; border: 2px solid #409eff; margin-top: 16px; }
.answers-panel { display: flex; flex-direction: column; gap: 8px; min-width: 0; }
.answer-list { max-height: 380px; overflow: auto; border: 1px solid #dcdfe6; border-radius: 6px; }
.answer-row { display: flex; align-items: center; gap: 8px; min-height: 44px; padding: 4px 8px; border-bottom: 1px solid #ebeef5; }
.attention { background: #fdf6ec; }
.question-button { border: 0; background: transparent; color: #2465a8; cursor: pointer; width: 46px; flex-shrink: 0; text-decoration: underline; }
.answer-row > input { width: 64px; min-height: 32px; border: 1px solid #c0c4cc; border-radius: 4px; text-align: center; }
.mark-status { color: #606266; font-size: 12px; flex: 1; }
.verify { display: flex; align-items: center; white-space: nowrap; font-size: 12px; }
.final-review { line-height: 1.7; margin-top: 8px; }
@media (max-width: 800px) { .review-layout { grid-template-columns: minmax(0, 1fr); } .answer-list { max-height: 280px; } }
</style>

<style>
.el-dialog.omr-dialog { max-width: calc(100vw - 24px); margin-top: 4vh; }
</style>
