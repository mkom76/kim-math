<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { lessonAPI, academyAPI, academyClassAPI, type Lesson, type Academy, type AcademyClass } from '../api/client'
import { usePagination } from '../composables/usePagination'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const lessons = ref<Lesson[]>([])
const academies = ref<Academy[]>([])
const allClasses = ref<AcademyClass[]>([])
const searchQuery = ref('')
const dialogVisible = ref(false)
const filterAcademyId = ref<number | undefined>(undefined)
const filterClassId = ref<number | undefined>(undefined)
const currentLesson = ref<{ academyId?: number; classId?: number; lessonDate?: string }>({
  academyId: undefined,
  classId: undefined,
  lessonDate: undefined
})
const { currentPage, pageSize } = usePagination('lessons-view')

const availableClasses = computed(() => {
  if (!currentLesson.value.academyId) return []
  return allClasses.value.filter(cls => cls.academyId === currentLesson.value.academyId)
})

const filterAvailableClasses = computed(() => {
  if (!filterAcademyId.value) return []
  return allClasses.value.filter(cls => cls.academyId === filterAcademyId.value)
})

const filteredData = computed(() => {
  let filtered = lessons.value

  // Apply academy filter
  if (filterAcademyId.value) {
    filtered = filtered.filter(lesson => lesson.academyId === filterAcademyId.value)
  }

  // Apply class filter
  if (filterClassId.value) {
    filtered = filtered.filter(lesson => lesson.classId === filterClassId.value)
  }

  // Apply search query
  if (searchQuery.value) {
    filtered = filtered.filter(lesson =>
      (lesson.academyName || '').toLowerCase().includes(searchQuery.value.toLowerCase()) ||
      (lesson.className || '').toLowerCase().includes(searchQuery.value.toLowerCase()) ||
      (lesson.lessonDate || '').includes(searchQuery.value)
    )
  }

  return filtered
})

const tableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredData.value.slice(start, end)
})

const totalItems = computed(() => filteredData.value.length)

watch(() => currentLesson.value.academyId, () => {
  currentLesson.value.classId = undefined
})

watch(() => filterAcademyId.value, (newVal, oldVal) => {
  // Only clear classId when user manually changes academy (not on initial load)
  if (oldVal !== undefined && newVal !== oldVal) {
    filterClassId.value = undefined
  }
})

const fetchLessons = async () => {
  loading.value = true
  try {
    const response = await lessonAPI.getLessons({ size: 10000 })
    lessons.value = response.data.content || response.data
  } catch (error) {
    ElMessage.error('수업 목록을 불러오는데 실패했습니다.')
  } finally {
    loading.value = false
  }
}

const fetchAcademies = async () => {
  try {
    const response = await academyAPI.getAcademies({ size: 10000 })
    academies.value = response.data.content || response.data
  } catch (error) {
    ElMessage.error('학원 목록을 불러오는데 실패했습니다.')
  }
}

const fetchClasses = async () => {
  try {
    const response = await academyClassAPI.getAcademyClasses({ size: 10000 })
    allClasses.value = response.data.content || response.data
  } catch (error) {
    ElMessage.error('반 목록을 불러오는데 실패했습니다.')
  }
}

const openAddDialog = () => {
  const now = new Date()
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  currentLesson.value = {
    academyId: undefined,
    classId: undefined,
    lessonDate: today
  }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!currentLesson.value.academyId || !currentLesson.value.classId || !currentLesson.value.lessonDate) {
    ElMessage.error('학원, 반, 날짜를 모두 선택해주세요.')
    return
  }

  try {
    await lessonAPI.createLesson({
      academyId: currentLesson.value.academyId,
      classId: currentLesson.value.classId,
      lessonDate: currentLesson.value.lessonDate
    })
    ElMessage.success('수업이 생성되었습니다.')
    dialogVisible.value = false
    fetchLessons()
  } catch (error: any) {
    if (error.response?.data?.message) {
      ElMessage.error(error.response.data.message)
    } else {
      ElMessage.error('수업 생성에 실패했습니다.')
    }
  }
}

const handleDelete = async (lesson: Lesson) => {
  try {
    await ElMessageBox.confirm(
      '이 수업을 삭제하시겠습니까? (시험 또는 숙제가 연결되어 있으면 삭제할 수 없습니다)',
      '확인',
      {
        confirmButtonText: '삭제',
        cancelButtonText: '취소',
        type: 'warning',
      }
    )

    await lessonAPI.deleteLesson(lesson.id!)
    ElMessage.success('수업이 삭제되었습니다.')
    fetchLessons()
  } catch (error: any) {
    if (error !== 'cancel') {
      if (error.response?.data?.message) {
        ElMessage.error(error.response.data.message)
      } else {
        ElMessage.error('수업 삭제에 실패했습니다.')
      }
    }
  }
}

const navigateToDetail = (lesson: Lesson) => {
  router.push(`/lessons/${lesson.id}`)
}

onMounted(async () => {
  fetchLessons()

  // Wait for academies and classes to load first
  await Promise.all([fetchAcademies(), fetchClasses()])

  // Apply filters from URL query parameters after data is loaded
  if (route.query.academyId) {
    filterAcademyId.value = Number(route.query.academyId)
  }
  if (route.query.classId) {
    filterClassId.value = Number(route.query.classId)
  }
})
</script>

<template>
  <div class="teacher-view" style="padding: 24px; max-width: 1400px; margin: 0 auto">
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <h2 style="margin: 0; font-size: 24px; font-weight: 600">수업 관리</h2>
          <div style="display: flex; gap: 12px">
            <el-input
              v-model="searchQuery"
              placeholder="검색..."
              style="width: 250px"
              clearable
            />
            <el-button type="primary" @click="openAddDialog">새 수업 만들기</el-button>
          </div>
        </div>
      </template>

      <!-- Filters -->
      <div style="margin-bottom: 20px; padding: 16px; background: #f5f7fa; border-radius: 8px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap">

        <div style="display: flex; align-items: center; gap: 8px">
          <el-icon size="16" color="#606266"><OfficeBuilding /></el-icon>
          <el-select
            v-model="filterAcademyId"
            placeholder="전체 학원"
            clearable
            style="width: 200px"
            size="default"
          >
            <el-option
              v-for="academy in academies"
              :key="academy.id"
              :label="academy.name"
              :value="academy.id"
            />
          </el-select>
        </div>

        <div style="display: flex; align-items: center; gap: 8px">
          <el-icon size="16" color="#606266"><Grid /></el-icon>
          <el-select
            v-model="filterClassId"
            placeholder="전체 반"
            clearable
            style="width: 200px"
            size="default"
            :disabled="!filterAcademyId"
          >
            <el-option
              v-for="cls in filterAvailableClasses"
              :key="cls.id"
              :label="cls.name"
              :value="cls.id"
            />
          </el-select>
        </div>

        <div style="flex: 1"></div>

        <el-tag v-if="filterAcademyId || filterClassId" type="primary" effect="plain" size="large">
          <el-icon style="margin-right: 4px"><Document /></el-icon>
          {{ totalItems }}개 수업
        </el-tag>
      </div>

      <el-table :data="tableData" style="width: 100%" v-loading="loading">
        <el-table-column prop="lessonDate" label="수업 날짜" width="150" sortable>
          <template #default="{ row }">
            {{ new Date(row.lessonDate).toLocaleDateString('ko-KR') }}
          </template>
        </el-table-column>
        <el-table-column prop="academyName" label="학원" width="200" />
        <el-table-column prop="className" label="반" width="150" />
        <el-table-column label="시험" width="200">
          <template #default="{ row }">
            <span v-if="row.testTitle">{{ row.testTitle }}</span>
            <el-tag v-else type="info" size="small">없음</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="navigateToDetail(row)">
              상세
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">
              삭제
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="tableData.length === 0" description="수업이 없습니다" style="padding: 60px 0" />

      <div v-if="totalItems > 0" style="margin-top: 16px; display: flex; justify-content: space-between; align-items: center">
        <span style="color: #909399; font-size: 14px">
          전체 {{ totalItems }}개
        </span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalItems"
          layout="sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      title="새 수업 만들기"
      width="500px"
    >
      <el-form label-width="80px">
        <el-form-item label="학원">
          <el-select v-model="currentLesson.academyId" placeholder="학원 선택" style="width: 100%">
            <el-option
              v-for="academy in academies"
              :key="academy.id"
              :label="academy.name"
              :value="academy.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="반">
          <el-select
            v-model="currentLesson.classId"
            placeholder="반 선택"
            style="width: 100%"
            :disabled="!currentLesson.academyId"
          >
            <el-option
              v-for="cls in availableClasses"
              :key="cls.id"
              :label="cls.name"
              :value="cls.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="수업 날짜">
          <el-date-picker
            v-model="currentLesson.lessonDate"
            type="date"
            placeholder="날짜 선택"
            style="width: 100%"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">취소</el-button>
        <el-button type="primary" @click="handleSubmit">생성</el-button>
      </template>
    </el-dialog>
  </div>
</template>
