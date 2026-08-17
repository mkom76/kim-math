import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import {
  STUDENT_UI_MODE_KEY,
  STUDENT_UI_PREVIEW_SEEN_KEY,
  useStudentUiMode,
} from './useStudentUiMode'

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

  it('uses the academy V2 default when the student has not chosen a mode', () => {
    const authStore = useAuthStore()
    authStore.userId = 404
    authStore.studentUiDefaultMode = 'v2'

    expect(useStudentUiMode().mode.value).toBe('v2')
  })

  it('keeps an explicit legacy choice above the academy V2 default', () => {
    const authStore = useAuthStore()
    authStore.userId = 405
    authStore.studentUiDefaultMode = 'v2'
    const studentUi = useStudentUiMode()

    studentUi.leavePreview()

    expect(studentUi.mode.value).toBe('legacy')
    expect(studentUi.hasSeenPreview.value).toBe(true)
    expect(window.localStorage.getItem(`${STUDENT_UI_MODE_KEY}:405`)).toBe('legacy')
  })

  it('keeps an explicit V2 choice above a legacy academy default', () => {
    const authStore = useAuthStore()
    authStore.userId = 406
    authStore.studentUiDefaultMode = 'legacy'
    const studentUi = useStudentUiMode()

    studentUi.enablePreview()

    expect(studentUi.mode.value).toBe('v2')
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

  it('remembers that a student has experienced the preview', () => {
    const authStore = useAuthStore()
    authStore.userId = 303
    const studentUi = useStudentUiMode()

    expect(studentUi.hasSeenPreview.value).toBe(false)

    studentUi.enablePreview()
    studentUi.leavePreview()

    expect(studentUi.hasSeenPreview.value).toBe(true)
    expect(window.localStorage.getItem(`${STUDENT_UI_PREVIEW_SEEN_KEY}:303`)).toBe('true')
  })
})
