import { Preferences } from '@capacitor/preferences'

/**
 * Legacy cleanup for the old biometric quick-login credential.
 * PIN storage is disabled until it can be backed by Android Keystore /
 * iOS Keychain instead of Capacitor Preferences.
 */

const KEY = 'student-credential.v1'

export interface StoredCredential {
  studentId: number
  pin: string
}

export async function saveCredential(_c: StoredCredential): Promise<void> {
  await clearCredential()
}

export async function loadCredential(): Promise<StoredCredential | null> {
  await clearCredential()
  return null
}

export async function clearCredential(): Promise<void> {
  await Preferences.remove({ key: KEY })
}

export async function hasCredential(): Promise<boolean> {
  await clearCredential()
  return false
}
