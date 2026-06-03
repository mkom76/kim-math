import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.kimmath.student.dev',
  appName: '킴매스 학생',
  webDir: 'dist',
  server: {
    // The native WebView treats `localhost` as the device itself; use Android's
    // emulator host alias (10.0.2.2) so dev API calls reach the host machine.
    // Production builds should override via `VITE_API_BASE_URL` at build time
    // to point at the live API host instead.
    // Serve the app over http://localhost (not https) so dev API calls to the
    // cleartext host alias http://10.0.2.2:8080 aren't blocked as mixed content.
    // An https page calling an http endpoint is blocked by Chromium regardless
    // of `cleartext`; matching the schemes (http -> http) avoids that.
    androidScheme: 'http',
    cleartext: true,
  },
};

export default config;
