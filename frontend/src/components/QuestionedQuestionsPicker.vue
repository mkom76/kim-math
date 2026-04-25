<script setup lang="ts">
import { ref, computed, watch } from 'vue'

const props = defineProps<{
  questionCount: number
  initialValue: string | null
  onSave: (value: string) => Promise<void>
}>()

const parseValue = (v: string | null): number[] => {
  if (!v) return []
  return v
    .split(',')
    .map(s => s.trim())
    .filter(Boolean)
    .map(Number)
    .filter(n => Number.isFinite(n) && n >= 1 && n <= props.questionCount)
}

const formatValue = (nums: number[]): string =>
  [...new Set(nums)].sort((a, b) => a - b).join(',')

const lastSaved = ref<number[]>(parseValue(props.initialValue))
const selected = ref<Set<number>>(new Set(lastSaved.value))

const numbers = computed(() => Array.from({ length: props.questionCount }, (_, i) => i + 1))

const isSelected = (n: number) => selected.value.has(n)

let debounceTimer: ReturnType<typeof setTimeout> | null = null

const scheduleSave = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(async () => {
    const next = formatValue([...selected.value])
    const prev = formatValue(lastSaved.value)
    if (next === prev) return

    const snapshotBeforeSave = [...lastSaved.value]
    try {
      await props.onSave(next)
      lastSaved.value = [...selected.value]
    } catch (e) {
      // Roll back local UI to last successfully saved state
      selected.value = new Set(snapshotBeforeSave)
      lastSaved.value = snapshotBeforeSave
    }
  }, 500)
}

const toggle = (n: number) => {
  if (selected.value.has(n)) {
    selected.value.delete(n)
  } else {
    selected.value.add(n)
  }
  // Trigger reactivity for Set mutation
  selected.value = new Set(selected.value)
  scheduleSave()
}

watch(
  () => props.initialValue,
  v => {
    const parsed = parseValue(v)
    lastSaved.value = parsed
    selected.value = new Set(parsed)
  },
)
</script>

<template>
  <div class="questioned-questions-picker">
    <button
      v-for="n in numbers"
      :key="n"
      type="button"
      data-test="question-number"
      class="number-btn"
      :class="{ 'is-selected': isSelected(n) }"
      @click="toggle(n)"
    >
      {{ n }}
    </button>
  </div>
</template>

<style scoped>
.questioned-questions-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
.number-btn {
  min-width: 44px;
  min-height: 44px;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  background: #fff;
  color: #303133;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s, border-color 0.15s;
}
.number-btn:active {
  transform: scale(0.97);
}
.number-btn.is-selected {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
}
</style>
