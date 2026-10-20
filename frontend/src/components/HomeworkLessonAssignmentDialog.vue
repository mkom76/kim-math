<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { academyClassAPI, homeworkAPI, lessonAPI, type AcademyClass, type Homework, type Lesson } from '@/api/client'

const props = defineProps<{ visible: boolean; homework: Homework | null }>()
const emit = defineEmits<{ 'update:visible': [visible: boolean]; assigned: [] }>()
const loading = ref(false)
const saving = ref(false)
const loadFailed = ref(false)
const lessons = ref<Lesson[]>([])
const classes = ref<AcademyClass[]>([])
const assigned = ref<Lesson[]>([])
const selectedLessonIds = ref<number[]>([])
const assignStudents = ref(true)
let loadVersion = 0

const availableLessons = computed(() => lessons.value.filter(lesson =>
  lesson.id != null && lesson.academyId === props.homework?.academyId &&
  classes.value.some(item => item.id === lesson.classId && !item.ended && !item.endedAt) &&
  !assigned.value.some(item => item.id === lesson.id),
).sort((a, b) => b.lessonDate.localeCompare(a.lessonDate) || (a.className || '').localeCompare(b.className || '')))
const lessonLabel = (lesson: Lesson) => `${lesson.lessonDate} · ${lesson.className || ''}`

async function load() {
  const version = ++loadVersion
  if (!props.visible || props.homework?.id == null) return
  loading.value = true
  loadFailed.value = false
  lessons.value = []
  assigned.value = []
  classes.value = []
  selectedLessonIds.value = []
  assignStudents.value = true
  try {
    const [lessonRes, assignedRes, classRes] = await Promise.all([
      lessonAPI.getLessons({ size: 10000 }),
      homeworkAPI.getAssignedLessons(props.homework.id),
      academyClassAPI.getAcademyClasses({ size: 10000 }),
    ])
    if (version !== loadVersion) return
    lessons.value = lessonRes.data.content
    assigned.value = assignedRes.data
    classes.value = classRes.data.content
  } catch {
    if (version === loadVersion) loadFailed.value = true
  } finally {
    if (version === loadVersion) loading.value = false
  }
}
watch(() => [props.visible, props.homework?.id], load, { immediate: true })

function close() {
  if (!saving.value) emit('update:visible', false)
}

async function submit() {
  if (props.homework?.id == null || loading.value || saving.value || loadFailed.value || !selectedLessonIds.value.length) return
  saving.value = true
  try {
    await homeworkAPI.assignToLessons(props.homework.id, selectedLessonIds.value, assignStudents.value)
    ElMessage.success(`${selectedLessonIds.value.length}개 수업에 숙제를 배정했습니다.`)
    emit('assigned')
    emit('update:visible', false)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '수업 배정에 실패했습니다. 선택한 수업은 유지됩니다.')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog :model-value="visible" title="여러 수업에 숙제 배정" width="min(620px, 95vw)" :before-close="close" :close-on-click-modal="false" @update:model-value="close">
    <div v-loading="loading">
      <h3>{{ homework?.title }}</h3>
      <el-alert title="숙제 내용과 문항 구성을 수정하면 배정된 모든 수업에 함께 반영됩니다." type="info" :closable="false" />
      <el-alert v-if="loadFailed" title="수업 목록을 불러오지 못했습니다." type="error" :closable="false" style="margin-top: 16px">
        <el-button size="small" @click="load">다시 불러오기</el-button>
      </el-alert>
      <el-form v-else label-position="top" style="margin-top: 20px">
        <el-form-item v-if="assigned.length" label="이미 배정된 수업">
          <div class="assigned-lessons">
            <el-tag v-for="lesson in assigned" :key="lesson.id" type="success">{{ lessonLabel(lesson) }}</el-tag>
          </div>
        </el-form-item>
        <el-form-item label="추가 배정할 수업" required>
          <el-select data-test="assignment-lessons" v-model="selectedLessonIds" multiple filterable :disabled="loading || saving" placeholder="반 이름 또는 날짜로 수업을 검색하세요" style="width: 100%">
            <el-option v-for="lesson in availableLessons" :key="lesson.id" :value="lesson.id!" :label="lessonLabel(lesson)" />
          </el-select>
          <p v-if="!loading && !availableLessons.length" class="hint">추가 배정할 수업이 없습니다. 수업 관리에서 수업을 먼저 만들어주세요.</p>
        </el-form-item>
        <el-checkbox v-model="assignStudents" :disabled="saving">숙제가 미배정된 학생에게도 배정</el-checkbox>
        <p class="hint">이미 다른 숙제를 배정받은 학생의 배정은 유지합니다. 학생별 배정은 수업 상세에서 조정할 수 있습니다.</p>
      </el-form>
    </div>
    <template #footer>
      <el-button :disabled="saving" @click="close">취소</el-button>
      <el-button data-test="assign-lessons" type="primary" :loading="saving" :disabled="loading || loadFailed || !selectedLessonIds.length" @click="submit">{{ selectedLessonIds.length ? `${selectedLessonIds.length}개 수업에 배정` : '수업을 선택하세요' }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.assigned-lessons { display: flex; flex-wrap: wrap; gap: 8px; }
.hint { margin: 8px 0 0; color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.6; }
</style>
