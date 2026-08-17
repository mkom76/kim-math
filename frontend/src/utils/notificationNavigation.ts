import type { Router } from 'vue-router'

export const STUDENT_NOTIFICATION_FALLBACK = '/student/dashboard'

export function resolveStudentNotificationTarget(router: Router, path?: string): string {
  if (!path?.startsWith('/student/')) return STUDENT_NOTIFICATION_FALLBACK

  try {
    const resolved = router.resolve(path)
    return resolved.matched.length ? path : STUDENT_NOTIFICATION_FALLBACK
  } catch {
    return STUDENT_NOTIFICATION_FALLBACK
  }
}

export async function navigateToStudentNotification(router: Router, path?: string): Promise<void> {
  const target = resolveStudentNotificationTarget(router, path)
  try {
    await router.push(target)
  } catch {
    if (target !== STUDENT_NOTIFICATION_FALLBACK) {
      await router.push(STUDENT_NOTIFICATION_FALLBACK)
    }
  }
}
