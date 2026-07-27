import { PushNotifications } from '@capacitor/push-notifications'
import type { Router } from 'vue-router'
import { isNativeApp, platformName } from './platform'
import { deviceAPI } from '@/api/client'

let initialized = false
let lastToken: string | null = null

/**
 * Bootstrap push notifications. Idempotent — safe to call multiple times
 * (e.g. on every login). On non-native platforms it's a no-op so callers
 * can invoke unconditionally.
 *
 * @param router  used to deep-link when the user taps a notification
 */
export async function initPushNotifications(router: Router): Promise<void> {
  if (!isNativeApp() || initialized) return

  // PushNotifications.register() requires google-services.json in the native
  // build. Keep the feature behind an explicit build flag so a release that has
  // not been provisioned in Firebase never calls the native registration API.
  if (import.meta.env.DEV || import.meta.env.VITE_PUSH_ENABLED !== 'true') return

  // Listeners must be added BEFORE requestPermissions on Android 13+ to catch
  // the registration event reliably.
  await PushNotifications.addListener('registration', async ({ value }) => {
    lastToken = value
    const platform = platformName()
    if (platform === 'web') return // unreachable in practice (guarded above)
    try {
      await deviceAPI.register({ token: value, platform })
    } catch {
      // Backend rejected — user may not be logged in yet; we'll retry on next login.
    }
  })

  await PushNotifications.addListener('registrationError', err => {
    // eslint-disable-next-line no-console
    console.warn('[push] registration failed:', err)
  })

  await PushNotifications.addListener('pushNotificationActionPerformed', ({ notification }) => {
    // The server can include a `path` in the data payload to deep-link the
    // notification tap (e.g. /student/tests/123). Falls back to dashboard.
    const path = (notification.data as Record<string, string>)?.path
    if (path && typeof path === 'string') {
      router.push(path).catch(() => router.push('/student/dashboard'))
    }
  })

  const perm = await PushNotifications.checkPermissions()
  let granted = perm.receive
  if (granted !== 'granted') {
    const req = await PushNotifications.requestPermissions()
    granted = req.receive
  }
  if (granted === 'granted') {
    await PushNotifications.register()
  }

  initialized = true
}

/** Tell the backend to forget this device. Call on explicit logout. */
export async function unregisterPushToken(): Promise<void> {
  if (!isNativeApp() || !lastToken) return
  try {
    await deviceAPI.unregister(lastToken)
  } catch {
    /* best effort */
  }
  lastToken = null
}
