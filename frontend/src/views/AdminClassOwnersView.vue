<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { academyClassAPI, adminClassAPI, adminTeacherAPI, type AdminTeacherDto } from '@/api/client'

const classes = ref<any[]>([])
const teachers = ref<AdminTeacherDto[]>([])
const loading = ref(false)

async function loadAll() {
  loading.value = true
  try {
    const [classRes, teacherRes] = await Promise.all([
      academyClassAPI.getAcademyClasses({ size: 1000 }),
      adminTeacherAPI.list(),
    ])
    const data: any = classRes.data
    classes.value = data?.content || data || []
    teachers.value = teacherRes.data
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

onMounted(loadAll)
</script>

<template>
  <div>
    <h2 style="margin-top: 0;">반 담당자 관리</h2>

    <el-card v-loading="loading" shadow="never">
      <el-table :data="classes" stripe>
        <el-table-column prop="name" label="반 이름" width="200" />
        <el-table-column label="현재 담당자" width="160">
          <template #default="{ row }">
            {{ teacherName(row.ownerTeacherId) }}
          </template>
        </el-table-column>
        <el-table-column label="담당자 변경">
          <template #default="{ row }">
            <el-select
              :model-value="row.ownerTeacherId"
              placeholder="담당자 선택"
              size="small"
              style="width: 200px;"
              @change="(v: number) => changeOwner(row, v)"
            >
              <el-option
                v-for="t in teachers"
                :key="t.teacherId"
                :label="t.name + (t.role === 'ACADEMY_ADMIN' ? ' (관리자)' : '')"
                :value="t.teacherId"
              />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
