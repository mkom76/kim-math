import { BiometricAuth, BiometryType } from '@aparajita/capacitor-biometric-auth'
import { isNativeApp } from './platform'

/**
 * True when the device supports + has enrolled biometry (Face ID / Touch ID /
 * 지문). On web or unsupported devices returns false.
 */
export async function isBiometricAvailable(): Promise<boolean> {
  if (!isNativeApp()) return false
  try {
    const info = await BiometricAuth.checkBiometry()
    return info.isAvailable && info.biometryType !== BiometryType.none
  } catch {
    return false
  }
}

/**
 * Show the system biometric prompt. Resolves true on success, false on user
 * cancel, dismiss, or any failure (the caller can fall back to PIN login).
 *
 * The reason string is shown by iOS Face ID / Android BiometricPrompt as the
 * subtitle.
 */
export async function verifyBiometric(reason = '본인 확인이 필요합니다'): Promise<boolean> {
  if (!isNativeApp()) return false
  try {
    await BiometricAuth.authenticate({
      reason,
      cancelTitle: '취소',
      iosFallbackTitle: 'PIN으로 로그인',
      androidTitle: '학생 로그인',
      androidSubtitle: reason,
      androidConfirmationRequired: false,
    })
    return true
  } catch {
    return false
  }
}
