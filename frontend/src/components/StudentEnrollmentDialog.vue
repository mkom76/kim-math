<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { studentAPI, type AcademyClass, type Student, type StudentEnrollment } from '@/api/client'

const props = defineProps<{
  visible: boolean
  student: Student | null
  classes: AcademyClass[]
  readOnly?: boolean
}>()
const emit = defineEmits<{
  'update:visible': [visible: boolean]
  updated: []
}>()

const enrollments = ref<StudentEnrollment[]>([])
const loading = ref(false)
const saving = ref(false)
const loadFailed = ref(false)
const targetClassId = ref<number>()
const transferSource = ref<StudentEnrollment | null>(null)
let loadVersion = 0

const statusLabels: Record<StudentEnrollment['status'], string> = {
  SCHEDULED: '수강 예정',
  ACTIVE: '수강 중',
  COMPLETED: '수강 종료',
  WITHDRAWN: '수강 철회',
}
const currentEnrollments = computed(() => enrollments.value.filter(isCurrent))
const pastEnrollments = computed(() => enrollments.value.filter(enrollment => !isCurrent(enrollment)))
const targetClasses = computed(() => props.classes.filter(academyClass =>
  academyClass.id != null &&
  academyClass.academyId === props.student?.academyId &&
  !academyClass.ended && !academyClass.endedAt &&
  !currentEnrollments.value.some(enrollment => enrollment.classId === academyClass.id),
))
const busy = computed(() => loading.value || saving.value)

function isCurrent(enrollment: StudentEnrollment) {
  return enrollment.status === 'ACTIVE' || enrollment.status === 'SCHEDULED'
}

async function loadEnrollments() {
  const version = ++loadVersion
  if (!props.visible || props.student?.id == null) return
  loading.value = true
  loadFailed.value = false
  enrollments.value = []
  targetClassId.value = undefined
  transferSource.value = null
  try {
    const response = await studentAPI.getEnrollments(props.student.id)
    if (version === loadVersion) enrollments.value = response.data
  } catch {
    if (version === loadVersion) loadFailed.value = true
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

watch(() => [props.visible, props.student?.id], loadEnrollments, { immediate: true })

function close() {
  if (!saving.value) emit('update:visible', false)
}

function startTransfer(enrollment: StudentEnrollment) {
  if (busy.value || props.readOnly || !enrollment.canManage) return
  transferSource.value = enrollment
  targetClassId.value = undefined
}

function cancelTransfer() {
  transferSource.value = null
  targetClassId.value = undefined
}

async function submit() {
  const studentId = props.student?.id
  const target = targetClasses.value.find(academyClass => academyClass.id === targetClassId.value)
  if (studentId == null || target?.id == null || busy.value || loadFailed.value || props.readOnly) return
  const source = transferSource.value
  saving.value = true
  try {
    if (source) {
      await ElMessageBox.confirm(
        `${source.className} 수강을 종료하고 ${target.name} 수강을 시작합니다. 기존 학습 기록과 다른 반의 소속은 유지됩니다.`,
        '반 이동',
        { confirmButtonText: '이동', cancelButtonText: '취소', type: 'warning' },
      )
    }
    const response = source
      ? await studentAPI.transferEnrollment(studentId, source.classId, target.id)
      : await studentAPI.addEnrollment(studentId, target.id)
    enrollments.value = response.data
    cancelTransfer()
    ElMessage.success(source ? '반을 이동했습니다.' : '반을 추가했습니다.')
    emit('updated')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || '반 배정을 변경하지 못했습니다.')
    }
  } finally {
    saving.value = false
  }
}

async function complete(enrollment: StudentEnrollment) {
  const studentId = props.student?.id
  if (studentId == null || busy.value || loadFailed.value || props.readOnly || !enrollment.canManage) return
  saving.value = true
  try {
    await ElMessageBox.confirm(
      `${enrollment.className} 수강을 종료하시겠습니까? 이 학생의 수강만 종료되며, 기존 학습 기록과 다른 반의 소속은 유지됩니다.`,
      '수강 종료',
      { confirmButtonText: '수강 종료', cancelButtonText: '취소', type: 'warning' },
    )
    const response = await studentAPI.completeEnrollment(studentId, enrollment.classId)
    enrollments.value = response.data
    cancelTransfer()
    ElMessage.success('수강을 종료했습니다.')
    emit('updated')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || '수강을 종료하지 못했습니다.')
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="`${student?.name ?? ''} · ${readOnly ? '소속 반' : '반 관리'}`"
    width="min(540px, calc(100vw - 32px))"
    class="student-enrollment-dialog"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    @update:model-value="close"
  >
    <div v-loading="loading" class="enrollment-content">
      <div v-if="loadFailed" class="enrollment-error" role="alert">
        <span>소속 반을 불러오지 못했습니다.</span>
        <el-button size="small" @click="loadEnrollments">다시 시도</el-button>
      </div>
      <template v-else-if="!loading">
        <div v-if="currentEnrollments.length" class="enrollment-list">
          <div v-for="enrollment in currentEnrollments" :key="enrollment.classId" class="enrollment-row">
            <div class="enrollment-label">
              <span>{{ enrollment.className }}</span>
              <el-tag size="small" :type="enrollment.status === 'ACTIVE' ? 'success' : 'info'">
                {{ statusLabels[enrollment.status] }}
              </el-tag>
            </div>
            <div v-if="!readOnly && enrollment.canManage" class="enrollment-actions">
              <el-button
                link type="primary" size="small" :disabled="busy || !targetClasses.length"
                :data-test="`transfer-${enrollment.classId}`" @click="startTransfer(enrollment)"
              >반 이동</el-button>
              <el-button
                link type="danger" size="small" :disabled="busy"
                :data-test="`complete-${enrollment.classId}`" @click="complete(enrollment)"
              >수강 종료</el-button>
            </div>
          </div>
        </div>
        <p v-else class="enrollment-note">현재 수강 중인 반이 없습니다.</p>

        <div v-if="!readOnly" class="enrollment-assignment">
          <label for="enrollment-target" class="assignment-label">
            {{ transferSource ? `${transferSource.className}에서 이동` : '반 추가' }}
          </label>
          <p class="enrollment-note">
            {{ transferSource ? '선택한 반으로 옮기고, 현재 반의 수강은 종료합니다.' : '기존 소속을 유지하면서 다른 반을 함께 수강합니다.' }}
          </p>
          <div class="assignment-controls">
            <el-select
              id="enrollment-target" v-model="targetClassId" filterable
              :placeholder="targetClasses.length ? '배정할 반 선택' : '추가 가능한 반이 없습니다'"
              :disabled="busy || !targetClasses.length" class="target-select" data-test="target-class"
            >
              <el-option v-for="academyClass in targetClasses" :key="academyClass.id" :label="academyClass.name" :value="academyClass.id!" />
            </el-select>
            <el-button
              type="primary" :loading="saving" :disabled="busy || targetClassId == null"
              data-test="submit-enrollment" @click="submit"
            >{{ transferSource ? '이동' : '추가' }}</el-button>
            <el-button v-if="transferSource" :disabled="busy" @click="cancelTransfer">취소</el-button>
          </div>
        </div>

        <details v-if="pastEnrollments.length" class="enrollment-history">
          <summary>지난 수강 {{ pastEnrollments.length }}개</summary>
          <div v-for="enrollment in pastEnrollments" :key="enrollment.classId" class="enrollment-row">
            <div class="enrollment-label">
              <span>{{ enrollment.className }}</span>
              <el-tag type="info" size="small">{{ statusLabels[enrollment.status] }}</el-tag>
            </div>
            <span v-if="enrollment.endedAt" class="enrollment-note">{{ enrollment.endedAt.slice(0, 10) }}</span>
          </div>
        </details>
      </template>
    </div>
    <template #footer>
      <el-button :disabled="saving" @click="close">닫기</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.enrollment-content { min-height: 96px; }
.enrollment-row, .enrollment-label, .enrollment-actions, .assignment-controls, .enrollment-error {
  display: flex;
  align-items: center;
  gap: 8px;
}
.enrollment-row { justify-content: space-between; padding: 10px 0; }
.enrollment-label { flex-wrap: wrap; }
.enrollment-actions { flex-shrink: 0; }
.enrollment-actions .el-button + .el-button { margin-left: 0; }
.enrollment-assignment { margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--el-border-color-lighter); }
.assignment-label { font-weight: 600; color: var(--el-text-color-primary); }
.enrollment-note { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.target-select { flex: 1; min-width: 0; }
.enrollment-history { margin-top: 20px; }
.enrollment-history summary { cursor: pointer; color: var(--el-text-color-secondary); font-size: 13px; }
@media (max-width: 600px) {
  .enrollment-row { align-items: flex-start; }
  .enrollment-label { align-items: flex-start; flex-direction: column; }
}
</style>
