<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { jobApi } from '@/api/job'
import { applicationApi } from '@/api/application'
import { messageApi } from '@/api/message'
import JobListCard from '@/views/common/JobListCard.vue'

const route = useRoute()
const router = useRouter()

function goPublish() {
  router.push('/hr/jobs?create=1')
}

function goInbox() {
  router.push('/hr/applications')
}

function goMessages() {
  router.push('/messages')
}

/** HR 端首页「我发布的职位」：仅展示 ONLINE，按用户要求不显示状态。 */
const onlineJobs = ref([])
const onlineJobsTotal = ref(0)
const jobsLoading = ref(false)

/** HR 端首页「近期收到的候选人」：跨职位聚合最新 5 条（按 AI 评分排序）。 */
const recentCandidates = ref([])
const recentCandidatesTotal = ref(0)
const recentLoading = ref(false)

/** HR 端首页 hero 指标。 */
const stats = ref({
  onlineJobs: 0,
  receivedResumes: 0,
  unreadMessages: 0
})

async function loadStats() {
  // 三类数据并发拉：在线职位数 + 收到简历数 + 未读消息数（§5 消息中心）
  try {
    const [jobsRes, appsRes, unreadRes] = await Promise.all([
      jobApi.listMine({ status: 'ONLINE', pageNum: 1, pageSize: 1 }),
      applicationApi.hrList({ jobId: null, status: 'ACTIVE', pageNum: 1, pageSize: 1 }),
      messageApi.unreadStats()
    ])
    stats.value.onlineJobs = jobsRes?.total || 0
    stats.value.receivedResumes = appsRes?.total || 0
    stats.value.unreadMessages = unreadRes?.total || 0
  } catch (e) {
    console.warn('HrHome stats load failed:', e)
  }
}

async function loadOnlineJobs() {
  jobsLoading.value = true
  try {
    const page = await jobApi.listMine({ status: 'ONLINE', pageNum: 1, pageSize: 5 })
    onlineJobs.value = page?.records || []
    onlineJobsTotal.value = page?.total || 0
  } catch (e) {
    console.warn('HrHome online jobs load failed:', e)
  } finally {
    jobsLoading.value = false
  }
}

async function loadRecentCandidates() {
  recentLoading.value = true
  try {
    const page = await applicationApi.hrList({
      jobId: null, status: 'ACTIVE', sort: 'applied_desc', pageNum: 1, pageSize: 5
    })
    recentCandidates.value = page?.records || []
    recentCandidatesTotal.value = page?.total || 0
  } catch (e) {
    console.warn('HrHome recent candidates load failed:', e)
  } finally {
    recentLoading.value = false
  }
}

async function refreshData() {
  // 静默失败，不阻塞首页
  try {
    await Promise.all([loadStats(), loadOnlineJobs(), loadRecentCandidates()])
  } catch (e) {
    // ignore
  }
}

function goJobDetail(job) {
  // HR 在首页点职位卡 → 跳该职位的收件箱
  router.push(`/hr/applications?jobId=${job.id}`)
}

function goCandidateDetail(app) {
  router.push(`/hr/applications/${app.id}`)
}

const quickActions = [
  { icon: '+', title: '发布新职位', action: 'publish' },
  { icon: '📥', title: '简历收件箱', action: 'inbox' },
  { icon: '💬', title: '消息中心', action: 'messages' }
]

function onQuick(action) {
  if (action === 'publish') return goPublish()
  if (action === 'inbox') return goInbox()
  if (action === 'messages') return goMessages()
}

const STATUS_TYPE = {
  PENDING_REVIEW: 'info',
  VIEWED_BY_HR: '',
  RESUME_PASSED: 'primary',
  INTERVIEWING: 'warning',
  OFFERED: 'success',
  HIRED: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'info'
}
const STATUS_LABEL = {
  PENDING_REVIEW: '待查看',
  VIEWED_BY_HR: '已查看',
  RESUME_PASSED: '简历通过',
  INTERVIEWING: '面试中',
  OFFERED: '已发 Offer',
  HIRED: '已入职',
  REJECTED: '已拒绝',
  WITHDRAWN: '已撤回'
}
function statusLabel(s) { return STATUS_LABEL[s] || s }
function statusType(s) { return STATUS_TYPE[s] || 'info' }

/**
 * 评分圆圈颜色：高分绿、中分蓝、低分灰、待评分 = 描边虚线
 */
function scoreColor(score) {
  if (score == null) return 'var(--text-muted)'
  if (score >= 80) return '#22c55e'   // 绿
  if (score >= 60) return '#3b82f6'   // 蓝
  return '#94a3b8'                    // 灰
}

onMounted(refreshData)

/**
 * v0.5 修复：HR 首页 /home 切换时强制刷新（解决"上线职位后首页不显示"等不同步问题）。
 */
watch(() => route.path, (newPath) => {
  if (newPath === '/home') refreshData()
})
</script>

<template>
  <div class="page-shell home hr-home">
    <section class="hero">
      <div class="hero__bg"></div>
      <div class="hero__inner">
        <div class="hero__text">
          <span class="hero__eyebrow">招聘工作台</span>
          <h1 class="hero__title">高效招聘，找到合适的人才</h1>
          <p class="hero__subtitle">集中处理职位、简历与候选人沟通，把时间花在判断而非整理上</p>
        </div>
        <div class="hero__metric">
          <div class="metric-item">
            <span class="metric-num">{{ stats.onlineJobs }}</span>
            <span class="metric-label">在线职位</span>
          </div>
          <div class="metric-item">
            <span class="metric-num">{{ stats.receivedResumes }}</span>
            <span class="metric-label">收到简历</span>
          </div>
          <div class="metric-item">
            <span class="metric-num">{{ stats.unreadMessages }}</span>
            <span class="metric-label">未读消息</span>
          </div>
        </div>
      </div>
    </section>

    <section class="quick-actions">
      <div class="quick-bar">
        <button
          v-for="(item, idx) in quickActions"
          :key="idx"
          class="quick-btn"
          :class="{ 'quick-btn--primary': idx === 0, 'quick-btn--disabled': item.disabled }"
          :disabled="item.disabled"
          @click="onQuick(item.action)"
        >
          <span class="quick-btn__icon">{{ item.icon }}</span>
          <span class="quick-btn__title">{{ item.title }}</span>
        </button>
      </div>
    </section>

    <div class="main-grid">
      <section class="panel applications-panel">
        <div class="panel-head">
          <div class="panel-title">
            <h2>近期收到的候选人</h2>
            <span class="hint">共 {{ recentCandidatesTotal }} 份投递 · 按投递时间倒序</span>
          </div>
          <el-button text type="primary" @click="goInbox()">查看全部 →</el-button>
        </div>

        <div v-if="recentLoading" class="loading">加载中…</div>

        <div v-else-if="recentCandidates.length === 0" class="empty-state">
          <div class="empty-state__icon">📭</div>
          <h3 class="empty-state__title">暂无候选人投递</h3>
          <p class="empty-state__desc">发布职位后，候选人的投递将自动汇总到简历收件箱，按匹配度排序展示</p>
          <el-button type="primary" @click="goPublish()">去发布职位</el-button>
        </div>

        <div v-else class="cand-list">
          <div
            v-for="c in recentCandidates"
            :key="c.id"
            class="cand-row"
            @click="goCandidateDetail(c)"
          >
            <div class="cand-score" :style="{ borderColor: scoreColor(c.aiScore) }">
              <span v-if="c.aiScore != null" class="cand-score__num">{{ c.aiScore }}</span>
              <span v-else class="cand-score__pending">—</span>
              <span class="cand-score__label">匹配度</span>
            </div>
            <div class="cand-main">
              <h3 class="cand-name">{{ c.candidateName || '候选人' }}</h3>
              <p class="cand-job">投递：{{ c.jobTitle || '—' }} · {{ c.companyName || '—' }}</p>
              <p class="cand-time">{{ c.appliedAt?.slice(0, 16).replace('T', ' ') || '—' }}</p>
            </div>
            <el-tag :type="statusType(c.status)" size="default" effect="plain">
              {{ statusLabel(c.status) }}
            </el-tag>
          </div>
        </div>
      </section>

      <aside class="panel tips-panel">
        <div class="panel-head">
          <div class="panel-title">
            <h2>招聘小贴士</h2>
          </div>
        </div>
        <p class="tip">JD 中包含具体的技术栈与项目类型，能让匹配度评估更精准。</p>
        <p class="tip">对高匹配度候选人，建议 24 小时内回复，提升入职转化。</p>
        <p class="tip">及时更新职位状态，避免无效投递堆积。</p>
      </aside>
    </div>

    <section class="panel jobs-panel">
      <div class="panel-head">
        <div class="panel-title">
          <h2>我发布的职位</h2>
          <span class="hint">共 {{ onlineJobsTotal }} 个在线职位 · 点击查看投递列表</span>
        </div>
        <el-button text type="primary" @click="router.push('/hr/jobs')">管理全部 →</el-button>
      </div>

      <div v-if="jobsLoading" class="loading">加载中…</div>

      <div v-else-if="onlineJobs.length === 0" class="empty-state">
        <div class="empty-state__icon">📋</div>
        <h3 class="empty-state__title">还没有在线职位</h3>
        <p class="empty-state__desc">前往「职位管理」发布并上线职位后，这里会展示所有在招岗位</p>
        <el-button type="primary" @click="goPublish()">立即发布</el-button>
      </div>

      <div v-else class="job-list">
        <JobListCard
          v-for="job in onlineJobs"
          :key="job.id"
          :job="job"
          :show-actions="false"
          @detail="goJobDetail"
        />
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
.hr-home {
  padding-top: 96px;
}

.hero {
  position: relative;
  margin-bottom: 24px;
  padding: 40px 40px;
  border-radius: 24px;
  background: linear-gradient(120deg, #0f4fb0 0%, #1d6fd8 50%, #06b6d4 100%);
  color: #fff;
  overflow: hidden;
  isolation: isolate;
}

.hero__bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 85% 15%, rgba(255, 255, 255, 0.15), transparent 45%),
    radial-gradient(circle at 15% 90%, rgba(255, 255, 255, 0.1), transparent 50%);
  z-index: -1;
}

.hero__inner {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 32px;
}

.hero__eyebrow {
  display: inline-block;
  font-size: 12px;
  letter-spacing: 0.16em;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  margin-bottom: 14px;
}

.hero__title {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 0.02em;
  margin-bottom: 10px;
}

.hero__subtitle {
  font-size: 15px;
  opacity: 0.92;
}

.hero__metric {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 18px 24px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 16px;
  backdrop-filter: blur(10px);
}

.metric-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 60px;
}

.metric-num {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
}

.metric-label {
  font-size: 12px;
  opacity: 0.86;
  margin-top: 2px;
  white-space: nowrap;
}

.quick-actions {
  margin-bottom: 24px;
}

.quick-bar {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  padding: 16px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  box-shadow: var(--shadow-soft);
}

.quick-btn {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 12px;
  height: 64px;
  padding: 0 20px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg);
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.quick-btn:hover:not(:disabled) {
  background: var(--primary-tint);
  border-color: var(--primary);
  color: var(--primary-deep);
  transform: translateY(-1px);
}

.quick-btn__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--primary-tint);
  color: var(--primary);
  font-size: 17px;
  font-weight: 700;
  flex-shrink: 0;
}

.quick-btn__title {
  font-size: 15px;
  letter-spacing: 0.02em;
}

.quick-btn--primary {
  background: linear-gradient(135deg, var(--primary), var(--primary-deep));
  border-color: var(--primary);
  color: #fff;
}

.quick-btn--primary:hover {
  background: linear-gradient(135deg, var(--primary-deep), var(--primary));
  border-color: var(--primary-deep);
  color: #fff;
}

.quick-btn--primary .quick-btn__icon {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.main-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
  margin-bottom: 24px;
}

.panel {
  padding: 24px;
  border-radius: 18px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-soft);
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

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-state__icon {
  font-size: 48px;
  margin-bottom: 12px;
  opacity: 0.55;
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
  line-height: 1.7;
}

.tips-panel .tip {
  font-size: 13px;
  color: var(--text-soft);
  line-height: 1.7;
  padding: 10px 12px;
  background: var(--bg);
  border-radius: 10px;
  margin-bottom: 8px;
}

.tips-panel .tip:last-child {
  margin-bottom: 0;
}

.loading {
  padding: 32px 24px;
  text-align: center;
  color: var(--text-soft);
  font-size: 13px;
}

.job-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* v0.5：候选人行（HR 首页） */
.cand-list { display: flex; flex-direction: column; gap: 10px; }
.cand-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.18s ease;
}
.cand-row:hover {
  border-color: var(--primary);
  transform: translateY(-1px);
}
.cand-score {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: 3px solid;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--panel-strong);
}
.cand-score__num { font-size: 18px; font-weight: 700; line-height: 1; }
.cand-score__pending { font-size: 18px; color: var(--text-muted); line-height: 1; }
.cand-score__label { font-size: 10px; color: var(--text-muted); margin-top: 2px; }

.cand-main { flex: 1; min-width: 0; }
.cand-name { font-size: 15px; font-weight: 600; color: var(--text); margin: 0 0 4px; }
.cand-job { font-size: 12px; color: var(--text-soft); margin: 0 0 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cand-time { font-size: 11px; color: var(--text-muted); margin: 0; }

/* v0.5：disabled quick action */
.quick-btn--disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: var(--bg);
  color: var(--text-muted);
}
.quick-btn--disabled:hover {
  background: var(--bg);
  border-color: var(--line);
  color: var(--text-muted);
  transform: none;
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

@media (max-width: 1100px) {
  .hero__inner {
    grid-template-columns: 1fr;
  }
  .hero__metric {
    flex-wrap: wrap;
    gap: 16px;
  }
  .quick-bar {
    grid-template-columns: 1fr;
  }
  .main-grid {
    grid-template-columns: 1fr;
  }
}
</style>
