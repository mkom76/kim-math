import { Preferences } from '@capacitor/preferences'

/**
 * Native credential storage for the student app's biometric quick-login.
 *
 * Backed by Capacitor `Preferences` — that is *not* hardware-keystore backed,
 * so this is NOT a place for secrets that would be catastrophic if leaked
 * from a rooted device. For a student-PIN flow whose threat model is "phone
 * left on a desk", it's acceptable. Upgrade path: swap implementation for a
 * Keychain/Keystore-backed plugin without touching callers.
 */

const KEY = 'student-credential.v1'

export interface StoredCredential {
  studentId: number
  pin: string
}

export async function saveCredential(c: StoredCredential): Promise<void> {
  await Preferences.set({ key: KEY, value: JSON.stringify(c) })
}

export async function loadCredential(): Promise<StoredCredential | null> {
  const { value } = await Preferences.get({ key: KEY })
  if (!value) return null
  try {
    const parsed = JSON.parse(value)
    if (typeof parsed?.studentId === 'number' && typeof parsed?.pin === 'string') {
      return parsed as StoredCredential
    }
    return null
  } catch {
    return null
  }
}

export async function clearCredential(): Promise<void> {
  await Preferences.remove({ key: KEY })
}

export async function hasCredential(): Promise<boolean> {
  return (await loadCredential()) !== null
}
