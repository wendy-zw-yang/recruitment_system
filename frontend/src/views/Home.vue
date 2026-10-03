<script setup>
import { computed } from 'vue'
import { useAuthStore } from '@/stores/useAuthStore'
import CandidateHome from '@/views/candidate/CandidateHome.vue'
import HrHome from '@/views/hr/HrHome.vue'
import AdminHome from '@/views/admin/AdminHome.vue'

const auth = useAuthStore()

const homeComponent = computed(() => {
  if (auth.isHR) return HrHome
  if (auth.isAdmin) return AdminHome
  return CandidateHome
})
</script>

<template>
  <component :is="homeComponent" />
</template>
