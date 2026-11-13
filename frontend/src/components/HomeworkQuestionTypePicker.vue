<script setup lang="ts">
import { computed } from 'vue'
import { ElCascader } from 'element-plus'
import {
  getHomeworkQuestionType,
  homeworkTypeOptions,
  matchesHomeworkTypeSearch,
} from '@/utils/homeworkQuestionTypes'

const props = defineProps<{
  modelValue?: string | null
  disabled?: boolean
  subjectId?: string
}>()
const emit = defineEmits<{
  (event: 'update:modelValue', value: string | null): void
}>()
const selected = computed(() => getHomeworkQuestionType(props.modelValue))
const options = computed(() => props.subjectId ? homeworkTypeOptions.filter(option => option.value === props.subjectId) : homeworkTypeOptions)
const unknownType = computed(() => Boolean(props.modelValue) && !selected.value)

function change(value: unknown) {
  if (value == null || value === '') {
    emit('update:modelValue', null)
  } else if (typeof value === 'string' && getHomeworkQuestionType(value)) {
    const type = getHomeworkQuestionType(value)!
    if (!props.subjectId || type.path[0] === options.value[0]?.label) emit('update:modelValue', value)
  }
}

function filter(node: { pathLabels: string[] }, keyword: string) {
  return matchesHomeworkTypeSearch(node.pathLabels, keyword)
}
</script>

<template>
  <div class="homework-type-picker">
    <ElCascader
      :model-value="selected?.id ?? null"
      :options="options"
      :props="{ emitPath: false }"
      :disabled="disabled"
      :filter-method="filter"
      popper-class="homework-type-cascader"
      placeholder="과목·단원·유형을 선택하거나 검색하세요"
      aria-label="문항 유형"
      filterable
      clearable
      @change="change"
    />
    <p v-if="unknownType" class="type-notice" role="alert">목록에서 문항 유형을 다시 선택해 주세요.</p>
    <p v-else-if="selected?.proposed" class="type-notice">본문에서 구성한 분류안입니다. 문항과 맞는지 확인해 주세요.</p>
    <p v-else-if="selected?.corrected" class="type-notice">원문 표제를 교정한 유형입니다. 문항과 맞는지 확인해 주세요.</p>
  </div>
</template>

<style scoped>
.homework-type-picker {
  min-width: 0;
}
.homework-type-picker :deep(.el-cascader) {
  width: 100%;
}
.type-notice {
  margin: 6px 0 0;
  color: var(--el-color-warning-dark-2);
  font-size: 12px;
  line-height: 1.5;
}
</style>

<style>
.homework-type-cascader .el-cascader-panel {
  max-width: calc(100vw - 32px);
  overflow-x: auto;
}
.homework-type-cascader .el-cascader__suggestion-panel {
  max-width: calc(100vw - 32px);
}
</style>
