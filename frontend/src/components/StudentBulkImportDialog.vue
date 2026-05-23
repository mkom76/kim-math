<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, CircleClose, DocumentCopy } from '@element-plus/icons-vue'
import {
  studentAPI,
  type AcademyClass,
  type StudentBulkCreateResultItem,
} from '@/api/client'
import { parseStudentRows, type ParsedStudentRow } from './studentBulkParser'

const props = defineProps<{
  visible: boolean
  /** Classes the admin can pick from (already filtered to active academy upstream). */
  classes: AcademyClass[]
  /** Names already on the roster, for duplicate warning. */
  existingNames: string[]
}>()
const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'imported'): void
}>()

const selectedClassId = ref<number | null>(null)
const inputText = ref('')
const saving = ref(false)
const results = ref<StudentBulkCreateResultItem[]>([])

const rows = computed<ParsedStudentRow[]>(() => parseStudentRows(inputText.value))
const validRows = computed(() => rows.value.filter(r => r.ok))
const errorCount = computed(() => rows.value.length - validRows.value.length)
const warningCount = computed(() => validRows.value.filter(r => r.warnings.length > 0).length)
const dupSet = computed(() => new Set(props.existingNames))
const dupCount = computed(() =>
  validRows.value.filter(r => r.data && dupSet.value.has(r.data.name)).length
)

const consentBaseUrl = computed(() => window.location.origin + '/consent/')

watch(() => props.visible, v => {
  if (!v) {
    inputText.value = ''
    selectedClassId.value = null
    results.value = []
  }
})

function close() { emit('update:visible', false) }

function fillExample() {
  inputText.value = [
    '김철수\t고1\tA고\t김보호\t010-1234-5678',
    '이영희\t고등학교 2학년\tB고등학교\t이부모\t01098765432',
    '박민수\t고3\tC고\t박학부모\t010 5555 6666\t010-7777-8888',
  ].join('\n')
}

async function submit() {
  if (!selectedClassId.value) {
    ElMessage.warning('반을 선택하세요')
    return
  }
  if (validRows.value.length === 0) {
    ElMessage.warning('등록할 유효한 행이 없습니다')
    return
  }
  saving.value = true
  try {
    const res = await studentAPI.bulkCreate({
      classId: selectedClassId.value,
      students: validRows.value.map(r => r.data!),
    })
    results.value = res.data.created
    ElMessage.success(`${res.data.created.length}명 등록 완료. 동의 링크를 학부모에게 전달하세요.`)
    emit('imported')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '일괄 등록에 실패했습니다')
  } finally {
    saving.value = false
  }
}

async function copyLink(token: string) {
  const url = consentBaseUrl.value + token
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('동의 링크가 클립보드에 복사되었습니다')
  } catch {
    ElMessage.error('복사 실패 — 직접 선택해서 복사해 주세요')
  }
}

async function copyAll() {
  if (results.value.length === 0) return
  const text = results.value
    .map(r => `${r.name} (학부모 ${r.parentName} ${r.parentPhone})\n${consentBaseUrl.value}${r.consentToken}`)
    .join('\n\n')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('전체 명단과 링크가 복사되었습니다')
  } catch {
    ElMessage.error('복사 실패')
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="close"
    title="학생 일괄 등록"
    width="860px"
    top="5vh"
  >
    <!-- Pre-submit: paste + preview -->
    <template v-if="results.length === 0">
      <el-form label-position="top">
        <el-form-item label="등록할 반" required>
          <el-select v-model="selectedClassId" placeholder="반 선택" style="width: 280px;">
            <el-option
              v-for="c in classes"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        <template #title><strong>붙여넣기 가이드</strong></template>
        <div style="font-size: 13px; line-height: 1.7">
          엑셀에서 영역을 복사해 아래 칸에 붙여넣으면 자동 인식됩니다. 컬럼 순서:
          <div class="schema-row">
            이름 │ 학년 │ 학교 │ 보호자 이름 │ 보호자 휴대폰 │ 학생 휴대폰(선택)
          </div>
          <div class="sample-row">
            <span class="sample-label">예시</span>
            <code>김철수&nbsp;&nbsp;고1&nbsp;&nbsp;A고&nbsp;&nbsp;김보호&nbsp;&nbsp;010-1234-5678</code>
          </div>
          <div style="margin-top: 6px; color: #606266;">
            • 전화는 <code>010-1234-5678</code> / <code>01012345678</code> / <code>010 1234 5678</code> 모두 지원<br />
            • 학년은 <code>고1</code> / <code>고등학교 1학년</code> / <code>중2</code> / <code>초3</code> 등 자동 정규화<br />
            • 미리보기 표에 "저장될 값"이 그대로 표시되므로 확인 후 등록하세요<br />
            • 학부모 동의 완료 전까지 학생은 로그인할 수 없으며, 초기 PIN은 <strong>보호자 휴대폰 뒤 4자리</strong>입니다
          </div>
        </div>
      </el-alert>

      <div style="position: relative;">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="8"
          placeholder="여기에 엑셀 영역을 붙여넣으세요 (예시 채우기 버튼으로 샘플 확인 가능)"
          style="font-family: monospace"
        />
        <el-button
          v-if="!inputText"
          size="small"
          link
          type="primary"
          style="position: absolute; right: 8px; bottom: 6px;"
          @click="fillExample"
        >
          예시 채우기
        </el-button>
      </div>

      <div v-if="rows.length > 0" style="margin-top: 16px">
        <div style="display: flex; gap: 8px; align-items: center; margin-bottom: 8px">
          <el-tag type="success" size="small">유효 {{ validRows.length }}</el-tag>
          <el-tag v-if="errorCount > 0" type="danger" size="small">오류 {{ errorCount }}</el-tag>
          <el-tag v-if="warningCount > 0" type="warning" size="small">알림 {{ warningCount }}</el-tag>
          <el-tag v-if="dupCount > 0" type="warning" size="small">동명이인 {{ dupCount }} (그대로 추가됨)</el-tag>
          <span style="margin-left: auto; color: #909399; font-size: 12px;">
            아래에 보이는 값이 그대로 저장됩니다 — 원본과 다르면 회색 글씨로 함께 표시
          </span>
        </div>

        <el-table
          :data="rows"
          :max-height="320"
          :row-class-name="(r) => !r.row.ok ? 'row-error' : (r.row.warnings.length > 0 || (r.row.data && dupSet.has(r.row.data.name)) ? 'row-warn' : '')"
        >
          <el-table-column type="index" label="줄" width="50" align="center" />
          <el-table-column label="상태" width="60" align="center">
            <template #default="{ row }">
              <el-icon v-if="row.ok" color="#67c23a"><CircleCheck /></el-icon>
              <el-icon v-else color="#f56c6c"><CircleClose /></el-icon>
            </template>
          </el-table-column>
          <el-table-column label="이름" width="90">
            <template #default="{ row }">{{ row.data?.name ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="학년" width="80">
            <template #default="{ row }">
              <div>{{ row.data?.grade ?? '—' }}</div>
              <div v-if="row.raw.grade" class="raw-note">입력: {{ row.raw.grade }}</div>
            </template>
          </el-table-column>
          <el-table-column label="학교" min-width="120">
            <template #default="{ row }">
              <div>{{ row.data?.school ?? '—' }}</div>
              <div v-if="row.raw.school" class="raw-note">입력: {{ row.raw.school }}</div>
            </template>
          </el-table-column>
          <el-table-column label="보호자" width="90">
            <template #default="{ row }">{{ row.data?.parentName ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="보호자 전화" width="150">
            <template #default="{ row }">
              <div>{{ row.data?.parentPhone ?? '—' }}</div>
              <div v-if="row.raw.parentPhone" class="raw-note">입력: {{ row.raw.parentPhone }}</div>
            </template>
          </el-table-column>
          <el-table-column label="학생 전화" width="150">
            <template #default="{ row }">
              <div>{{ row.data?.contactPhone || '—' }}</div>
              <div v-if="row.raw.contactPhone" class="raw-note">입력: {{ row.raw.contactPhone }}</div>
            </template>
          </el-table-column>
          <el-table-column label="메모" min-width="180">
            <template #default="{ row }">
              <div v-if="row.errors.length > 0" class="msg-err">{{ row.errors.join(' / ') }}</div>
              <div v-else-if="row.warnings.length > 0" class="msg-warn">{{ row.warnings.join(' / ') }}</div>
              <div v-else-if="row.data && dupSet.has(row.data.name)" class="msg-warn">
                동명이인이 이미 있습니다 (그대로 추가됨)
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- Post-submit: distribute consent links -->
    <template v-else>
      <el-alert
        type="success"
        :closable="false"
        show-icon
        :title="`${results.length}명이 등록되었습니다.`"
        description="아래 동의 링크를 학부모에게 카카오톡/문자로 전달해 주세요. 학부모가 동의를 완료하면 학생이 로그인할 수 있습니다."
        style="margin-bottom: 14px;"
      />
      <div style="margin-bottom: 8px; text-align: right;">
        <el-button size="small" :icon="DocumentCopy" @click="copyAll">전체 명단·링크 복사</el-button>
      </div>
      <el-table :data="results" :max-height="420" stripe>
        <el-table-column label="학생" width="100" prop="name" />
        <el-table-column label="보호자" width="100" prop="parentName" />
        <el-table-column label="보호자 전화" width="150" prop="parentPhone" />
        <el-table-column label="동의 링크" min-width="280">
          <template #default="{ row }">
            <div style="font-family: monospace; font-size: 12px; word-break: break-all; color: #606266;">
              {{ consentBaseUrl }}{{ row.consentToken }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="" width="80" align="center">
          <template #default="{ row }">
            <el-button size="small" :icon="DocumentCopy" circle @click="copyLink(row.consentToken)" />
          </template>
        </el-table-column>
      </el-table>
    </template>

    <template #footer>
      <el-button @click="close">{{ results.length > 0 ? '닫기' : '취소' }}</el-button>
      <el-button
        v-if="results.length === 0"
        type="primary"
        :disabled="validRows.length === 0 || !selectedClassId"
        :loading="saving"
        @click="submit"
      >
        {{ validRows.length }}명 등록{{ errorCount > 0 ? ` (오류 ${errorCount}개 제외)` : '' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
:deep(.row-error) { background-color: #fef0f0 !important; }
:deep(.row-warn)  { background-color: #fdf6ec !important; }

.schema-row {
  margin-top: 4px;
  font-family: monospace;
  background: #f5f7fa;
  padding: 6px 10px;
  border-radius: 4px;
}
.sample-row {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.sample-label {
  font-size: 11px;
  color: #909399;
  background: #ecf5ff;
  padding: 1px 6px;
  border-radius: 3px;
}
.sample-row code {
  font-size: 12px;
  color: #303133;
}
code {
  background: #f5f7fa;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}
.raw-note {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
.msg-err {
  color: #f56c6c;
  font-size: 12px;
  line-height: 1.4;
}
.msg-warn {
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.4;
}
</style>
