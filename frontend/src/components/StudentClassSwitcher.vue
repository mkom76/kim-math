<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const switching = ref(false)

const selectableClasses = computed(() =>
  authStore.studentClasses.filter(item => item.selectable)
)
const hasMultipleChoices = computed(() => selectableClasses.value.length > 1)

const handleChange = async (classId: number) => {
  if (!classId || classId === authStore.activeStudentClassId || switching.value) return
  switching.value = true
  try {
    await authStore.switchStudentClass(classId)
    window.location.reload()
  } catch (error) {
    console.error('Student class switch failed:', error)
    ElMessage.error('반을 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  } finally {
    switching.value = false
  }
}
</script>

<template>
  <section v-if="authStore.activeStudentClass" class="student-class-switcher" aria-label="현재 반">
    <div class="student-class-switcher__inner">
      <span class="student-class-switcher__label">현재 반</span>
      <el-select
        v-if="hasMultipleChoices"
        :model-value="authStore.activeStudentClassId"
        :loading="switching"
        size="small"
        class="student-class-switcher__select"
        aria-label="반 선택"
        @change="handleChange"
      >
        <el-option
          v-for="item in selectableClasses"
          :key="item.classId"
          :label="item.className"
          :value="item.classId"
        />
      </el-select>
      <strong v-else>{{ authStore.activeStudentClass.className }}</strong>
      <span v-if="authStore.studentClassReadOnly" class="student-class-switcher__readonly">
        종강 · 조회 전용
      </span>
    </div>
  </section>
</template>

<style scoped>
.student-class-switcher {
  position: relative;
  z-index: 20;
  border-bottom: 1px solid #e5e7eb;
  background: rgba(255, 255, 255, 0.96);
}

.student-class-switcher__inner {
  display: flex;
  min-height: 44px;
  max-width: 1120px;
  margin: 0 auto;
  padding: 6px 16px;
  align-items: center;
  gap: 10px;
  color: #1f2937;
}

.student-class-switcher__label {
  color: #6b7280;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.student-class-switcher__select {
  width: min(240px, 62vw);
}

.student-class-switcher__readonly {
  padding: 3px 7px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 11px;
  font-weight: 700;
}
</style>
