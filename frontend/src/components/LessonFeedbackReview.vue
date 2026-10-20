<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dailyFeedbackAPI, type DailyFeedback, type LessonStudentFeedback } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ lessonId: number; refreshVersion: number; generating?: boolean }>()
const auth = useAuthStore()
const students = ref<LessonStudentFeedback[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const dialogVisible = ref(false)
const detailLoading = ref(false)
const detailFailed = ref(false)
const saving = ref(false)
const selectedIndex = ref(0)
const detail = ref<DailyFeedback | null>(null)
const draft = ref('')
const original = ref('')
let rosterVersion = 0
let detailVersion = 0

const current = computed(() => students.value[selectedIndex.value])
const dirty = computed(() => draft.value !== original.value)
const busy = computed(() => detailLoading.value || saving.value)
const writtenCount = computed(() => students.value.filter(student => student.instructorFeedback?.trim()).length)
const attendanceLabels: Record<string, string> = {
  PRESENT: '출석', ABSENT: '결석', LATE: '지각', EARLY_LEAVE: '조퇴', VIDEO: '인강',
}

async function loadStudents() {
  const version = ++rosterVersion
  loading.value = true
  loadFailed.value = false
  try {
    const response = await dailyFeedbackAPI.getLessonFeedbacks(props.lessonId)
    if (version === rosterVersion) students.value = response.data
  } catch {
    if (version === rosterVersion) loadFailed.value = true
  } finally {
    if (version === rosterVersion) loading.value = false
  }
}

watch(() => [props.lessonId, props.refreshVersion], loadStudents, { immediate: true })

async function loadDetail() {
  if (!current.value) return
  const version = ++detailVersion
  const studentId = current.value.studentId
  detailLoading.value = true
  detailFailed.value = false
  detail.value = null
  draft.value = ''
  original.value = ''
  try {
    const response = await dailyFeedbackAPI.getDailyFeedback(studentId, props.lessonId)
    if (version !== detailVersion) return
    detail.value = response.data
    draft.value = response.data.instructorFeedback || ''
    original.value = draft.value
  } catch {
    if (version === detailVersion) detailFailed.value = true
  } finally {
    if (version === detailVersion) detailLoading.value = false
  }
}

async function confirmDiscard() {
  if (!dirty.value) return true
  try {
    await ElMessageBox.confirm('저장하지 않은 수정 내용을 버리시겠습니까?', '수정 내용 확인', {
      confirmButtonText: '버리기', cancelButtonText: '계속 수정', type: 'warning',
    })
    return true
  } catch {
    return false
  }
}

async function openStudent(index: number) {
  if (saving.value || props.generating || !(await confirmDiscard())) return
  selectedIndex.value = index
  dialogVisible.value = true
  await loadDetail()
}

async function moveStudent(offset: number) {
  const index = selectedIndex.value + offset
  if (busy.value || index < 0 || index >= students.value.length) return
  await openStudent(index)
}

async function closeDialog(done?: () => void) {
  if (saving.value || !(await confirmDiscard())) return
  ++detailVersion
  draft.value = ''
  original.value = ''
  dialogVisible.value = false
  done?.()
}

async function save(next = false) {
  if (!current.value || busy.value || detailFailed.value || props.generating) return
  if (!draft.value.trim()) {
    ElMessage.warning('피드백 내용을 입력해주세요.')
    return
  }
  saving.value = true
  try {
    await dailyFeedbackAPI.updateInstructorFeedback(
      current.value.studentId, props.lessonId, draft.value, auth.name, false,
    )
    current.value.instructorFeedback = draft.value
    current.value.feedbackAuthor = auth.name
    original.value = draft.value
    ElMessage.success('피드백이 저장되었습니다.')
  } catch {
    ElMessage.error('피드백 저장에 실패했습니다. 수정 내용은 유지됩니다.')
    return
  } finally {
    saving.value = false
  }
  if (next) await moveStudent(1)
}
</script>

<template>
  <div class="feedback-review" v-loading="loading">
    <div class="review-heading">
      <div>
        <h4>학생별 확인·수정</h4>
        <p>학생의 학습 결과와 피드백을 확인하고, 저장 후 다음 학생으로 이동하세요.</p>
      </div>
      <el-tag>작성 {{ writtenCount }} / {{ students.length }}명</el-tag>
    </div>
    <el-alert v-if="loadFailed" title="학생 피드백을 불러오지 못했습니다." type="error" :closable="false">
      <el-button size="small" @click="loadStudents">다시 불러오기</el-button>
    </el-alert>
    <el-table v-else :data="students" stripe>
      <el-table-column prop="studentName" label="학생" width="110" />
      <el-table-column label="출결" width="85">
        <template #default="{ row }">{{ attendanceLabels[row.attendanceStatus] || '미확인' }}</template>
      </el-table-column>
      <el-table-column label="피드백" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ row.instructorFeedback || '미작성' }}</template>
      </el-table-column>
      <el-table-column prop="feedbackAuthor" label="작성자" width="100" />
      <el-table-column label="확인" width="110" fixed="right">
        <template #default="{ $index }">
          <el-button data-test="review-student" size="small" type="primary" plain :disabled="generating" @click="openStudent($index)">확인·수정</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="`${current?.studentName || ''} 학생 피드백 (${selectedIndex + 1}/${students.length})`"
      width="min(900px, 95vw)"
      top="5vh"
      :before-close="closeDialog"
      :close-on-click-modal="false"
    >
      <div v-loading="detailLoading" class="review-content">
        <el-alert v-if="detailFailed" title="학습 결과를 불러오지 못했습니다." type="error" :closable="false">
          <el-button size="small" @click="loadDetail">다시 불러오기</el-button>
        </el-alert>
        <template v-else-if="detail">
          <div class="learning-results">
            <section>
              <h4>숙제 결과</h4>
              <template v-if="detail.todayHomework">
                <p>{{ detail.todayHomework.homeworkTitle }}</p>
                <p>완성도 {{ detail.todayHomework.completion == null ? '미제출' : `${detail.todayHomework.completion}%` }}</p>
                <p>오답: {{ detail.todayHomework.incorrectCount == null ? '미입력' : `${detail.todayHomework.incorrectCount}개` }}<span v-if="detail.todayHomework.incorrectQuestions"> · {{ detail.todayHomework.incorrectQuestions }}번</span></p>
                <p>미풀이: {{ detail.todayHomework.unsolvedCount == null ? '미입력' : `${detail.todayHomework.unsolvedCount}개` }}<span v-if="detail.todayHomework.unsolvedQuestions"> · {{ detail.todayHomework.unsolvedQuestions }}번</span></p>
                <p>질문: {{ detail.todayHomework.questionedQuestions || '없음' }}</p>
              </template>
              <p v-else>배정된 숙제가 없습니다.</p>
              <p v-if="detail.nextHomework">다음 숙제: {{ detail.nextHomework.homeworkTitle }}</p>
            </section>
            <section>
              <h4>시험 결과</h4>
              <template v-if="detail.todayTest">
                <p>{{ detail.todayTest.testTitle }}</p>
                <p>{{ detail.todayTest.studentScore }}점 · 평균 {{ detail.todayTest.classAverage.toFixed(1) }}점 · {{ detail.todayTest.rank }}등</p>
                <p>오답: {{ detail.todayTest.incorrectQuestions.join(', ') || '없음' }}</p>
                <ul v-if="detail.todayTest.incorrectQuestions.length">
                  <li v-for="number in detail.todayTest.incorrectQuestions" :key="number">
                    {{ number }}번
                    {{ detail.todayTest.questionAccuracyRates.find(rate => rate.questionNumber === number)?.topic || '' }}
                    <span v-if="detail.todayTest.questionAccuracyRates.some(rate => rate.questionNumber === number)">
                      (정답률 {{ detail.todayTest.questionAccuracyRates.find(rate => rate.questionNumber === number)?.correctRate }}%)
                    </span>
                  </li>
                </ul>
              </template>
              <p v-else>제출된 시험 결과가 없습니다.</p>
            </section>
          </div>
          <el-table v-if="detail.todayTest?.essayDetails?.length" :data="detail.todayTest.essayDetails" size="small">
            <el-table-column prop="questionNumber" label="서술형" width="75" />
            <el-table-column prop="studentAnswer" label="학생 답안" min-width="160" />
            <el-table-column label="점수" width="100">
              <template #default="{ row }">{{ row.earnedPoints ?? '미채점' }} / {{ row.maxPoints }}</template>
            </el-table-column>
            <el-table-column prop="teacherComment" label="코멘트" min-width="160" />
          </el-table>
          <el-form label-position="top" class="feedback-form">
            <el-form-item label="개별 피드백">
              <el-input data-test="feedback-draft" v-model="draft" type="textarea" :rows="9" :disabled="saving || generating" placeholder="학생에게 전달할 피드백을 입력하세요" />
            </el-form-item>
          </el-form>
          <p class="save-hint">저장 시 작성자: {{ auth.name }} <span v-if="dirty">· 저장하지 않은 수정 내용이 있습니다.</span></p>
        </template>
      </div>
      <template #footer>
        <div class="review-actions">
          <div>
            <el-button :disabled="busy || selectedIndex === 0" @click="moveStudent(-1)">이전 학생</el-button>
            <el-button :disabled="busy || selectedIndex === students.length - 1" @click="moveStudent(1)">다음 학생</el-button>
          </div>
          <div>
            <el-button :disabled="saving" @click="closeDialog()">닫기</el-button>
            <el-button type="primary" :loading="saving" :disabled="busy || detailFailed || !detail || generating" @click="save()">저장</el-button>
            <el-button data-test="save-next-feedback" v-if="selectedIndex < students.length - 1" type="success" :disabled="busy || detailFailed || !detail || generating" @click="save(true)">저장 후 다음</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.feedback-review { margin-top: 20px; border-top: 1px solid var(--el-border-color-light); padding-top: 20px; }
.review-heading, .review-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
h4 { margin: 0 0 8px; }
.review-heading p, .save-hint { color: var(--el-text-color-secondary); font-size: 13px; }
.review-heading p { margin: 0 0 16px; }
.review-content { min-height: 180px; max-height: calc(90dvh - 180px); overflow-y: auto; }
.learning-results { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.learning-results section { padding: 16px; border-radius: 8px; background: var(--el-fill-color-light); overflow-wrap: anywhere; }
.learning-results p, .learning-results li { font-size: 14px; margin: 8px 0 0; }
.learning-results ul { padding-left: 20px; }
.feedback-form { margin-top: 20px; }
@media (max-width: 600px) {
  .learning-results { grid-template-columns: 1fr; }
  .review-actions { justify-content: center; }
}
</style>
