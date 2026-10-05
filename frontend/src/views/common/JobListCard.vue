<script setup>
import { computed } from 'vue'

const props = defineProps({
  job: { type: Object, required: true },
  showActions: { type: Boolean, default: false },
  busy: { type: Boolean, default: false }
})

const emit = defineEmits(['favorite', 'detail'])

const salaryText = computed(() => {
  const { salaryMin, salaryMax } = props.job
  if (!salaryMin && !salaryMax) return '面议'
  if (salaryMin && salaryMax) return `${salaryMin}-${salaryMax}K`
  return `${salaryMin || salaryMax}K`
})

function onFavorite(e) {
  e.stopPropagation()
  emit('favorite', props.job)
}

function onDetail() {
  emit('detail', props.job)
}
</script>

<template>
  <!--
    v0.5：去除整卡片 @click 跳转，避免用户误触或与"返回/清除筛选"等行为冲突。
    现在只有"查看详情"按钮是显式跳转入口；收藏星标单独处理；卡片其它区域悬停仅做高亮。
  -->
  <div class="job-card" :class="{ 'job-card--favorited': job.favorited }">
    <div class="job-card__main">
      <div class="title-row">
        <h3 class="title">{{ job.title }}</h3>
        <span class="salary">{{ salaryText }}</span>
      </div>
      <p class="company">{{ job.companyName || '—' }} · {{ [job.province, job.cityName].filter(Boolean).join(' · ') || '不限城市' }}</p>
      <p v-if="job.description" class="snippet">{{ job.description.slice(0, 80) }}{{ job.description.length > 80 ? '…' : '' }}</p>
    </div>
    <div class="job-card__side">
      <el-button
        v-if="showActions"
        :type="job.favorited ? 'danger' : 'default'"
        :icon="job.favorited ? 'StarFilled' : 'Star'"
        circle
        :loading="busy"
        @click="onFavorite"
      />
      <el-button type="primary" plain size="small" @click.stop="onDetail">查看详情</el-button>
    </div>
  </div>
</template>

<style scoped>
.job-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px;
  border-radius: 14px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.job-card:hover {
  border-color: var(--primary-tint-strong);
  box-shadow: var(--shadow-tab);
}
.job-card--favorited {
  border-color: var(--warning);
  background: linear-gradient(180deg, #fffaf0 0%, var(--panel-strong) 100%);
}
.title-row {
  display: flex;
  align-items: baseline;
  gap: 14px;
  margin-bottom: 4px;
}
.title { font-size: 16px; font-weight: 600; color: var(--text); }
.salary { font-size: 18px; font-weight: 700; color: var(--danger); }
.company { font-size: 13px; color: var(--text-soft); margin-bottom: 4px; }
.snippet { font-size: 12px; color: var(--text-muted); line-height: 1.5; max-width: 720px; }
.job-card__side { display: flex; align-items: center; gap: 8px; }
</style>
