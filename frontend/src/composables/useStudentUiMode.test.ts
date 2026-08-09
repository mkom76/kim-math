import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { STUDENT_UI_MODE_KEY, useStudentUiMode } from './useStudentUiMode'

describe('useStudentUiMode', () => {
  beforeEach(() => {
    window.localStorage.clear()
    setActivePinia(createPinia())
  })

  it('uses the legacy UI by default', () => {
    const authStore = useAuthStore()
    authStore.userId = 101

    expect(useStudentUiMode().mode.value).toBe('legacy')
  })

  it('persists preview choice separately for each student', () => {
    const authStore = useAuthStore()
    authStore.userId = 101
    const studentUi = useStudentUiMode()

    studentUi.enablePreview()
    expect(studentUi.mode.value).toBe('v2')
    expect(window.localStorage.getItem(`${STUDENT_UI_MODE_KEY}:101`)).toBe('v2')

    authStore.userId = 202
    expect(studentUi.mode.value).toBe('legacy')

    authStore.userId = 101
    expect(studentUi.mode.value).toBe('v2')
  })

  it('returns to the legacy UI on request', () => {
    const authStore = useAuthStore()
    authStore.userId = 101
    const studentUi = useStudentUiMode()

    studentUi.enablePreview()
    studentUi.leavePreview()

    expect(studentUi.mode.value).toBe('legacy')
  })
})
