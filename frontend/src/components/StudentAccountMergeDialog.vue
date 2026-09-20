<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  studentAccountMergeAPI,
  type StudentAccountMergeCandidate,
  type StudentAccountMergePreview,
} from '@/api/client'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  merged: []
}>()

const loading = ref(false)
const merging = ref(false)
const candidates = ref<StudentAccountMergeCandidate[]>([])
const selectedGroup = ref<StudentAccountMergeCandidate | null>(null)
const targetId = ref<number | null>(null)
const sourceIds = ref<number[]>([])
const preview = ref<StudentAccountMergePreview | null>(null)
const confirmation = ref('')

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})
const expectedConfirmation = computed(() =>
  preview.value ? `대표 ID ${preview.value.target.id}` : ''
)
const canPreview = computed(() => targetId.value != null && sourceIds.value.length > 0)
const canMerge = computed(() =>
  preview.value?.mergeable && confirmation.value === expectedConfirmation.value
)
const visibleImpacts = computed(() => preview.value?.impacts.filter(impact => impact.count > 0) ?? [])

watch(targetId, (value) => {
  if (value != null) sourceIds.value = sourceIds.value.filter(id => id !== value)
})

const resetSelection = () => {
  selectedGroup.value = null
  targetId.value = null
  sourceIds.value = []
  preview.value = null
  confirmation.value = ''
}

const loadCandidates = async () => {
  loading.value = true
  resetSelection()
  try {
    const response = await studentAccountMergeAPI.getCandidates()
    candidates.value = response.data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '중복 계정 후보를 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

watch(() => props.visible, (visible) => {
  if (visible) loadCandidates()
  else resetSelection()
}, { immediate: true })

const reviewGroup = (group: StudentAccountMergeCandidate) => {
  selectedGroup.value = group
  targetId.value = null
  sourceIds.value = []
  preview.value = null
  confirmation.value = ''
}

const createPreview = async () => {
  if (targetId.value == null || sourceIds.value.length === 0) return
  loading.value = true
  try {
    const response = await studentAccountMergeAPI.preview({
      targetStudentId: targetId.value,
      sourceStudentIds: sourceIds.value,
    })
    preview.value = response.data
    confirmation.value = ''
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '통합 영향을 확인하지 못했습니다.')
  } finally {
    loading.value = false
  }
}

const executeMerge = async () => {
  if (!preview.value || !canMerge.value) return
  merging.value = true
  try {
    await studentAccountMergeAPI.merge({
      targetStudentId: preview.value.target.id,
      sourceStudentIds: preview.value.sources.map(source => source.id),
    })
    ElMessage.success(`학생 계정을 대표 ID ${preview.value.target.id}로 통합했습니다.`)
    emit('merged')
    await loadCandidates()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '학생 계정을 통합하지 못했습니다.')
  } finally {
    merging.value = false
  }
}

const statusLabel = (status: string) => ({
  ACTIVE: '이용 중',
  PENDING_CONSENT: '동의 대기',
  REVOKED: '철회',
  SCHEDULED: '예정',
  COMPLETED: '종료',
  WITHDRAWN: '중도 종료',
}[status] ?? status)

const enrollmentLabel = (student: StudentAccountMergeCandidate['students'][number]) =>
  student.enrollments
    .map(enrollment => `${enrollment.className} (${statusLabel(enrollment.status)})`)
    .join(', ')

const createdAtLabel = (value?: string) => value ? new Date(value).toLocaleDateString('ko-KR') : '-'
</script>

<template>
  <el-dialog v-model="dialogVisible" title="중복 학생 계정 정리" width="820px" destroy-on-close>
    <div v-loading="loading">
      <template v-if="!selectedGroup">
        <el-alert type="info" :closable="false" style="margin-bottom: 16px">
          이름만 같은 학생은 후보로 잡지 않습니다. 전화번호 또는 학교·학년 정보가 함께 일치한 계정만 표시합니다.
        </el-alert>

        <el-empty v-if="!candidates.length" description="확인할 중복 후보가 없습니다." />
        <div v-else class="candidate-list">
          <el-card v-for="(group, index) in candidates" :key="index" shadow="never">
            <div class="candidate-header">
              <div>
                <strong>{{ group.students.map(student => `${student.name} #${student.id}`).join(', ') }}</strong>
                <div class="reason-list">
                  <el-tag v-for="reason in group.matchReasons" :key="reason" size="small" type="warning">
                    {{ reason }}
                  </el-tag>
                </div>
              </div>
              <el-button type="primary" plain @click="reviewGroup(group)">통합 검토</el-button>
            </div>
          </el-card>
        </div>
      </template>

      <template v-else-if="!preview">
        <el-button link type="primary" style="margin-bottom: 12px" @click="resetSelection">← 후보 목록</el-button>
        <el-alert type="warning" :closable="false" style="margin-bottom: 16px">
          대표 계정의 이름·연락처·로그인 정보가 유지됩니다. 나머지 계정의 학습 이력과 반 소속만 대표 계정으로 이동합니다.
        </el-alert>

        <el-table :data="selectedGroup.students" border>
          <el-table-column label="대표" width="74" align="center">
            <template #default="{ row }">
              <el-radio v-model="targetId" :value="row.id" :aria-label="`대표 ID ${row.id}`" />
            </template>
          </el-table-column>
          <el-table-column label="통합" width="74" align="center">
            <template #default="{ row }">
              <el-checkbox
                v-model="sourceIds"
                :value="row.id"
                :disabled="targetId === row.id"
                :aria-label="`통합 ID ${row.id}`"
              />
            </template>
          </el-table-column>
          <el-table-column label="학생" min-width="150">
            <template #default="{ row }">
              <strong>{{ row.name }} #{{ row.id }}</strong>
              <div class="subtext">{{ row.school }} · {{ row.grade }} · {{ statusLabel(row.status) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="연락처" min-width="140">
            <template #default="{ row }">
              <div>{{ row.parentName || '-' }} {{ row.parentPhoneMasked || '' }}</div>
              <div class="subtext">학생 {{ row.contactPhoneMasked || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="반 이력" min-width="220">
            <template #default="{ row }">{{ enrollmentLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="등록일" width="110">
            <template #default="{ row }">{{ createdAtLabel(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </template>

      <template v-else>
        <el-button link type="primary" style="margin-bottom: 12px" @click="preview = null">← 선택 수정</el-button>
        <el-descriptions :column="1" border style="margin-bottom: 16px">
          <el-descriptions-item label="유지할 대표 계정">
            {{ preview.target.name }} #{{ preview.target.id }}
          </el-descriptions-item>
          <el-descriptions-item label="삭제할 중복 계정">
            {{ preview.sources.map(source => `${source.name} #${source.id}`).join(', ') }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="!preview.mergeable" type="error" :closable="false" style="margin-bottom: 16px">
          같은 항목의 기록이 양쪽 계정에 있어 자동 통합할 수 없습니다. 아래 충돌 데이터를 먼저 확인해주세요.
        </el-alert>
        <el-table v-if="preview.conflicts.length" :data="preview.conflicts" border style="margin-bottom: 16px">
          <el-table-column prop="label" label="충돌 데이터" />
          <el-table-column prop="conflictCount" label="충돌 묶음" width="120" align="right" />
        </el-table>

        <h4>변경되는 데이터</h4>
        <el-empty v-if="!visibleImpacts.length" :image-size="52" description="이동하거나 폐기할 연결 데이터가 없습니다." />
        <el-table v-else :data="visibleImpacts" border style="margin-bottom: 16px">
          <el-table-column prop="label" label="데이터" />
          <el-table-column prop="count" label="건수" width="100" align="right" />
          <el-table-column label="처리" width="120">
            <template #default="{ row }">{{ row.action === 'MOVE' ? '대표로 이동' : '보안상 폐기' }}</template>
          </el-table-column>
        </el-table>

        <template v-if="preview.mergeable">
          <el-alert type="warning" :closable="false" style="margin-bottom: 12px">
            통합 후 중복 학생 ID는 삭제되며, 기존 로그인 세션과 자동 로그인은 해제됩니다. 실행 전에 DB 백업을 권장합니다.
          </el-alert>
          <el-input v-model="confirmation" :placeholder="`확인을 위해 '${expectedConfirmation}' 입력`" />
        </template>
      </template>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">닫기</el-button>
      <el-button
        v-if="selectedGroup && !preview"
        type="primary"
        :disabled="!canPreview"
        @click="createPreview"
      >영향 확인</el-button>
      <el-button
        v-if="preview?.mergeable"
        type="danger"
        :loading="merging"
        :disabled="!canMerge"
        @click="executeMerge"
      >계정 통합 실행</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.candidate-list { display: grid; gap: 12px; }
.candidate-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.reason-list { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.subtext { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
h4 { margin: 0 0 10px; }
</style>
