<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { jobApi } from '@/api/job'
import { useAuthStore } from '@/stores/useAuthStore'
import ApplyConfirmDialog from '@/views/candidate/ApplyConfirmDialog.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const job = ref(null)
const loading = ref(true)
const favoriteBusy = ref(false)
const applyDialogOpen = ref(false)

const salaryText = computed(() => {
  if (!job.value) return ''
  const { salaryMin, salaryMax } = job.value
  if (!salaryMin && !salaryMax) return '面议'
  if (salaryMin && salaryMax) return `${salaryMin}-${salaryMax}K / 月`
  return `${salaryMin || salaryMax}K / 月`
})

async function loadDetail() {
  const id = Number(route.params.id)
  loading.value = true
  try {
    job.value = await jobApi.detail(id)
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onFavorite() {
  if (!auth.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }
  favoriteBusy.value = true
  try {
    const favorited = await jobApi.toggleFavorite(job.value.id)
    job.value.favorited = favorited
    ElMessage.success(favorited ? '已收藏' : '已取消收藏')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    favoriteBusy.value = false
  }
}

function onApply() {
  if (!auth.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  if (!auth.isCandidate) {
    ElMessage.warning('仅候选人可投递')
    return
  }
  applyDialogOpen.value = true
}

/**
 * 顶部"← 返回职位列表"：跳到 /jobs 无 query 参数，清除所有搜索/筛选条件。
 * 不要用 router.back()：上一页面可能带 keyword/cityName/favorited 等参数，
 * 会回到搜索后的列表而非干净的职位列表入口。
 */
function goBackToList() {
  router.push({ path: '/jobs', query: {} })
}

onMounted(loadDetail)
</script>

<template>
  <div class="page-shell job-detail">
    <div class="back" @click="goBackToList">← 返回职位列表</div>

    <div v-if="loading" class="loading">加载中…</div>
    <div v-else-if="!job" class="loading">职位不存在或已下线</div>

    <template v-else>
      <div class="head-card">
        <div class="head-main">
          <h1 class="title">{{ job.title }}</h1>
          <p class="meta">
            <span>{{ job.companyName || '—' }}</span>
            <span class="dot">·</span>
            <span>{{ [job.province, job.cityName].filter(Boolean).join(' · ') || '不限城市' }}</span>
            <span v-if="job.industryName" class="dot">·</span>
            <span v-if="job.industryName">{{ job.industryName }}</span>
          </p>
          <p class="salary">{{ salaryText }}</p>
        </div>
        <div class="head-actions">
          <el-button
            :type="job.favorited ? 'danger' : 'default'"
            :icon="job.favorited ? 'StarFilled' : 'Star'"
            round
            :loading="favoriteBusy"
            @click="onFavorite"
          >
            {{ job.favorited ? '已收藏' : '收藏' }}
          </el-button>
          <el-button type="primary" round size="large" @click="onApply">立即投递</el-button>
        </div>
      </div>

      <div class="section">
        <h2 class="section-title">岗位职责</h2>
        <pre class="content">{{ job.description }}</pre>
      </div>

      <div class="section">
        <h2 class="section-title">任职要求</h2>
        <pre class="content">{{ job.requirements }}</pre>
      </div>

      <div class="section">
        <h2 class="section-title">公司信息</h2>
        <p class="content company-line">{{ job.companyName || '—' }}</p>
      </div>
    </template>

    <ApplyConfirmDialog v-if="job" v-model="applyDialogOpen" :job="job" />
  </div>
</template>

<style scoped>
.job-detail { padding-top: 96px; }
.back {
  font-size: 13px;
  color: var(--text-soft);
  cursor: pointer;
  margin-bottom: 14px;
  display: inline-block;
}
.back:hover { color: var(--primary); }
.head-card {
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
  box-shadow: var(--shadow-soft);
}
.head-main { flex: 1; min-width: 0; }
.title { font-size: 26px; font-weight: 700; color: var(--text); margin-bottom: 10px; }
.meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 13px;
  color: var(--text-soft);
  margin-bottom: 10px;
}
.meta .dot { color: var(--text-muted); }
.salary { font-size: 22px; font-weight: 700; color: var(--danger); }
.head-actions { display: flex; gap: 12px; flex-shrink: 0; }
.section {
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 24px 28px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-soft);
}
.section-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--line);
}
.content {
  font-family: inherit;
  font-size: 14px;
  color: var(--text);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}
.company-line { color: var(--text); font-weight: 500; }
.loading {
  padding: 56px 24px;
  text-align: center;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  color: var(--text-soft);
}
</style>
