<script setup>
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/useAuthStore'

const route = useRoute()
const auth = useAuthStore()
</script>

<template>
  <el-result icon="info" title="无权访问">
    <template #sub-title>
      当前角色：{{ auth.role || '未登录' }}，目标页要求：{{ route.meta?.roles?.join(' / ') }}
    </template>
    <template #extra>
      <el-button type="primary" @click="$router.push('/home')">返回首页</el-button>
    </template>
  </el-result>
</template>
