<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { jobApi } from '@/api/job'
import JobFormDrawer from '@/views/hr/JobFormDrawer.vue'

const router = useRouter()
const route = useRoute()

const tabs = [
  { key: 'DRAFT', label: '草稿' },
  { key: 'PENDING', label: '审核中' },
  { key: 'ONLINE', label: '招聘中' },
  { key: 'OFFLINE', label: '已下架' }
]

const activeTab = ref('ONLINE')
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
const AUDIT_MAP = {
  PENDING: { label: '审核中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' }
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
        <div>审核</div>
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
          {{ row.salaryMin ? `${row.salaryMin}-${row.salaryMax}K` : '面议' }}
        </div>
        <div>
          <el-tag :type="STATUS_MAP[row.status]?.type" size="small">{{ STATUS_MAP[row.status]?.label }}</el-tag>
        </div>
        <div>
          <el-tag v-if="AUDIT_MAP[row.auditStatus]" :type="AUDIT_MAP[row.auditStatus].type" size="small" effect="plain">
            {{ AUDIT_MAP[row.auditStatus].label }}
          </el-tag>
          <span v-if="row.auditNote" class="audit-note">{{ row.auditNote }}</span>
        </div>
        <div class="time">{{ row.updatedAt ? new Date(row.updatedAt).toLocaleString('zh-CN', { hour12: false }) : '—' }}</div>
        <div class="text-right actions">
          <el-button v-if="row.status === 'DRAFT'" text type="primary" size="small" @click="handlePublish(row)">
            提交上线
          </el-button>
          <el-button v-if="row.status === 'OFFLINE'" text type="primary" size="small" @click="handlePublish(row)">
            重新上线
          </el-button>
          <el-button v-if="row.status === 'ONLINE'" text type="warning" size="small" @click="handleOffline(row)">
            下架
          </el-button>
          <el-button v-if="row.status !== 'ONLINE'" text type="primary" size="small" @click="openEdit(row)">
            编辑
          </el-button>
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
}
.tab-btn--active {
  background: var(--panel-strong);
  color: var(--primary);
  font-weight: 600;
  box-shadow: var(--shadow-soft);
}
.job-table {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--line);
  background: var(--panel-strong);
}
.row {
  display: grid;
  grid-template-columns: 2fr 1.5fr 1fr 1fr 1.5fr 1.4fr 2fr;
  gap: 12px;
  padding: 14px 18px;
  align-items: center;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
  color: var(--text);
}
.row:last-child { border-bottom: none; }
.row--head {
  background: var(--bg);
  color: var(--text-soft);
  font-weight: 600;
  font-size: 12px;
}
.job-info { display: flex; flex-direction: column; gap: 2px; }
.job-title { font-weight: 600; }
.job-company { font-size: 12px; color: var(--text-soft); }
.meta { display: flex; flex-direction: column; gap: 2px; color: var(--text-soft); font-size: 12px; }
.salary { font-weight: 600; color: var(--danger); }
.audit-note {
  display: block;
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.time { color: var(--text-muted); font-size: 12px; }
.actions { display: flex; justify-content: flex-end; gap: 4px; flex-wrap: wrap; }
.text-right { text-align: right; }
.empty-state, .loading {
  padding: 56px 24px;
  text-align: center;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
}
.empty-state__icon { font-size: 48px; opacity: 0.55; margin-bottom: 8px; }
.empty-state__title { font-size: 16px; font-weight: 600; margin-bottom: 6px; color: var(--text); }
.empty-state__desc { font-size: 13px; color: var(--text-soft); }
</style>
