<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const props = withDefaults(
  defineProps<{
    variant?: 'bar' | 'home'
    reloadOnSwitch?: boolean
  }>(),
  {
    variant: 'bar',
    reloadOnSwitch: true,
  },
)

const emit = defineEmits<{
  switched: [classId: number]
}>()

const authStore = useAuthStore()
const switchingClassId = ref<number | null>(null)
const choicesOpen = ref(false)

const selectableClasses = computed(() => authStore.studentClasses.filter((item) => item.selectable))
const hasMultipleChoices = computed(() => selectableClasses.value.length > 1)
const isHomeVariant = computed(() => props.variant === 'home')

const handleChange = async (classId: number) => {
  if (!classId || classId === authStore.activeStudentClassId || switchingClassId.value) return
  switchingClassId.value = classId
  try {
    await authStore.switchStudentClass(classId)
    choicesOpen.value = false
    emit('switched', classId)
    if (props.reloadOnSwitch) window.location.reload()
  } catch (error) {
    console.error('Student class switch failed:', error)
    ElMessage.error('반을 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  } finally {
    switchingClassId.value = null
  }
}
</script>

<template>
  <section
    v-if="authStore.activeStudentClass"
    class="student-class-switcher"
    :class="`student-class-switcher--${props.variant}`"
    aria-label="현재 반"
  >
    <template v-if="isHomeVariant">
      <button
        v-if="hasMultipleChoices"
        type="button"
        class="student-class-switcher__summary"
        :aria-expanded="choicesOpen"
        aria-controls="student-class-choices"
        @click="choicesOpen = !choicesOpen"
      >
        <span class="student-class-switcher__home-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <path d="M4 6.5h16v11H4z" />
            <path d="M8 10h8M8 14h5" />
          </svg>
        </span>
        <span class="student-class-switcher__home-copy" aria-live="polite">
          <small>학습 반</small>
          <strong>{{ authStore.activeStudentClass.className }}</strong>
        </span>
        <span class="student-class-switcher__change">
          변경
          <svg viewBox="0 0 16 16" :class="{ 'is-open': choicesOpen }" aria-hidden="true">
            <path d="m4 6 4 4 4-4" />
          </svg>
        </span>
      </button>

      <div v-else class="student-class-switcher__summary">
        <span class="student-class-switcher__home-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <path d="M4 6.5h16v11H4z" />
            <path d="M8 10h8M8 14h5" />
          </svg>
        </span>
        <span class="student-class-switcher__home-copy">
          <small>학습 반</small>
          <strong>{{ authStore.activeStudentClass.className }}</strong>
        </span>
        <span v-if="authStore.studentClassReadOnly" class="student-class-switcher__readonly">
          종강 · 조회 전용
        </span>
      </div>

      <div
        v-if="hasMultipleChoices && choicesOpen"
        id="student-class-choices"
        class="student-class-switcher__choices"
        role="group"
        aria-label="학습할 반 선택"
      >
        <button
          v-for="item in selectableClasses"
          :key="item.classId"
          type="button"
          class="student-class-switcher__choice"
          :class="{ 'is-active': item.classId === authStore.activeStudentClassId }"
          :aria-pressed="item.classId === authStore.activeStudentClassId"
          :disabled="switchingClassId !== null"
          @click="handleChange(item.classId)"
        >
          <span class="student-class-switcher__choice-copy">
            <strong>{{ item.className }}</strong>
            <small v-if="item.classId === authStore.activeStudentClassId">현재 선택</small>
          </span>
          <span
            v-if="switchingClassId === item.classId"
            class="student-class-switcher__spinner"
            aria-label="반 변경 중"
          />
          <svg
            v-else-if="item.classId === authStore.activeStudentClassId"
            class="student-class-switcher__check"
            viewBox="0 0 16 16"
            aria-hidden="true"
          >
            <path d="m3 8.2 3.1 3.1L13 4.8" />
          </svg>
        </button>
      </div>
    </template>

    <div v-else class="student-class-switcher__inner">
      <span class="student-class-switcher__label">현재 반</span>
      <el-select
        v-if="hasMultipleChoices"
        :model-value="authStore.activeStudentClassId"
        :loading="switchingClassId !== null"
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
.student-class-switcher--bar {
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
  flex: 0 0 auto;
  padding: 5px 9px;
  border-radius: 999px;
  background: var(--student-slate-100, #f3f4f6);
  color: var(--student-muted, #6b7280);
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}

.student-class-switcher--home {
  min-width: 0;
}

.student-class-switcher__summary {
  display: flex;
  box-sizing: border-box;
  width: 100%;
  min-height: 58px;
  padding: 9px 12px;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--student-border, #dfe5ec);
  border-radius: 16px;
  color: var(--student-text, #344054);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--student-shadow-soft, 0 5px 18px rgba(28, 46, 78, 0.06));
  font: inherit;
  text-align: left;
}

button.student-class-switcher__summary {
  cursor: pointer;
  transition:
    border-color 160ms ease,
    background-color 160ms ease;
}

button.student-class-switcher__summary:hover {
  border-color: var(--student-primary-border, #cbd8fb);
  background: var(--student-surface-hover, #f8faff);
}

button.student-class-switcher__summary:focus-visible {
  outline: none;
  box-shadow: var(--student-focus-ring, 0 0 0 3px rgba(36, 87, 214, 0.25));
}

.student-class-switcher__home-icon {
  display: grid;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 11px;
  color: var(--student-primary, #2457d6);
  background: var(--student-primary-soft, #eaf0ff);
}

.student-class-switcher__home-icon svg {
  width: 19px;
  height: 19px;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.student-class-switcher__home-copy {
  display: grid;
  min-width: 0;
  flex: 1 1 auto;
  gap: 1px;
}

.student-class-switcher__home-copy strong {
  overflow: hidden;
  color: var(--student-ink, #172033);
  font-size: 14px;
  font-weight: 800;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.student-class-switcher__home-copy small {
  overflow: hidden;
  color: var(--student-muted, #667085);
  font-size: 10px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.student-class-switcher__change {
  display: inline-flex;
  flex: 0 0 auto;
  min-height: 32px;
  padding: 0 9px;
  align-items: center;
  gap: 3px;
  border-radius: 10px;
  color: var(--student-primary, #2457d6);
  background: var(--student-primary-soft, #eaf0ff);
  font-size: 11px;
  font-weight: 800;
}

.student-class-switcher__change svg {
  width: 13px;
  height: 13px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
  transition: transform 160ms ease;
}

.student-class-switcher__change svg.is-open {
  transform: rotate(180deg);
}

.student-class-switcher__choices {
  display: grid;
  gap: 3px;
  margin-top: 6px;
  padding: 5px;
  border: 1px solid var(--student-border, #dfe5ec);
  border-radius: 14px;
  background: var(--student-surface, #fff);
  box-shadow: var(--student-shadow-soft, 0 5px 18px rgba(28, 46, 78, 0.06));
}

.student-class-switcher__choice {
  display: flex;
  width: 100%;
  min-height: 44px;
  padding: 6px 10px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border: 0;
  border-radius: 10px;
  color: var(--student-text, #344054);
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    background-color 160ms ease,
    color 160ms ease;
}

.student-class-switcher__choice:hover {
  background: var(--student-surface-hover, #f8faff);
}

.student-class-switcher__choice:focus-visible {
  outline: none;
  box-shadow: var(--student-focus-ring, 0 0 0 3px rgba(36, 87, 214, 0.25));
}

.student-class-switcher__choice.is-active {
  color: var(--student-primary-strong, #1945b8);
  background: var(--student-primary-soft, #eaf0ff);
}

.student-class-switcher__choice:disabled {
  cursor: wait;
}

.student-class-switcher__choice-copy {
  display: grid;
  min-width: 0;
  gap: 1px;
}

.student-class-switcher__choice-copy strong {
  overflow: hidden;
  font-size: 13px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.student-class-switcher__choice-copy small {
  font-size: 10px;
  font-weight: 700;
}

.student-class-switcher__check {
  flex: 0 0 auto;
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.student-class-switcher__spinner {
  width: 13px;
  height: 13px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: student-class-spinner 0.7s linear infinite;
}

@keyframes student-class-spinner {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .student-class-switcher__choice,
  .student-class-switcher__spinner {
    transition: none;
    animation-duration: 1.5s;
  }
}
</style>
