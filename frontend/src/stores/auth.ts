import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authAPI, type Membership } from '@/api/client'
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

  const isAdmin = computed(() => activeRole.value === 'ACADEMY_ADMIN')
  const isAssistant = computed(() => activeRole.value === 'ASSISTANT')
  const activeAcademy = computed(() =>
    memberships.value.find(m => m.academyId === activeAcademyId.value)
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
    return data
  }

  async function ensureActiveAcademy() {
    if (role.value !== 'TEACHER' || memberships.value.length === 0) return
    if (activeAcademyId.value) return

    const stored = localStorage.getItem(LAST_ACADEMY_KEY)
    const storedId = stored ? Number(stored) : null

    const target = (storedId && memberships.value.find(m => m.academyId === storedId))
      ? storedId
      : memberships.value[0].academyId

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
    // localStorage.lastAcademyId는 유지 (다음 로그인 기본값)
    // Clear stored biometric quick-login credential — logout is the explicit
    // "switch user / hand off device" signal.
    await clearCredential()
  }

  return {
    userId, name, role, memberships, activeAcademyId, activeRole,
    isAdmin, isAssistant, activeAcademy,
    loadCurrentUser, ensureActiveAcademy, switchAcademy, logout
  }
})
