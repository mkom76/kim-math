import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Router } from 'vue-router'

const { markReadBySourceKey } = vi.hoisted(() => ({
  markReadBySourceKey: vi.fn(),
}))

vi.mock('@/api/client', () => ({
  deviceAPI: { register: vi.fn(), unregister: vi.fn() },
  studentNotificationAPI: { markReadBySourceKey },
}))

vi.mock('@capacitor/push-notifications', () => ({
  PushNotifications: {},
}))

vi.mock('@capacitor/preferences', () => ({
  Preferences: {},
}))

vi.mock('./platform', () => ({
  isNativeApp: () => true,
  platformName: () => 'android',
}))

import { handlePushNotificationAction } from './push'

function routerFor(validPaths: string[]) {
  return {
    resolve: vi.fn((path: string) => ({ matched: validPaths.includes(path) ? [{}] : [] })),
    push: vi.fn().mockResolvedValue(undefined),
  } as unknown as Router
}

describe('native push notification action', () => {
  beforeEach(() => {
    markReadBySourceKey.mockReset()
    markReadBySourceKey.mockResolvedValue({ data: {} })
  })

  it('marks the inbox item read and opens the supplied student route', async () => {
    const router = routerFor(['/student/daily-feedback'])

    await handlePushNotificationAction(router, {
      path: '/student/daily-feedback',
      sourceKey: 'lesson-feedback:55',
    })

    expect(markReadBySourceKey).toHaveBeenCalledWith('lesson-feedback:55')
    expect(router.push).toHaveBeenCalledWith('/student/daily-feedback')
  })

  it('continues navigation when read tracking fails', async () => {
    markReadBySourceKey.mockRejectedValueOnce(new Error('network error'))
    const router = routerFor(['/student/daily-feedback'])

    await handlePushNotificationAction(router, {
      path: '/student/daily-feedback',
      sourceKey: 'lesson-feedback:55',
    })

    expect(router.push).toHaveBeenCalledWith('/student/daily-feedback')
  })

  it('uses the dashboard when the push contains an invalid route', async () => {
    const router = routerFor([])

    await handlePushNotificationAction(router, { path: '/teacher/settings' })

    expect(router.push).toHaveBeenCalledWith('/student/dashboard')
  })

  it('falls back to the dashboard when opening a valid target fails', async () => {
    const router = routerFor(['/student/daily-feedback'])
    vi.mocked(router.push)
      .mockRejectedValueOnce(new Error('navigation failed'))
      .mockResolvedValueOnce(undefined)

    await handlePushNotificationAction(router, { path: '/student/daily-feedback' })

    expect(router.push).toHaveBeenNthCalledWith(1, '/student/daily-feedback')
    expect(router.push).toHaveBeenNthCalledWith(2, '/student/dashboard')
  })
})
