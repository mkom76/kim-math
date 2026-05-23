<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { academyClassAPI, adminClassAPI, adminTeacherAPI, type AdminTeacherDto } from '@/api/client'

const classes = ref<any[]>([])
const teachers = ref<AdminTeacherDto[]>([])
const assistantsByClass = ref<Record<number, number[]>>({})
const loading = ref(false)

// Owner candidates exclude ASSISTANT-role teachers (backend would reject anyway)
const ownerCandidates = computed(() =>
  teachers.value.filter(t => t.role !== 'ASSISTANT')
)

async function loadAll() {
  loading.value = true
  try {
    const [classRes, teacherRes, assistantRes] = await Promise.all([
      academyClassAPI.getAcademyClasses({ size: 1000 }),
      adminTeacherAPI.list(),
      adminClassAPI.listAssistants(),
    ])
    const data: any = classRes.data
    classes.value = data?.content || data || []
    teachers.value = teacherRes.data
    // Backend returns Map<Long, List<Long>> — JSON keys are strings, normalize to number
    const normalized: Record<number, number[]> = {}
    for (const [k, v] of Object.entries(assistantRes.data || {})) {
      normalized[Number(k)] = v as number[]
    }
    assistantsByClass.value = normalized
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '데이터 로드 실패')
  } finally {
    loading.value = false
  }
}

async function changeOwner(classRow: any, newTeacherId: number) {
  if (classRow.ownerTeacherId === newTeacherId) return
  try {
    await adminClassAPI.updateOwner(classRow.id, newTeacherId)
    ElMessage.success('담당자가 변경되었습니다')
    await loadAll()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '담당자 변경 실패')
  }
}

function teacherName(id?: number) {
  if (!id) return '미지정'
  const t = teachers.value.find(x => x.teacherId === id)
  return t ? t.name : `#${id}`
}

function assistantCandidates(classRow: any): AdminTeacherDto[] {
  const assigned = new Set(assistantsByClass.value[classRow.id] || [])
  return teachers.value.filter(t =>
    // exclude the owner (owner already has full power) and already-assigned
    t.teacherId !== classRow.ownerTeacherId && !assigned.has(t.teacherId)
  )
}

async function addAssistant(classRow: any, teacherId: number) {
  if (!teacherId) return
  try {
    await adminClassAPI.addAssistant(classRow.id, teacherId)
    ElMessage.success('조교가 추가되었습니다')
    await loadAll()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '조교 추가 실패')
  }
}

async function removeAssistant(classRow: any, teacherId: number) {
  try {
    await ElMessageBox.confirm(
      `${teacherName(teacherId)} 조교를 ${classRow.name} 반에서 제거하시겠습니까?`,
      '조교 제거 확인',
      { confirmButtonText: '제거', cancelButtonText: '취소', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await adminClassAPI.removeAssistant(classRow.id, teacherId)
    ElMessage.success('조교가 제거되었습니다')
    await loadAll()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '조교 제거 실패')
  }
}

function roleSuffix(role: string): string {
  if (role === 'ACADEMY_ADMIN') return ' (관리자)'
  if (role === 'ASSISTANT') return ' (조교)'
  return ''
}

onMounted(loadAll)
</script>

<template>
  <div>
    <h2 style="margin-top: 0;">반 담당자 / 조교 관리</h2>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="classes" stripe>
        <el-table-column prop="name" label="반 이름" width="180" />
        <el-table-column label="현재 담당자" width="140">
          <template #default="{ row }">
            {{ teacherName(row.ownerTeacherId) }}
          </template>
        </el-table-column>
        <el-table-column label="담당자 변경" width="220">
          <template #default="{ row }">
            <el-select
              :model-value="row.ownerTeacherId"
              placeholder="담당자 선택"
              size="small"
              style="width: 200px;"
              @change="(v: number) => changeOwner(row, v)"
            >
              <el-option
                v-for="t in ownerCandidates"
                :key="t.teacherId"
                :label="t.name + roleSuffix(t.role)"
                :value="t.teacherId"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="조교">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
              <el-tag
                v-for="tid in assistantsByClass[row.id] || []"
                :key="tid"
                closable
                type="info"
                @close="removeAssistant(row, tid)"
              >
                {{ teacherName(tid) }}
              </el-tag>
              <el-select
                placeholder="조교 추가"
                size="small"
                style="width: 160px;"
                :model-value="null"
                @change="(v: number) => addAssistant(row, v)"
              >
                <el-option
                  v-for="t in assistantCandidates(row)"
                  :key="t.teacherId"
                  :label="t.name + roleSuffix(t.role)"
                  :value="t.teacherId"
                />
              </el-select>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
