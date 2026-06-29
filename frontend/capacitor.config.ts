import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.kimmath.student.dev',
  appName: '킴매스 학생',
  webDir: 'dist',
  server: {
    // Dev builds run the Capacitor WebView at http://localhost. The API client
    // derives the backend origin from that host, so Android debug runs need
    // `adb reverse tcp:8080 tcp:8080` to map device localhost:8080 to the Mac's
    // backend. Production builds should set VITE_API_BASE_URL to the live API.
    // Keep the scheme as http so local cleartext API calls are not blocked as
    // mixed content during development.
    androidScheme: 'http',
    cleartext: true,
  },
};

export default config;
