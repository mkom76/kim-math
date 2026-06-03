import { Capacitor } from '@capacitor/core'

/**
 * True when the bundle is running inside the Capacitor native shell
 * (iOS or Android). On the regular browser this returns false.
 *
 * Use this to:
 *  - lock the router to /student/* routes in the student app
 *  - default the LoginView to the student tab (and hide the teacher tab)
 *  - gate any native-only features (push notifications, biometric, etc.)
 */
export function isNativeApp(): boolean {
  return Capacitor.isNativePlatform()
}

/** 'ios' | 'android' | 'web' */
export function platformName(): 'ios' | 'android' | 'web' {
  const p = Capacitor.getPlatform()
  return p === 'ios' || p === 'android' ? p : 'web'
}
