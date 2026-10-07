<script setup>
import { onMounted, ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApplicationStore } from '@/stores/useApplicationStore'
import { jobApi } from '@/api/job'
import JobListCard from '@/views/common/JobListCard.vue'

const router = useRouter()
const route = useRoute()
const applicationStore = useApplicationStore()

const searchKeyword = ref('')
const searchLocation = ref('')

const hotCities = ['北京', '上海', '广州', '深圳', '杭州', '成都', '南京', '武汉']

/**
 * 候选人首页 quick action 简化为 2 项：
 * 「简历」与「消息」已通过顶部 bar 始终可达，无需在首页重复展示。
 * 留下的 2 个聚焦「投递工作流」核心 CTA：发现岗位 + 跟踪投递。
 */
const quickActions = [
  { icon: '🔍', title: '浏览职位', desc: '发现匹配的机会', to: '/jobs' },
  { icon: '📮', title: '我的投递', desc: '查看投递进度与回复', to: '/applications/mine' }
]

/**
 * "我的投递（X 条）" 提示。
 * 优先用 store.total（准确），fallback 到 records 长度。
 */
const myApplicationsTotal = computed(() =>
  applicationStore.myApplicationsTotal || applicationStore.myApplications.length)

/**
 * 按状态统计。
 *
 * v0.5 用户反馈："面试邀请" 仅表示 HR 主动发起面试邀请（INTERVIEWING）；
 * OFFERED 是面试通过后发 offer 的状态，单独维度（这里不展开计数）。
 *
 * 注：「面试邀请」原误把 OFFERED 计入，导致 HR 把状态推到 OFFERED 后仍显示「面试邀请：1」，
 * 语义不符。现严格按 INTERVIEWING 计数。
 *
 * 返回值仅供「我的投递」section 顶部 hint 使用，不展示为大卡片（用户要求"不要那么显眼"）。
 */
const stats = computed(() => {
  const apps = applicationStore.myApplications
  return {
    total: myApplicationsTotal.value,
    pendingReview: apps.filter(a => a.status === 'PENDING_REVIEW').length,
    interviewing: apps.filter(a => a.status === 'INTERVIEWING').length,
    withdrawn: apps.filter(a => a.status === 'WITHDRAWN').length
  }
})

/**
 * 最近 3 条投递（按状态时间排序在 store fetch 时已是 appliedAt DESC）。
 * 若没数据，section 显示空状态。
 */
const recentApplications = computed(() => applicationStore.myApplications.slice(0, 3))

/** v0.7.3 推荐职位（按候选人偏好 4 维度打分排序；未设偏好退化为最新发布 5 条） */
const recommendedJobs = ref([])
const recommendedTotal = ref(0)
/** 兜底列表：偏好过滤无结果时显示最新发布 5 条 */
const fallbackJobs = ref([])
const jobsLoading = ref(false)

async function loadRecommended() {
  jobsLoading.value = true
  fallbackJobs.value = []
  try {
    const page = await jobApi.recommended({ pageNum: 1, pageSize: 5 })
    recommendedJobs.value = page?.records || []
    recommendedTotal.value = page?.total || 0
    // v0.7.3.1：偏好过滤无结果时拉取最新发布 5 条作为兜底
    if (recommendedJobs.value.length === 0) {
      try {
        const fallback = await jobApi.list({ sort: 'newest', pageNum: 1, pageSize: 5 })
        fallbackJobs.value = fallback?.records || []
      } catch (e) {
        console.warn('CandidateHome fallback jobs load failed:', e)
      }
    }
  } catch (e) {
    console.warn('CandidateHome recommended jobs load failed:', e)
  } finally {
    jobsLoading.value = false
  }
}

function goJobDetail(job) {
  router.push(`/jobs/${job.id}`)
}

const STATUS_LABEL = {
  PENDING_REVIEW: '待 HR 查看',
  RESUME_PASSED: '简历通过',
  INTERVIEWING: '面试中',
  OFFERED: '已发 Offer',
  HIRED: '已入职',
  REJECTED: '已拒绝',
  WITHDRAWN: '已撤回'
}
function statusLabel(s) { return STATUS_LABEL[s] || s }

function doSearch() {
  // 跳职位列表并带上搜索词 + 城市（JobBrowse onMounted 会读这两个参数初始化筛选）
  const q = {}
  if (searchKeyword.value?.trim()) q.keyword = searchKeyword.value.trim()
  if (searchLocation.value?.trim()) q.cityName = searchLocation.value.trim()
  router.push({ path: '/jobs', query: q })
}

function goDetail(item) {
  router.push(`/applications/${item.id}`)
}

async function refreshData() {
  // 静默失败（未登录 / 网络错误都不影响首页渲染）
  try {
    await Promise.all([
      applicationStore.fetchMine(1, 100),
      loadRecommended()
    ])
  } catch (e) {
    // ignore
  }
}

onMounted(refreshData)

/**
 * v0.5 修复：候选人首页 stats 不同步。
 *
 * 原因：Vue Router 在跳转回 /home 时若命中同一个路由组件，onMounted 不会重跑，
 * 导致 stats 仍是上次加载时的快照。
 *
 * 修复：监听 route.path，进入 /home 时强制刷新数据（包括"已投递/待 HR 查看/面试邀请/已撤回"统计）。
 */
watch(
  () => route.path,
  (newPath) => {
    if (newPath === '/home') refreshData()
  }
)
</script>

<template>
  <div class="page-shell home candidate-home">
    <section class="hero">
      <div class="hero__bg"></div>
      <div class="hero__inner">
        <h1 class="hero__title">发现更适合你的下一份工作</h1>
        <p class="hero__subtitle">覆盖互联网、金融、制造业等热门行业，每日更新上万优质岗位</p>
        <div class="search-bar">
          <el-input v-model="searchKeyword" placeholder="搜索职位、公司或关键词" size="large" class="search-bar__input">
            <template #prefix><span class="search-icon">⌕</span></template>
          </el-input>
          <el-input v-model="searchLocation" placeholder="城市" size="large" class="search-bar__city" />
          <el-button type="primary" size="large" class="search-bar__btn" @click="doSearch">搜索</el-button>
        </div>
        <div class="hot-cities">
          <span class="hot-cities__label">热门城市：</span>
          <a v-for="c in hotCities" :key="c" class="hot-cities__item" @click="searchLocation = c">{{ c }}</a>
        </div>
      </div>
    </section>

    <section class="quick-actions">
      <div class="card-grid">
        <div v-for="(item, idx) in quickActions" :key="idx" class="action-card" @click="router.push(item.to)">
          <div class="action-card__icon">{{ item.icon }}</div>
          <div class="action-card__body">
            <h3>{{ item.title }}</h3>
            <p>{{ item.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- v0.5：移除独立的 stats 大卡片块（用户原话："不要那么显眼"）。
         状态计数改嵌入下方「我的投递」section 顶部 hint，仅文字展示 -->

    <section class="panel recommended-panel">
      <div class="panel-head">
        <div class="panel-title">
          <h2>推荐职位</h2>
          <span class="hint">按你的偏好智能筛选 · 来自 HR 发布</span>
        </div>
        <el-button text type="primary" @click="router.push('/jobs')">查看更多 →</el-button>
      </div>

      <div v-if="jobsLoading" class="loading">加载中…</div>

      <template v-else>
        <!-- 无匹配但有兜底：显示提示 + 推荐最新发布 -->
        <div v-if="recommendedJobs.length === 0 && fallbackJobs.length > 0">
          <div class="empty-state">
            <div class="empty-state__icon">🔍</div>
            <h3 class="empty-state__title">未找到符合您偏好的职位</h3>
            <p class="empty-state__desc">下面是平台最新在招岗位，你可以先看看，也可以去<a class="link" @click="router.push('/profile?tab=preference')">调整偏好</a>后刷新</p>
          </div>
          <h4 class="fallback-title">最新在线岗位</h4>
          <div class="job-list">
            <JobListCard
              v-for="job in fallbackJobs"
              :key="job.id"
              :job="job"
              :show-actions="false"
              @detail="goJobDetail"
            />
          </div>
        </div>

        <!-- 无匹配且无兜底 -->
        <div v-else-if="recommendedJobs.length === 0" class="empty-state">
          <div class="empty-state__icon">🔍</div>
          <h3 class="empty-state__title">还没有在线职位</h3>
          <p class="empty-state__desc">HR 暂未发布任何职位；你可以先去<a class="link" @click="router.push('/resume')">完善简历</a>，新职位上线后会优先看到</p>
        </div>

        <!-- 有匹配 -->
        <div v-else class="job-list">
          <JobListCard
            v-for="job in recommendedJobs"
            :key="job.id"
            :job="job"
            :show-actions="false"
            @detail="goJobDetail"
          />
        </div>
      </template>

      <!-- v0.7.3：未设偏好时引导去设置偏好 -->
      <div v-if="!jobsLoading && recommendedTotal >= 5" class="set-pref-hint">
        <span>想要更精准的推荐？</span>
        <a class="link" @click="router.push('/profile?tab=preference')">设置求职偏好 →</a>
      </div>
    </section>

    <section class="panel applications-panel">
      <div class="panel-head">
        <div class="panel-title">
          <h2>我的投递</h2>
          <span class="hint">
            共 {{ stats.total }} 条 ·
            待 HR 查看 <strong>{{ stats.pendingReview }}</strong> ·
            面试邀请 <strong>{{ stats.interviewing }}</strong> ·
            已撤回 <strong>{{ stats.withdrawn }}</strong>
          </span>
        </div>
        <el-button text type="primary" @click="router.push('/applications/mine')">查看全部 →</el-button>
      </div>

      <div v-if="recentApplications.length === 0" class="empty-state">
        <div class="empty-state__icon">📮</div>
        <h3 class="empty-state__title">还没有投递记录</h3>
        <p class="empty-state__desc">浏览职位并投递后，进度、面试邀请与 HR 回复都会汇总到这里</p>
        <el-button type="primary" @click="router.push('/jobs')">去浏览职位</el-button>
      </div>

      <div v-else class="app-list">
        <div
          v-for="item in recentApplications"
          :key="item.id"
          class="app-row"
          :class="{ 'app-row--withdrawn': item.withdrawn }"
          @click="goDetail(item)"
        >
          <div class="app-main">
            <h3 class="job-title">{{ item.jobTitle || '—' }}</h3>
            <p class="meta">
              <span>{{ item.companyName || '—' }}</span>
              <span class="dot">·</span>
              <span>{{ item.appliedAt?.slice(0, 10) || '—' }}</span>
            </p>
          </div>
          <div class="app-aside">
            <!-- v0.5：候选人侧不展示 AI 评分（详见 AI集成.md §6.4.3） -->
            <el-tag :type="item.withdrawn ? 'info' : 'primary'" size="default">
              {{ statusLabel(item.status) }}
            </el-tag>
          </div>
        </div>
      </div>
    </section>

    <footer class="footer">
      <div class="footer__inner">
        <span>© 2026 汇聘 · 让每一次相遇都更值得</span>
        <nav class="footer__links">
          <a>关于我们</a>
          <a>联系客服</a>
          <a>使用条款</a>
        </nav>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.candidate-home {
  padding-top: 96px;
}

.hero {
  position: relative;
  padding: 64px 32px;
  margin-bottom: 32px;
  border-radius: 24px;
  background: linear-gradient(120deg, #1d6fd8 0%, #06b6d4 100%);
  color: #fff;
  overflow: hidden;
  isolation: isolate;
}

.hero__bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 80% 20%, rgba(255, 255, 255, 0.18), transparent 40%),
    radial-gradient(circle at 20% 80%, rgba(255, 255, 255, 0.12), transparent 45%);
  z-index: -1;
}

.hero__inner {
  max-width: 880px;
  margin: 0 auto;
  text-align: center;
}

.hero__title {
  font-size: 36px;
  font-weight: 700;
  letter-spacing: 0.04em;
  margin-bottom: 12px;
}

.hero__subtitle {
  font-size: 15px;
  opacity: 0.92;
  margin-bottom: 32px;
}

.search-bar {
  display: grid;
  grid-template-columns: 1fr 200px 120px;
  gap: 12px;
  background: #fff;
  padding: 12px;
  border-radius: 16px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.12);
}

.search-bar :deep(.el-input__wrapper) {
  padding: 4px 12px;
  border-radius: 10px;
  box-shadow: none;
}

.search-bar :deep(.el-input__wrapper):hover {
  box-shadow: 0 0 0 1px var(--primary-tint-strong);
}

.search-bar :deep(.el-input__inner) {
  height: 40px;
  font-size: 15px;
}

.search-bar__btn {
  height: 48px;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 600;
}

.search-icon {
  font-size: 20px;
  color: var(--text-muted);
}

.hot-cities {
  margin-top: 16px;
  font-size: 13px;
  opacity: 0.92;
}

.hot-cities__label {
  margin-right: 6px;
}

.hot-cities__item {
  margin-right: 12px;
  cursor: pointer;
  opacity: 0.92;
}

.hot-cities__item:hover {
  opacity: 1;
  text-decoration: underline;
}

.quick-actions {
  margin-bottom: 32px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.action-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 22px;
  border-radius: 18px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-soft);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.action-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow);
  border-color: var(--primary-tint-strong);
}

.action-card__icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--primary) 0%, var(--accent) 100%);
  color: #fff;
  font-size: 24px;
  font-weight: 600;
}

.action-card__body h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 4px;
}

.action-card__body p {
  font-size: 13px;
  color: var(--text-soft);
}

/* v0.5：候选人首页 stats 大卡片样式已移除（数据改嵌入「我的投递」section hint）。 */

.panel {
  padding: 24px;
  border-radius: 18px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-soft);
  margin-bottom: 32px;
}

.panel-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 18px;
}

.panel-title h2 {
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 4px;
}

.hint {
  font-size: 13px;
  color: var(--text-soft);
}
.hint strong {
  color: var(--primary-deep);
  font-weight: 600;
  margin: 0 2px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 24px;
  text-align: center;
}

.empty-state__icon {
  font-size: 56px;
  margin-bottom: 12px;
  opacity: 0.6;
}

.empty-state__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8px;
}

.empty-state__desc {
  font-size: 13px;
  color: var(--text-soft);
  margin-bottom: 18px;
  max-width: 420px;
}

.footer {
  margin-top: 32px;
  padding: 24px 0;
  border-top: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.5);
}

.footer__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-muted);
}

.footer__links {
  display: flex;
  gap: 24px;
}

.footer__links a {
  cursor: pointer;
  transition: color 0.18s ease;
}

.footer__links a:hover {
  color: var(--primary);
}

@media (max-width: 960px) {
  .search-bar {
    grid-template-columns: 1fr;
  }
  .card-grid {
    grid-template-columns: 1fr;
  }
}

.app-list { display: flex; flex-direction: column; gap: 10px; }
.app-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.18s ease;
}
.app-row:hover { border-color: var(--primary); transform: translateY(-1px); }
.app-row--withdrawn { opacity: 0.6; }
.app-main { flex: 1; min-width: 0; }
.job-title { font-size: 15px; font-weight: 600; color: var(--text); margin: 0 0 4px; }
.meta { font-size: 12px; color: var(--text-soft); margin: 0; display: flex; gap: 6px; }
.meta .dot { color: var(--text-muted); }
.app-aside { display: flex; gap: 6px; flex-shrink: 0; }

.job-list { display: flex; flex-direction: column; gap: 10px; }
.loading {
  padding: 32px 24px;
  text-align: center;
  color: var(--text-soft);
  font-size: 13px;
}
.link { color: var(--primary); cursor: pointer; text-decoration: none; }
.link:hover { text-decoration: underline; }
.set-pref-hint {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed var(--line);
  font-size: 13px;
  color: var(--text-soft);
  display: flex;
  align-items: center;
  gap: 8px;
}
.fallback-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-soft);
  margin: 18px 0 10px;
  padding-left: 4px;
}
</style>
