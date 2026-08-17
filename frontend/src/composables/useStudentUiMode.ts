import { computed, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

export type StudentUiMode = 'legacy' | 'v2'

export const STUDENT_UI_MODE_KEY = 'kim-math:student-ui-mode'
export const STUDENT_UI_PREVIEW_SEEN_KEY = 'kim-math:student-ui-preview-seen'
const modeOverrides = ref<Record<string, StudentUiMode>>({})
const seenOverrides = ref<Record<string, boolean>>({})

export function useStudentUiMode() {
  const authStore = useAuthStore()
  const storageKey = computed(() => authStore.userId
    ? `${STUDENT_UI_MODE_KEY}:${authStore.userId}`
    : null)
  const seenStorageKey = computed(() => authStore.userId
    ? `${STUDENT_UI_PREVIEW_SEEN_KEY}:${authStore.userId}`
    : null)
  const mode = computed<StudentUiMode>(() => {
    if (typeof window === 'undefined' || !storageKey.value) return 'legacy'
    const override = modeOverrides.value[storageKey.value]
    if (override) return override
    const storedMode = window.localStorage.getItem(storageKey.value)
    if (storedMode === 'legacy' || storedMode === 'v2') return storedMode
    return authStore.studentUiDefaultMode
  })
  const hasSeenPreview = computed(() => {
    if (typeof window === 'undefined' || !seenStorageKey.value) return false
    const override = seenOverrides.value[seenStorageKey.value]
    if (override !== undefined) return override
    return window.localStorage.getItem(seenStorageKey.value) === 'true'
  })

  const markPreviewSeen = () => {
    if (typeof window !== 'undefined' && seenStorageKey.value) {
      window.localStorage.setItem(seenStorageKey.value, 'true')
      seenOverrides.value = { ...seenOverrides.value, [seenStorageKey.value]: true }
    }
  }

  const setMode = (nextMode: StudentUiMode) => {
    if (typeof window !== 'undefined' && storageKey.value) {
      window.localStorage.setItem(storageKey.value, nextMode)
      modeOverrides.value = { ...modeOverrides.value, [storageKey.value]: nextMode }
      if (nextMode === 'v2') markPreviewSeen()
    }
  }

  return {
    mode,
    hasSeenPreview,
    setMode,
    enablePreview: () => setMode('v2'),
    leavePreview: () => {
      markPreviewSeen()
      setMode('legacy')
    },
  }
}
