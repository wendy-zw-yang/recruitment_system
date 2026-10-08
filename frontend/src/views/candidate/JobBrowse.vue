<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { jobApi } from '@/api/job'
import { dictApi } from '@/api/dict'
import { useAuthStore } from '@/stores/useAuthStore'
import JobListCard from '@/views/common/JobListCard.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const query = reactive({
  keyword: '',
  industryId: null,
  province: '',
  cityId: null,
  cityName: '',
  favoritedOnly: false,
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

/** 是否有任何筛选条件生效（用于显示「清除筛选」按钮） */
const hasActiveFilters = computed(() =>
  !!(query.keyword || query.cityName || query.industryId || query.province || query.cityId || query.favoritedOnly))

const provinceOptions = computed(() => {
  const set = new Set(cities.value.map((c) => c.province).filter(Boolean))
  return Array.from(set).sort((a, b) => a.localeCompare(b, 'zh-CN'))
})

const cityOptions = computed(() => {
  if (!query.province) return cities.value
  return cities.value.filter((c) => c.province === query.province)
})

/**
 * v0.5 用户反馈（修正版）：
 *  - 顶部搜索栏（关键词/城市/行业/省份/城市下拉）：仅点击「搜索」按钮才触发（避免输入即请求）
 *  - "只看收藏" checkbox + "最新/薪资" sort radio：**即时触发**（切完就生效，不需要再点搜索）
 *  - 分页切换：即时触发（@current-change）
 *  - 切换省份时清空城市下拉（保证省/市组合一致，不触发搜索）
 */
watch(() => query.province, (newVal, oldVal) => {
  if (newVal !== oldVal) query.cityId = null
})

watch(() => query.favoritedOnly, (newVal, oldVal) => {
  if (newVal === oldVal) return
  query.pageNum = 1
  fetchList()
})

watch(() => query.sort, (newVal, oldVal) => {
  if (newVal === oldVal) return
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
      cityName: query.cityName || undefined,
      favoritedOnly: query.favoritedOnly || undefined,
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

function clearAllFilters() {
  query.keyword = ''
  query.cityName = ''
  query.industryId = null
  query.province = ''
  query.cityId = null
  query.favoritedOnly = false
  query.sort = 'newest'
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
    // toggleFavorite 接口返回 {jobId, favorited} 对象，只取 favorited 布尔
    const res = await jobApi.toggleFavorite(job.id)
    const favorited = res?.favorited === true
    job.favorited = favorited
    ElMessage.success(favorited ? '已收藏' : '已取消收藏')
    // 如果当前是"只看收藏"模式且取消收藏 → 立即从列表移除
    if (query.favoritedOnly && !favorited) {
      records.value = records.value.filter(j => j.id !== job.id)
      total.value = Math.max(0, total.value - 1)
    }
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    favoriteBusyId.value = null
  }
}

onMounted(async () => {
  // 读取首页跳转过来的 query 参数（关键词 / 城市）
  const q = route.query
  if (q.keyword) query.keyword = String(q.keyword)
  if (q.cityName) query.cityName = String(q.cityName)
  if (q.favorited === 'true') query.favoritedOnly = true
  await Promise.all([fetchIndustries(), fetchCities()])
  fetchList()
})

/**
 * v0.7.3：从首页 / CandidateHome.vue "查看更多" 进入时带 query.recommended=true
 * → 拉取 AI 排序后的职位列表（pageSize=10），附带 aiScore 用于卡片展示。
 */
const isRecommendedMode = ref(false)

async function fetchRecommendedList() {
  loading.value = true
  try {
    const page = await jobApi.recommended({ pageNum: 1, pageSize: 10 })
    records.value = page.records || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

// 监听 query.recommended 变化，切换数据源
watch(() => route.query.recommended, async (val) => {
  isRecommendedMode.value = val === 'true' || val === true
  if (isRecommendedMode.value) {
    await fetchRecommendedList()
  } else if (route.path === '/jobs') {
    fetchList()
  }
}, { immediate: true })
</script>

<template>
  <div class="page-shell job-browse">
    <!-- v0.7.3：AI 推荐模式下显示标题条 -->
    <div v-if="isRecommendedMode" class="recommend-banner">
      <span class="recommend-banner__icon">🤖</span>
      <span class="recommend-banner__text">
        以下是根据你的求职偏好 + 简历 AI 智能匹配的职位
      </span>
    </div>
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
      <el-input
        v-model="query.cityName"
        placeholder="城市（如：北京）"
        size="large"
        clearable
        @keyup.enter="doSearch"
      />
      <el-select v-model="query.industryId" placeholder="行业" clearable size="large">
        <el-option v-for="i in industries" :key="i.id" :label="i.name" :value="i.id" />
      </el-select>
      <el-select v-model="query.province" placeholder="省份" clearable size="large" filterable>
        <el-option v-for="p in provinceOptions" :key="p" :label="p" :value="p" />
      </el-select>
      <el-select v-model="query.cityId" placeholder="城市（下拉）" clearable size="large" filterable :disabled="!query.province">
        <el-option v-for="c in cityOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-button type="primary" size="large" @click="doSearch">搜索</el-button>
    </div>

    <!-- 来自首页搜索的提示条已移除：v0.5 用户反馈"只要填写搜索内容并点击搜索即可"，不需要这条提示 -->

    <div class="result-meta">
      <div class="result-meta__left">
        <span class="count">共 {{ total }} 个职位</span>
        <span class="data-source">数据来自 HR 实时发布</span>
        <!-- v0.5 新增：只看收藏的筛选项 -->
        <el-checkbox
          v-model="query.favoritedOnly"
          size="small"
          class="fav-filter"
          :disabled="!auth.isLoggedIn"
        >
          只看收藏
        </el-checkbox>
        <!-- v0.5 用户反馈：搜索后提供"清除筛选"入口，避免误以为无路回未搜索列表 -->
        <el-button
          v-if="hasActiveFilters"
          text
          type="primary"
          size="small"
          class="clear-btn"
          @click="clearAllFilters"
        >
          ✕ 清除筛选
        </el-button>
      </div>
      <el-radio-group v-model="query.sort" size="small">
        <el-radio-button value="newest">最新</el-radio-button>
        <el-radio-button value="salary">薪资</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="loading" class="loading">加载中…</div>
    <div v-else-if="records.length === 0" class="empty-state">
      <div class="empty-state__icon">🔍</div>
      <h3 class="empty-state__title">
        {{ query.favoritedOnly ? '还没有收藏任何职位' : '暂无匹配的职位' }}
      </h3>
      <p class="empty-state__desc">
        {{ query.favoritedOnly ? '在职位列表点星标收藏感兴趣的岗位，会汇总到这里' : '试试调整搜索词或筛选条件' }}
      </p>
      <el-button v-if="query.favoritedOnly" type="primary" @click="query.favoritedOnly = false">查看全部职位</el-button>
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
.recommend-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  margin-bottom: 18px;
  border-radius: 12px;
  background: linear-gradient(90deg, #ede9fe 0%, #ddd6fe 100%);
  border: 1px solid #c4b5fd;
  color: #4c1d95;
  font-size: 13px;
  font-weight: 500;
}
.recommend-banner__icon {
  font-size: 18px;
  flex-shrink: 0;
}
.search-bar {
  display: grid;
  grid-template-columns: 2fr 1.2fr 1fr 1fr 1fr auto;
  gap: 12px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 12px;
  box-shadow: var(--shadow-soft);
  margin-bottom: 14px;
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
  flex-wrap: wrap;
  gap: 12px;
}
.result-meta__left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.count { font-size: 13px; color: var(--text-soft); }
.data-source { font-size: 12px; color: var(--text-muted); }
.fav-filter { font-size: 13px; }
.clear-btn { font-size: 12px; }
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
.empty-state__desc { font-size: 13px; color: var(--text-soft); margin-bottom: 12px; }
.pager { display: flex; justify-content: center; margin-top: 20px; }

@media (max-width: 1100px) {
  .search-bar {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
