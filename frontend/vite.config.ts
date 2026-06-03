import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  build: {
    // The Element Plus bundle dominates total size; splitting vendor + UI lib
    // into separate chunks lets the browser cache them across deploys and lets
    // route chunks stay small.
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks: id => {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('@element-plus/icons-vue')) return 'element-icons'
          if (id.includes('element-plus')) return 'element-plus'
          if (id.includes('@capacitor') || id.includes('@aparajita')) return 'capacitor'
          if (id.includes('vue') || id.includes('pinia') || id.includes('axios')) return 'vendor'
          return undefined
        },
      },
    },
  },
})
