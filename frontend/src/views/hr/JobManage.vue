<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { jobApi } from '@/api/job'
import JobFormDrawer from '@/views/hr/JobFormDrawer.vue'

const router = useRouter()
const route = useRoute()

const tabs = [
  { key: 'DRAFT', label: '草稿' },
  { key: 'ONLINE', label: '招聘中' },
  { key: 'OFFLINE', label: '已下架' }
]

const activeTab = ref('DRAFT')
const loading = ref(false)
const records = ref([])
const total = ref(0)
const drawerOpen = ref(false)
const editingJob = ref(null)

const STATUS_MAP = {
  DRAFT: { label: '草稿', type: 'info' },
  ONLINE: { label: '招聘中', type: 'success' },
  OFFLINE: { label: '已下架', type: 'info' }
}

async function fetchJobs() {
  loading.value = true
  try {
    const page = await jobApi.listMine({
      status: activeTab.value,
      pageNum: 1,
      pageSize: 50
    })
    records.value = page.records || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

async function handlePublish(row) {
  try {
    await ElMessageBox.confirm(`确认将「${row.title}」上线招聘？`, '提示', { type: 'info' })
  } catch { return }
  try {
    await jobApi.publish(row.id)
    ElMessage.success('已上线')
    fetchJobs()
  } catch (e) {
    ElMessage.error(e.message || '上线失败')
  }
}

async function handleOffline(row) {
  try {
    await ElMessageBox.confirm(`确认将「${row.title}」下架？`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await jobApi.offline(row.id)
    ElMessage.success('已下架')
    fetchJobs()
  } catch (e) {
    ElMessage.error(e.message || '下架失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.title}」？删除后不可恢复`, '提示', { type: 'warning' })
  } catch { return }
  try {
    await jobApi.deleteJob(row.id)
    ElMessage.success('已删除')
    fetchJobs()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

function openCreate() {
  editingJob.value = null
  drawerOpen.value = true
}

function openEdit(row) {
  editingJob.value = row
  drawerOpen.value = true
}

function onSaved() {
  fetchJobs()
}

onMounted(() => {
  if (route.query.create === '1') {
    editingJob.value = null
    drawerOpen.value = true
    router.replace({ path: '/hr/jobs' })
  }
  fetchJobs()
})
</script>

<template>
  <div class="page-shell job-manage">
    <div class="head">
      <div class="head__title">
        <h2>职位管理</h2>
        <span class="hint">管理你发布的所有职位 · 共 {{ total }} 条</span>
      </div>
      <el-button type="primary" @click="openCreate">+ 发布新职位</el-button>
    </div>

    <div class="tabs">
      <button
        v-for="t in tabs"
        :key="t.key"
        class="tab-btn"
        :class="{ 'tab-btn--active': activeTab === t.key }"
        @click="activeTab = t.key; fetchJobs()"
      >
        {{ t.label }}
      </button>
    </div>

    <div v-if="loading" class="loading">加载中…</div>
    <div v-else-if="records.length === 0" class="empty-state">
      <div class="empty-state__icon">📋</div>
      <h3 class="empty-state__title">该状态下暂无职位</h3>
      <p class="empty-state__desc">点击右上角"发布新职位"开始招聘</p>
    </div>
    <div v-else class="job-table">
      <div class="row row--head">
        <div>职位</div>
        <div>行业 / 城市</div>
        <div>薪资</div>
        <div>状态</div>
        <div>更新时间</div>
        <div class="text-right">操作</div>
      </div>
      <div v-for="row in records" :key="row.id" class="row">
        <div class="job-info">
          <span class="job-title">{{ row.title }}</span>
          <span class="job-company">{{ row.companyName }}</span>
        </div>
        <div class="meta">
          <span>{{ row.industryName || '—' }}</span>
          <span>{{ row.cityName || '—' }}</span>
        </div>
        <div class="salary">
          <span class="salary-amount">{{ row.salaryMin ? `${row.salaryMin}-${row.salaryMax}` : '面议' }}</span>
          <span class="salary-unit" v-if="row.salaryMin">K / 月</span>
        </div>
        <div>
          <el-tag :type="STATUS_MAP[row.status]?.type" size="small">{{ STATUS_MAP[row.status]?.label }}</el-tag>
        </div>
        <div class="time">{{ row.updatedAt ? new Date(row.updatedAt).toLocaleString('zh-CN', { hour12: false }) : '—' }}</div>
        <div class="text-right actions">
          <!-- 草稿 → 上线 -->
          <el-button v-if="row.status === 'DRAFT'" text type="primary" size="small" @click="handlePublish(row)">
            上线
          </el-button>
          <!-- 已下架 → 重新上线 -->
          <el-button v-if="row.status === 'OFFLINE'" text type="primary" size="small" @click="handlePublish(row)">
            重新上线
          </el-button>
          <!-- 招聘中 → 下架 -->
          <el-button v-if="row.status === 'ONLINE'" text type="warning" size="small" @click="handleOffline(row)">
            下架
          </el-button>
          <!-- 编辑：除 ONLINE 外 -->
          <el-button v-if="row.status !== 'ONLINE'" text type="primary" size="small" @click="openEdit(row)">
            编辑
          </el-button>
          <!-- 删除：除 ONLINE 外 -->
          <el-button v-if="row.status !== 'ONLINE'" text type="danger" size="small" @click="handleDelete(row)">
            删除
          </el-button>
        </div>
      </div>
    </div>

    <JobFormDrawer v-model="drawerOpen" :job="editingJob" @saved="onSaved" />
  </div>
</template>

<style scoped>
.job-manage { padding-top: 96px; }
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 18px;
}
.head__title h2 { font-size: 22px; font-weight: 700; color: var(--text); margin-bottom: 4px; }
.hint { font-size: 13px; color: var(--text-soft); }
.tabs {
  display: flex;
  gap: 4px;
  background: var(--bg);
  padding: 4px;
  border-radius: 12px;
  margin-bottom: 16px;
  width: fit-content;
}
.tab-btn {
  border: none;
  background: transparent;
  padding: 8px 18px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--text-soft);
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease;
}
.tab-btn:hover {
  color: var(--primary);
}
.tab-btn--active {
  background: var(--panel-strong);
  color: var(--primary);
  font-weight: 600;
  box-shadow: var(--shadow-soft);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 0;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  box-shadow: var(--shadow-soft);
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
  margin-bottom: 6px;
}
.empty-state__desc {
  font-size: 13px;
  color: var(--text-soft);
}

.job-table {
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 8px 16px;
  box-shadow: var(--shadow-soft);
}
.row {
  display: grid;
  grid-template-columns: 2.4fr 1.4fr 0.9fr 0.9fr 1.1fr 1.6fr;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--line);
}
.row:last-child { border-bottom: none; }
.row--head {
  font-size: 12px;
  color: var(--text-soft);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: 12px 0 10px;
  border-bottom: 1px solid var(--line);
}
.job-info { display: flex; flex-direction: column; gap: 2px; }
.job-title { font-weight: 600; color: var(--text); }
.job-company { font-size: 12px; color: var(--text-soft); }
.meta { display: flex; gap: 12px; font-size: 13px; color: var(--text-soft); }
.salary {
  display: flex;
  align-items: baseline;
  gap: 3px;
}
.salary-amount {
  font-size: 16px;
  font-weight: 700;
  color: var(--danger);
  letter-spacing: -0.02em;
}
.salary-unit {
  font-size: 11px;
  color: var(--text-soft);
}
.time { font-size: 13px; color: var(--text-soft); }
.text-right { text-align: right; }
.actions { display: flex; justify-content: flex-end; gap: 6px; flex-wrap: wrap; }

.loading {
  padding: 24px;
  text-align: center;
  color: var(--text-soft);
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
}

@media (max-width: 900px) {
  .row { grid-template-columns: 1.6fr 1fr 1fr; row-gap: 6px; }
  .row--head { display: none; }
}
</style>