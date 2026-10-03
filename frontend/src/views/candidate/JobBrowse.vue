<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { jobApi } from '@/api/job'
import { dictApi } from '@/api/dict'
import { useAuthStore } from '@/stores/useAuthStore'
import JobListCard from '@/views/common/JobListCard.vue'

const router = useRouter()
const auth = useAuthStore()

const query = reactive({
  keyword: '',
  industryId: null,
  province: '',
  cityId: null,
  sort: 'newest',
  pageNum: 1,
  pageSize: 10
})

const total = ref(0)
const records = ref([])
const loading = ref(false)
const industries = ref([])
const cities = ref([])
const favoriteBusyId = ref(null)

const provinceOptions = computed(() => {
  const set = new Set(cities.value.map((c) => c.province).filter(Boolean))
  return Array.from(set).sort((a, b) => a.localeCompare(b, 'zh-CN'))
})

const cityOptions = computed(() => {
  if (!query.province) return cities.value
  return cities.value.filter((c) => c.province === query.province)
})

watch(() => query.province, () => {
  query.cityId = null
  query.pageNum = 1
  fetchList()
})

async function fetchIndustries() {
  if (industries.value.length === 0) industries.value = (await dictApi.industries()) || []
}
async function fetchCities() {
  if (cities.value.length === 0) cities.value = (await dictApi.cities()) || []
}

async function fetchList() {
  loading.value = true
  try {
    const page = await jobApi.list({
      keyword: query.keyword || undefined,
      industryId: query.industryId || undefined,
      province: query.province || undefined,
      cityId: query.cityId || undefined,
      sort: query.sort || 'newest',
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    records.value = page.records || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

function doSearch() {
  query.pageNum = 1
  fetchList()
}

function goDetail(job) {
  router.push(`/jobs/${job.id}`)
}

async function onFavorite(job) {
  if (!auth.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }
  favoriteBusyId.value = job.id
  try {
    const favorited = await jobApi.toggleFavorite(job.id)
    job.favorited = favorited
    ElMessage.success(favorited ? '已收藏' : '已取消收藏')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    favoriteBusyId.value = null
  }
}

onMounted(async () => {
  await Promise.all([fetchIndustries(), fetchCities()])
  fetchList()
})

watch(
  () => query.sort,
  () => {
    query.pageNum = 1
    fetchList()
  }
)
</script>

<template>
  <div class="page-shell job-browse">
    <div class="search-bar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索职位名称或关键词"
        size="large"
        clearable
        @keyup.enter="doSearch"
      >
        <template #prefix><span class="search-icon">⌕</span></template>
      </el-input>
      <el-select v-model="query.industryId" placeholder="行业" clearable size="large" @change="doSearch">
        <el-option v-for="i in industries" :key="i.id" :label="i.name" :value="i.id" />
      </el-select>
      <el-select v-model="query.province" placeholder="省份" clearable size="large" filterable>
        <el-option v-for="p in provinceOptions" :key="p" :label="p" :value="p" />
      </el-select>
      <el-select v-model="query.cityId" placeholder="城市" clearable size="large" filterable :disabled="!query.province">
        <el-option v-for="c in cityOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-button type="primary" size="large" @click="doSearch">搜索</el-button>
    </div>

    <div class="result-meta">
      <span class="count">共 {{ total }} 个职位</span>
      <el-radio-group v-model="query.sort" size="small">
        <el-radio-button value="newest">最新</el-radio-button>
        <el-radio-button value="salary">薪资</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="loading" class="loading">加载中…</div>
    <div v-else-if="records.length === 0" class="empty-state">
      <div class="empty-state__icon">🔍</div>
      <h3 class="empty-state__title">暂无匹配的职位</h3>
      <p class="empty-state__desc">试试调整搜索词或筛选条件</p>
    </div>
    <div v-else class="job-list">
      <JobListCard
        v-for="job in records"
        :key="job.id"
        :job="job"
        :show-actions="true"
        :busy="favoriteBusyId === job.id"
        @favorite="onFavorite"
        @detail="goDetail"
      />
    </div>

    <el-pagination
      v-if="total > query.pageSize"
      class="pager"
      :current-page="query.pageNum"
      :page-size="query.pageSize"
      :total="total"
      layout="prev, pager, next"
      background
      @current-change="(p) => { query.pageNum = p; fetchList() }"
    />
  </div>
</template>

<style scoped>
.job-browse { padding-top: 96px; }
.search-bar {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr auto;
  gap: 12px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 12px;
  box-shadow: var(--shadow-soft);
  margin-bottom: 18px;
}
.search-bar :deep(.el-input__wrapper) {
  box-shadow: none;
  border-radius: 10px;
  background: var(--bg);
}
.search-icon { font-size: 18px; color: var(--text-muted); margin-left: 8px; }
.result-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.count { font-size: 13px; color: var(--text-soft); }
.job-list { display: flex; flex-direction: column; gap: 12px; }
.loading, .empty-state {
  padding: 56px 24px;
  text-align: center;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
}
.empty-state__icon { font-size: 48px; opacity: 0.55; margin-bottom: 8px; }
.empty-state__title { font-size: 16px; font-weight: 600; margin-bottom: 6px; color: var(--text); }
.empty-state__desc { font-size: 13px; color: var(--text-soft); }
.pager { display: flex; justify-content: center; margin-top: 20px; }
</style>
