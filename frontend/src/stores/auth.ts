import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authAPI, type Membership, type StudentClassMembership } from '@/api/client'
import { clearCredential } from '@/utils/credentialStore'
import { unregisterPushToken } from '@/utils/push'

const LAST_ACADEMY_KEY = 'lastAcademyId'

export const useAuthStore = defineStore('auth', () => {
  const userId = ref<number | null>(null)
  const name = ref<string>('')
  const role = ref<'STUDENT' | 'TEACHER' | null>(null)
  const memberships = ref<Membership[]>([])
  const activeAcademyId = ref<number | null>(null)
  const activeRole = ref<'TEACHER' | 'ACADEMY_ADMIN' | 'ASSISTANT' | null>(null)
  const studentUiDefaultMode = ref<'legacy' | 'v2'>('legacy')
  const studentClasses = ref<StudentClassMembership[]>([])
  const activeStudentClassId = ref<number | null>(null)
  const studentClassReadOnly = ref(false)

  const isAdmin = computed(() => activeRole.value === 'ACADEMY_ADMIN')
  const isAssistant = computed(() => activeRole.value === 'ASSISTANT')
  const activeAcademy = computed(() =>
    memberships.value.find(m => m.academyId === activeAcademyId.value)
  )
  const activeStudentClass = computed(() =>
    studentClasses.value.find(item => item.classId === activeStudentClassId.value)
  )

  async function loadCurrentUser() {
    const res = await authAPI.getCurrentUser()
    const data = res.data
    userId.value = data.userId ?? null
    name.value = data.name ?? ''
    role.value = data.role ?? null
    memberships.value = data.memberships ?? []
    activeAcademyId.value = data.activeAcademyId ?? null
    activeRole.value = data.activeRole ?? null
    studentUiDefaultMode.value = data.studentUiDefaultMode ?? 'legacy'
    studentClasses.value = data.studentClasses ?? []
    activeStudentClassId.value = data.activeStudentClassId ?? null
    studentClassReadOnly.value = data.studentClassReadOnly ?? false
    return data
  }

  async function ensureActiveAcademy() {
    if (role.value !== 'TEACHER' || memberships.value.length === 0) return
    if (activeAcademyId.value) return

    const stored = localStorage.getItem(LAST_ACADEMY_KEY)
    const storedId = stored ? Number(stored) : null

    const firstMembership = memberships.value[0]
    if (!firstMembership) return
    const target = (storedId && memberships.value.find(m => m.academyId === storedId))
      ? storedId
      : firstMembership.academyId

    await switchAcademy(target)
  }

  async function switchAcademy(academyId: number) {
    const res = await authAPI.switchAcademy(academyId)
    activeAcademyId.value = res.data.activeAcademyId ?? null
    activeRole.value = res.data.activeRole ?? null
    if (activeAcademyId.value) {
      localStorage.setItem(LAST_ACADEMY_KEY, String(activeAcademyId.value))
    }
  }

  async function switchStudentClass(classId: number) {
    const res = await authAPI.switchStudentClass(classId)
    studentClasses.value = res.data.studentClasses ?? studentClasses.value
    activeStudentClassId.value = res.data.activeStudentClassId ?? null
    studentClassReadOnly.value = res.data.studentClassReadOnly ?? false
  }

  async function logout() {
    // Unregister push token first while the session is still valid.
    await unregisterPushToken()
    await authAPI.logout()
    userId.value = null
    name.value = ''
    role.value = null
    memberships.value = []
    activeAcademyId.value = null
    activeRole.value = null
    studentUiDefaultMode.value = 'legacy'
    studentClasses.value = []
    activeStudentClassId.value = null
    studentClassReadOnly.value = false
    // localStorage.lastAcademyId는 유지 (다음 로그인 기본값)
    // Clear stored biometric quick-login credential — logout is the explicit
    // "switch user / hand off device" signal.
    await clearCredential()
  }

  return {
    userId, name, role, memberships, activeAcademyId, activeRole, studentUiDefaultMode,
    studentClasses, activeStudentClassId, studentClassReadOnly,
    isAdmin, isAssistant, activeAcademy, activeStudentClass,
    loadCurrentUser, ensureActiveAcademy, switchAcademy, switchStudentClass, logout
  }
})
