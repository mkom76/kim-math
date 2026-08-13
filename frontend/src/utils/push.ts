import { PushNotifications } from '@capacitor/push-notifications'
import { Preferences } from '@capacitor/preferences'
import type { Router } from 'vue-router'
import { isNativeApp, platformName } from './platform'
import { deviceAPI } from '@/api/client'

const TOKEN_STORAGE_KEY = 'push-device-token'

let listenersInitialized = false
let lastToken: string | null = null

/**
 * Bootstrap push notifications. Idempotent — safe to call multiple times
 * (e.g. on every login). On non-native platforms it's a no-op so callers
 * can invoke unconditionally.
 *
 * @param router  used to deep-link when the user taps a notification
 */
export async function initPushNotifications(router: Router): Promise<void> {
  if (!isNativeApp()) return

  // PushNotifications.register() requires google-services.json in the native
  // build. Keep the feature behind an explicit build flag so a release that has
  // not been provisioned in Firebase never calls the native registration API.
  if (import.meta.env.DEV || import.meta.env.VITE_PUSH_ENABLED !== 'true') return

  if (!listenersInitialized) {
    // Listeners must be added BEFORE requestPermissions on Android 13+ to catch
    // the registration event reliably.
    await PushNotifications.addListener('registration', async ({ value }) => {
      lastToken = value
      await Preferences.set({ key: TOKEN_STORAGE_KEY, value })
      const platform = platformName()
      if (platform === 'web') return // unreachable in practice (guarded above)
      try {
        await deviceAPI.register({ token: value, platform })
      } catch {
        // register() is called on every login, so a transient failure is retried.
      }
    })

    await PushNotifications.addListener('registrationError', err => {
      // eslint-disable-next-line no-console
      console.warn('[push] registration failed:', err)
    })

    await PushNotifications.addListener('pushNotificationReceived', notification => {
      // Native presentationOptions displays the banner; retain a listener so
      // foreground delivery is observable in device logs as well.
      // eslint-disable-next-line no-console
      console.info('[push] notification received:', notification.id)
      window.dispatchEvent(new CustomEvent('student-notifications-changed'))
    })

    await PushNotifications.addListener('pushNotificationActionPerformed', ({ notification }) => {
      // The server can include a `path` in the data payload to deep-link the
      // notification tap (e.g. /student/tests/123). Falls back to dashboard.
      const path = (notification.data as Record<string, string>)?.path
      if (path && typeof path === 'string') {
        router.push(path).catch(() => router.push('/student/dashboard'))
      }
    })

    listenersInitialized = true
  }

  const perm = await PushNotifications.checkPermissions()
  let granted = perm.receive
  if (granted !== 'granted') {
    const req = await PushNotifications.requestPermissions()
    granted = req.receive
  }
  if (granted === 'granted') {
    // Call on every login. FCM re-emits the current token, which retries the
    // backend upsert if a previous registration failed transiently.
    await PushNotifications.register()
  }
}

/** Tell the backend to forget this device. Call on explicit logout. */
export async function unregisterPushToken(): Promise<void> {
  if (!isNativeApp()) return

  const stored = await Preferences.get({ key: TOKEN_STORAGE_KEY })
  const token = lastToken ?? stored.value
  if (token) {
    try {
      await deviceAPI.unregister(token)
    } catch {
      /* best effort; native unregister below invalidates the token in FCM */
    }
  }

  try {
    await PushNotifications.unregister()
  } catch {
    /* best effort */
  }

  lastToken = null
  await Preferences.remove({ key: TOKEN_STORAGE_KEY })
}
