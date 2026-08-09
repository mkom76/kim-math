import { computed, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

export type StudentUiMode = 'legacy' | 'v2'

export const STUDENT_UI_MODE_KEY = 'kim-math:student-ui-mode'
const modeOverrides = ref<Record<string, StudentUiMode>>({})

export function useStudentUiMode() {
  const authStore = useAuthStore()
  const storageKey = computed(() => authStore.userId
    ? `${STUDENT_UI_MODE_KEY}:${authStore.userId}`
    : null)
  const mode = computed<StudentUiMode>(() => {
    if (typeof window === 'undefined' || !storageKey.value) return 'legacy'
    const override = modeOverrides.value[storageKey.value]
    if (override) return override
    return window.localStorage.getItem(storageKey.value) === 'v2' ? 'v2' : 'legacy'
  })

  const setMode = (nextMode: StudentUiMode) => {
    if (typeof window !== 'undefined' && storageKey.value) {
      window.localStorage.setItem(storageKey.value, nextMode)
      modeOverrides.value = { ...modeOverrides.value, [storageKey.value]: nextMode }
    }
  }

  return {
    mode,
    setMode,
    enablePreview: () => setMode('v2'),
    leavePreview: () => setMode('legacy'),
  }
}
