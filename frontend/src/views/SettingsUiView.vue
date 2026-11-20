<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const ready = ref(authStore.role !== null)

const teacherSettings = defineAsyncComponent(() => import('@/views/SettingsView.vue'))
const studentSettings = defineAsyncComponent(
  () => import('@/views/student-v2/StudentSettingsView.vue'),
)
const activeView = computed(() =>
  authStore.role === 'STUDENT' ? studentSettings : teacherSettings,
)

onMounted(async () => {
  if (authStore.role === null) {
    try {
      await authStore.loadCurrentUser()
    } finally {
      ready.value = true
    }
    return
  }
  ready.value = true
})
</script>

<template>
  <component :is="activeView" v-if="ready" />
</template>
