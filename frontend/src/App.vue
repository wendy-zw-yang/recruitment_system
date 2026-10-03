<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'

const route = useRoute()
const useLayout = computed(() => !route.meta?.standalone)
</script>

<template>
  <AppLayout v-if="useLayout">
    <router-view />
  </AppLayout>
  <router-view v-else />
</template>

<style>
.page-shell {
  width: min(1200px, calc(100% - 48px));
  margin: 0 auto;
  padding: 96px 0 64px;
}
</style>
