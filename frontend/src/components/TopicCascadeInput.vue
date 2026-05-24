<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { textbookAPI } from '@/api/client'
import { parseTopicPath, joinTopicLevels, TOPIC_MAX_LEVELS } from '@/utils/topicPath'

const props = defineProps<{
  modelValue: string | null | undefined
  /** Textbook to scope autocomplete suggestions to. */
  textbookId?: number
  placeholder?: string
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const levels = ref<string[]>(parseTopicPath(props.modelValue))

// Sync from outside (e.g. edit dialog opening with a new row)
watch(() => props.modelValue, v => {
  const next = parseTopicPath(v)
  // Avoid wiping local typing if the path matches
  if (joinTopicLevels(next) !== joinTopicLevels(levels.value)) {
    levels.value = next
  }
})

/** Show level i when i===0 or the previous level has a non-blank value. */
const visibleCount = computed(() => {
  let shown = 1
  for (let i = 0; i < TOPIC_MAX_LEVELS - 1; i++) {
    if (levels.value[i] && levels.value[i].trim().length > 0) shown = i + 2
    else break
  }
  return Math.min(shown, TOPIC_MAX_LEVELS)
})

function onChange(index: number, value: string) {
  const next = [...levels.value]
  next[index] = value.trim()
  // Trim trailing levels beyond the first blank
  for (let i = next.length - 1; i >= 0; i--) {
    if (!next[i] || !next[i].trim()) next.pop()
    else break
  }
  // If a middle level got cleared, drop everything after it too
  for (let i = 0; i < next.length; i++) {
    if (!next[i] || !next[i].trim()) {
      next.length = i
      break
    }
  }
  levels.value = next
  emit('update:modelValue', joinTopicLevels(next))
}

/** el-autocomplete fetcher per level — filters by selected parent levels. */
function suggesterFor(index: number) {
  return async (query: string, cb: (items: Array<{ value: string }>) => void) => {
    if (!props.textbookId) { cb([]); return }
    try {
      const res = await textbookAPI.topicSuggestions(props.textbookId, {
        level: index + 1,
        l1: levels.value[0],
        l2: levels.value[1],
        l3: levels.value[2],
        l4: levels.value[3],
      })
      const q = (query || '').trim().toLowerCase()
      const all = res.data || []
      const filtered = q ? all.filter(s => s.toLowerCase().includes(q)) : all
      cb(filtered.slice(0, 50).map(value => ({ value })))
    } catch {
      cb([])
    }
  }
}

function placeholderFor(index: number) {
  if (index === 0) return props.placeholder ?? '유형 (예: 함수)'
  return `하위 유형 ${index + 1} (선택)`
}
</script>

<template>
  <div class="topic-cascade">
    <template v-for="i in visibleCount" :key="i - 1">
      <el-autocomplete
        :model-value="levels[i - 1] ?? ''"
        :placeholder="placeholderFor(i - 1)"
        :fetch-suggestions="suggesterFor(i - 1)"
        :trigger-on-focus="true"
        clearable
        size="default"
        @update:model-value="(v: string) => onChange(i - 1, v)"
        @select="(item: { value: string }) => onChange(i - 1, item.value)"
        class="level-input"
      />
      <span v-if="i < visibleCount" class="sep">›</span>
    </template>
    <div v-if="levels.length > 0" class="preview">
      저장될 값:&nbsp;<strong>{{ joinTopicLevels(levels) }}</strong>
    </div>
  </div>
</template>

<style scoped>
.topic-cascade {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.level-input {
  flex: 1 1 160px;
  min-width: 140px;
  max-width: 200px;
}
.sep {
  color: #909399;
  font-size: 16px;
  user-select: none;
}
.preview {
  flex-basis: 100%;
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.preview strong {
  color: #303133;
  font-weight: 500;
}
</style>
