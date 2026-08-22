<script setup lang="ts">
defineProps<{
  eyebrow: string
  title: string
  subtitle?: string
  titleId?: string
}>()
</script>

<template>
  <header class="student-page__header">
    <div>
      <p class="student-page__eyebrow">{{ eyebrow }}</p>
      <h1 :id="titleId" class="student-page__title">{{ title }}</h1>
      <p v-if="subtitle" class="student-page__subtitle">{{ subtitle }}</p>
      <slot name="meta" />
    </div>
    <slot name="action" />
  </header>
</template>
