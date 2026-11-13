<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { examPdfAPI, type PdfConfig, type PdfImport, type PdfQuestion } from '@/api/examPdf'
import type { AcademyClass } from '@/api/client'
import { homeworkTypeCatalogVersion, homeworkTypeOptions } from '@/utils/homeworkQuestionTypes'
import HomeworkQuestionTypePicker from './HomeworkQuestionTypePicker.vue'

const props = defineProps<{ visible: boolean; classes: AcademyClass[]; academyId: number | null; importId?: string | null }>()
const emit = defineEmits<{ (event: 'update:visible', value: boolean): void; (event: 'saved'): void }>()
type DraftQuestion = PdfQuestion & { typeId: string | null }
const config = ref<PdfConfig | null>(null)
const job = ref<PdfImport | null>(null)
const file = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const classId = ref<number>()
const subjectId = ref('ALG')
const title = ref('')
const consent = ref(false)
const solutionRange = ref('')
const pageRange = ref('')
const contentsReviewed = ref(false)
const draft = ref<DraftQuestion[]>([])
const busy = ref(false)
const loading = ref(false)
const error = ref('')
const pdfUrl = ref('')
const activePage = ref(1)
const requestKey = ref('')
let generation = 0
let pollTimer: ReturnType<typeof setTimeout> | undefined
let polling = false
const readOnly = computed(() => job.value?.state === 'REGISTERED')
const canReview = computed(() => job.value?.state === 'REVIEW' || readOnly.value)
const activeClasses = computed(() => props.classes.filter(item => item.academyId === props.academyId && !item.ended && !item.endedAt))
const unknownCount = computed(() => draft.value.filter(question => !question.typeId).length)
const mismatch = computed(() => Boolean(config.value && config.value.catalogVersion !== homeworkTypeCatalogVersion)
  || Boolean(job.value && job.value.catalogVersion !== homeworkTypeCatalogVersion))

function message(cause: unknown, fallback: string): string {
  const response = (cause as { response?: { data?: { message?: string; detail?: string } } })?.response?.data
  return response?.message || response?.detail || fallback
}
function reset() {
  generation++
  polling = false
  clearTimeout(pollTimer)
  if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value)
  pdfUrl.value = ''; file.value = null; job.value = null; draft.value = []; error.value = ''
  busy.value = false; loading.value = false; consent.value = false; contentsReviewed.value = false
  title.value = ''; classId.value = undefined; activePage.value = 1
  requestKey.value = crypto.randomUUID()
  solutionRange.value = ''; pageRange.value = ''
}
function applyJob(result: PdfImport) {
  job.value = result
  if (result.state !== 'PROCESSING') draft.value = result.questions.map(question => ({ ...question, typeId: result.state === 'REGISTERED' ? question.confirmedTypeId : question.suggestedTypeId }))
}
async function loadPdf(id: string, token: number) {
  const response = await examPdfAPI.document(id)
  if (token !== generation || !props.visible) return
  if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value)
  pdfUrl.value = URL.createObjectURL(response.data)
}
async function poll(id: string, token: number) {
  if (polling) return
  polling = true
  clearTimeout(pollTimer)
  try {
    const response = await examPdfAPI.get(id)
    if (token !== generation || !props.visible) return
    if (job.value?.id === id && job.value.state !== 'PROCESSING') return
    applyJob(response.data)
    error.value = ''
    if (response.data.state === 'REVIEW') await loadPdf(id, token)
    if (response.data.state === 'PROCESSING') pollTimer = setTimeout(() => { void poll(id, token) }, 1500)
  } catch (cause) {
    if (token === generation) error.value = message(cause, '분석 상태를 불러오지 못했습니다. 상태 확인을 다시 눌러 주세요.')
  } finally { if (token === generation) polling = false }
}
async function open() {
  reset(); loading.value = true
  const token = generation
  try {
    const response = await examPdfAPI.config()
    if (token !== generation) return
    config.value = response.data
    if (props.importId) {
      const result = await examPdfAPI.get(props.importId)
      if (token !== generation) return
      applyJob(result.data)
      if (canReview.value) await loadPdf(result.data.id, token)
    }
  } catch (cause) { if (token === generation) error.value = message(cause, 'PDF 등록 설정을 불러오지 못했습니다.') }
  finally { if (token === generation) loading.value = false }
}
function chooseFile(event: Event) {
  const selected = (event.target as HTMLInputElement).files?.[0]
  if (!selected) return
  if (!selected.name.toLowerCase().endsWith('.pdf') || selected.size > (config.value?.maxBytes ?? 8 * 1024 * 1024)) {
    file.value = null
    error.value = '8MB 이하 PDF 파일을 선택해 주세요.'
    ;(event.target as HTMLInputElement).value = ''
    return
  }
  if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value)
  file.value = selected; title.value = selected.name.replace(/\.pdf$/i, '')
  pdfUrl.value = URL.createObjectURL(selected); error.value = ''; requestKey.value = crypto.randomUUID()
}
async function analyze() {
  if (!file.value || !classId.value || !consent.value || mismatch.value || busy.value) return
  busy.value = true; error.value = ''
  const token = generation
  try {
    if (!pageRange.value.trim() || !solutionRange.value.trim()) { error.value = '등록할 페이지 범위를 입력해 주세요.'; return }
    const response = await examPdfAPI.upload(file.value, classId.value, subjectId.value, requestKey.value, consent.value, pageRange.value, solutionRange.value)
    if (token !== generation) return
    applyJob(response.data)
    if (response.data.state === 'PROCESSING') void poll(response.data.id, token)
    else if (response.data.state === 'REVIEW') await loadPdf(response.data.id, token)
  } catch (cause) { if (token === generation) error.value = message(cause, 'PDF 분석을 시작하지 못했습니다.') }
  finally { if (token === generation) busy.value = false }
}
function addQuestion() {
  if (draft.value.length >= (config.value?.maxQuestions ?? 60)) return
  draft.value.push({ id: `new-${crypto.randomUUID()}`, position: draft.value.length + 1, sourceNumber: '', text: '', regions: [{ page: Math.min(activePage.value, job.value?.questionPageCount ?? 1), x: 0, y: 0, width: 1, height: 1 }], suggestedTypeId: null, confirmedTypeId: null, typeId: null, typePath: [], answer: null, points: null, format: null, multipleAnswers: false, reason: '직접 추가한 문항', needsReview: true })
  contentsReviewed.value = false
}
async function register() {
  if (!job.value || busy.value || readOnly.value || !contentsReviewed.value || mismatch.value) return
  if (!title.value.trim() || !draft.value.length || draft.value.some(question => !question.sourceNumber.trim() || !question.text.trim())) {
    error.value = '시험명과 각 문항의 번호·내용을 입력해 주세요.'; return
  }
  if (draft.value.some(q => !q.format || q.points == null || !Number.isFinite(q.points) || q.points <= 0 || (q.format !== 'ESSAY' && !q.answer?.trim()))) { error.value = '정답·문항 형식·배점을 확인해 주세요. 해설에서 찾지 못한 값은 직접 입력해 주세요.'; return }
  if (new Set(draft.value.map(question => question.sourceNumber.trim())).size !== draft.value.length) { error.value = '문항 번호가 중복되어 있습니다.'; return }
  busy.value = true; error.value = ''
  const token = generation
  try {
    const response = await examPdfAPI.register(job.value.id, {
      expectedVersion: job.value.version, catalogVersion: job.value.catalogVersion, title: title.value.trim(), contentsReviewed: contentsReviewed.value,
      questions: draft.value.map(question => ({ id: question.id, sourceNumber: question.sourceNumber.trim(), text: question.text, regions: question.regions, typeId: question.typeId, answer: question.answer, points: question.points, format: question.format, multipleAnswers: question.multipleAnswers })),
    })
    if (token !== generation) return
    applyJob(response.data); ElMessage.success('PDF 시험이 등록되었습니다.'); emit('saved')
  } catch (cause) { if (token === generation) error.value = message(cause, '시험을 등록하지 못했습니다.') }
  finally { if (token === generation) busy.value = false }
}
async function close() {
  if (busy.value) return
  if (job.value && ['PROCESSING', 'REVIEW'].includes(job.value.state)) {
    try {
      await ElMessageBox.confirm('등록하지 않은 분석 초안을 취소하고 닫을까요?', 'PDF 시험 등록', { confirmButtonText: '취소하고 닫기', cancelButtonText: '계속 작성' })
      await examPdfAPI.cancel(job.value.id)
    } catch { return }
  }
  emit('update:visible', false)
}
watch(() => [props.visible, props.importId, props.academyId] as const, () => {
  if (props.visible) void open()
  else reset()
}, { immediate: true })
watch(draft, () => { contentsReviewed.value = false }, { deep: true })
watch([solutionRange, pageRange, classId, subjectId], () => { requestKey.value = crypto.randomUUID() })
onBeforeUnmount(() => { generation++; clearTimeout(pollTimer); if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value) })
</script>

<template>
  <el-dialog :model-value="visible" :title="readOnly ? 'PDF 시험의 문항과 유형' : 'PDF로 시험 등록'" width="min(1200px, 96vw)" top="3vh" :before-close="close" :close-on-click-modal="false">
    <div v-loading="loading" class="pdf-import">
      <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
      <el-alert v-if="mismatch" title="유형트리가 변경되었습니다. 화면을 새로고침한 뒤 다시 분석해 주세요." type="warning" :closable="false" />
      <el-alert v-if="config?.mock" title="검증용 가상 분석입니다. 선택한 PDF를 실제로 판독한 결과가 아닙니다." type="warning" :closable="false" />
      <template v-if="!job">
        <el-alert v-if="config && !config.ready" title="PDF 시험 등록을 준비 중입니다. 연결 설정이 완료되면 사용할 수 있습니다." type="info" :closable="false" />
        <el-form label-position="top" @submit.prevent>
          <div class="pdf-fields">
            <el-form-item label="등록할 반" required><el-select v-model="classId" placeholder="반 선택"><el-option v-for="item in activeClasses" :key="item.id" :value="item.id!" :label="item.name" /></el-select></el-form-item>
            <el-form-item label="과목" required><el-select v-model="subjectId"><el-option v-for="subject in homeworkTypeOptions" :key="subject.value" :value="subject.value" :label="subject.label" /></el-select></el-form-item>
          </div>
          <el-form-item label="시험 PDF" required>
            <input ref="fileInput" type="file" accept="application/pdf,.pdf" aria-label="시험 PDF 선택" :disabled="busy" @change="chooseFile" />
            <p class="pdf-hint">8MB·20쪽·60문항 이내. 스캔본과 수식·도형이 포함된 PDF도 분석합니다.</p>
          </el-form-item>
          <p class="pdf-notice">{{ config?.notice }}</p>
          <el-form-item label="문제 페이지" required><el-input v-model="pageRange" placeholder="예: 1-3,5" aria-label="문제 페이지 범위" :disabled="busy" maxlength="200" /></el-form-item>
          <el-form-item label="해설·정답 페이지" required><el-input v-model="solutionRange" placeholder="예: 6-8" aria-label="해설 페이지 범위" :disabled="busy" maxlength="200" /></el-form-item>
          <p class="pdf-hint">PDF의 실제 페이지 번호를 입력해 주세요. 두 범위는 겹칠 수 없으며 합계 20쪽 이내입니다. 선택한 문제·해설만 분석·보관하고 PDF는 교사만 열람합니다. 해설에 명시되지 않은 정답·배점은 미입력으로 남깁니다. 주석·입력 양식에 적은 답은 PDF 본문으로 저장한 파일을 올려 주세요.</p>
          <el-checkbox v-model="consent" :disabled="busy">PDF 분석 전송 안내를 확인했습니다.</el-checkbox>
          <div class="pdf-actions"><el-button type="primary" :loading="busy" :disabled="!config?.ready || !file || !classId || !consent || mismatch" @click="analyze">문항 분석 및 유형 자동 분류</el-button></div>
        </el-form>
      </template>
      <template v-else>
        <el-alert :title="job.message" :type="job.state === 'FAILED' ? 'error' : job.reviewRequired ? 'warning' : 'info'" :closable="false" />
        <div v-if="job.state === 'PROCESSING'" class="pdf-processing"><el-progress :percentage="50" :indeterminate="true" :show-text="false" /><p>문항 번호와 내용을 읽고 유형트리와 대조하고 있습니다.</p><el-button @click="poll(job!.id, generation)">상태 다시 확인</el-button></div>
        <template v-if="canReview">
          <p class="pdf-hint">아래 PDF는 선택한 문제 페이지를 먼저, 해설 페이지를 뒤에 모은 분석본입니다. 페이지 번호는 이 분석본의 1쪽부터 표시합니다. 객관식 정답은 1~5, 복수 정답은 1,3처럼 입력해 주세요.</p>
          <div v-if="!readOnly" class="pdf-fields">
            <el-form-item label="시험명" required><el-input v-model="title" maxlength="255" /></el-form-item>
          </div>
          <div v-if="pdfUrl" class="pdf-source"><div><strong>PDF 원본</strong> <span>{{ job.fileName }}</span></div><a :href="`${pdfUrl}#page=${activePage}`" target="_blank" rel="noopener noreferrer">원본을 새 창에서 보기</a><iframe :src="`${pdfUrl}#page=${activePage}`" title="시험 PDF 원본" /></div>
          <div class="pdf-summary"><strong>{{ draft.length }}문항</strong><span v-if="unknownCount">미분류 {{ unknownCount }}문항</span><span v-if="readOnly">시험 검사 번호는 아래 순서로 고정됩니다.</span><el-button v-else size="small" :disabled="draft.length >= (config?.maxQuestions ?? 60)" @click="addQuestion">누락 문항 추가</el-button></div>
          <article v-for="(question, index) in draft" :key="question.id" class="pdf-question">
            <div class="pdf-question-heading"><strong>검사 {{ index + 1 }}번</strong><el-tag v-if="question.needsReview && !readOnly" type="warning">확인 필요</el-tag><el-button v-if="!readOnly" text type="danger" @click="draft.splice(index, 1)">문항 제외</el-button></div>
            <div class="pdf-fields">
              <el-form-item label="PDF 문항 번호"><el-input v-model="question.sourceNumber" :disabled="readOnly" maxlength="40" /></el-form-item>
              <el-form-item label="PDF 시작 페이지"><el-input-number v-model="question.regions[0]!.page" :min="1" :max="job.questionPageCount" :disabled="readOnly" @change="activePage = question.regions[0]!.page" /></el-form-item>
            </div>
            <el-form-item label="문항 유형"><HomeworkQuestionTypePicker v-model="question.typeId" :subject-id="job.subjectId" :disabled="readOnly" /></el-form-item>
            <div class="pdf-fields">
              <el-form-item label="인식한 정답"><el-input v-model="question.answer" :disabled="readOnly" maxlength="1000" placeholder="해설에 없으면 직접 입력" /></el-form-item>
              <el-form-item label="배점"><el-input-number :model-value="question.points ?? undefined" @update:model-value="question.points = $event ?? null" :min="0.1" :max="1000" :disabled="readOnly" /></el-form-item>
              <el-form-item label="문항 형식"><el-select v-model="question.format" :disabled="readOnly" placeholder="확인 필요"><el-option value="OBJECTIVE" label="객관식" /><el-option value="SUBJECTIVE" label="주관식" /><el-option value="ESSAY" label="서술형" /></el-select></el-form-item>
              <el-checkbox v-if="question.format === 'OBJECTIVE'" v-model="question.multipleAnswers" :disabled="readOnly">복수 정답</el-checkbox>
            </div>
            <p v-if="question.reason" class="pdf-hint">분류 근거: {{ question.reason }}</p>
            <el-form-item label="인식한 문항 내용"><el-input v-model="question.text" type="textarea" :rows="3" maxlength="6000" :disabled="readOnly" /></el-form-item>
          </article>
          <template v-if="!readOnly">
            <el-checkbox v-model="contentsReviewed">원본과 문항 수·번호·내용·유형·정답·배점을 확인했습니다.</el-checkbox>
            <p v-if="unknownCount" class="pdf-hint">미분류 문항은 유형 없이 등록됩니다.</p>
          </template>
        </template>
      </template>
    </div>
    <template #footer><el-button :disabled="busy" @click="close">닫기</el-button><el-button v-if="job?.state === 'REVIEW'" type="primary" :loading="busy" :disabled="!contentsReviewed || !title.trim() || !draft.length || mismatch" @click="register">{{ draft.length }}문항 시험 등록</el-button></template>
  </el-dialog>
</template>

<style scoped>
.pdf-import :deep(.el-form-item__content) > .pdf-hint{width:100%}.pdf-import input[type=file]{max-width:100%}
.pdf-import{display:grid;gap:16px}.pdf-fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.pdf-fields .el-select,.pdf-fields .el-date-editor{width:100%}.pdf-hint,.pdf-notice{color:var(--el-text-color-secondary);font-size:13px;line-height:1.6;margin:8px 0}.pdf-actions{margin-top:16px}.pdf-processing{display:grid;gap:12px}.pdf-source{display:grid;gap:8px}.pdf-source span{margin-left:8px;overflow-wrap:anywhere}.pdf-source iframe{width:100%;height:400px;border:1px solid var(--el-border-color);border-radius:6px}.pdf-summary,.pdf-question-heading{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.pdf-question{padding:16px 0;border-top:1px solid var(--el-border-color-light)}.pdf-question-heading{margin-bottom:12px}.pdf-question .homework-type-picker{width:100%}@media(max-width:600px){.pdf-fields{grid-template-columns:1fr;gap:4px}.pdf-source iframe{height:260px}.pdf-actions .el-button{width:100%;white-space:normal;height:auto;min-height:40px}.pdf-import :deep(.el-checkbox){white-space:normal;height:auto;align-items:flex-start;line-height:1.6}.pdf-import :deep(.el-checkbox__label){white-space:normal}}
</style>
