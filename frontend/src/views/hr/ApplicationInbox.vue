<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { applicationApi } from '@/api/application'
import { jobApi } from '@/api/job'

const router = useRouter()

// 数据
const jobs = ref([])                 // HR 端所有职位（用于顶部 select）
const selectedJobId = ref(null)
const records = ref([])
const total = ref(0)
const loading = ref(false)
const showWithdrawn = ref(false)
const keyword = ref('')

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
const STATUS_TYPE = {
  PENDING_REVIEW: 'info',
  // v0.7.4.5：空字符串 '' 改为 'info'，避免 el-tag type 渲染异常
  VIEWED_BY_HR: 'info',
  RESUME_PASSED: 'primary',
  INTERVIEWING: 'warning',
  OFFERED: 'success',
  HIRED: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'info'
}

onMounted(async () => {
  // 加载 HR 的所有职位（取 DRAFT/ONLINE/OFFLINE 三个 tab 的并集）
  await loadJobs()
  // 默认选第一个
  if (jobs.value.length > 0) {
    selectedJobId.value = jobs.value[0].id
    await loadList()
  }
})

async function loadJobs() {
  try {
    const all = []
    for (const status of ['DRAFT', 'ONLINE', 'OFFLINE']) {
      const res = await jobApi.listMine({ status, pageNum: 1, pageSize: 100 })
      all.push(...(res?.records || []))
    }
    jobs.value = all
  } catch (e) {
    ElMessage.error(e?.message || '加载职位失败')
  }
}

async function loadList() {
  if (!selectedJobId.value) return
  loading.value = true
  try {
    const query = {
      jobId: selectedJobId.value,
      pageNum: 1,
      pageSize: 50,
      status: showWithdrawn.value ? 'WITHDRAWN' : 'ACTIVE'
    }
    if (keyword.value?.trim()) query.keyword = keyword.value.trim()
    const res = await applicationApi.hrList(query)
    records.value = res?.records || []
    total.value = res?.total || 0
  } catch (e) {
    ElMessage.error(e?.message || '加载投递列表失败')
  } finally {
    loading.value = false
  }
}

function onJobChange() { loadList() }
function onToggleWithdrawn() { loadList() }

function goDetail(row) {
  router.push(`/hr/applications/${row.id}`)
}

function statusLabel(s) { return STATUS_LABEL[s] || s }
function statusType(s) { return STATUS_TYPE[s] || 'info' }

const empty = computed(() => !loading.value && records.value.length === 0)
</script>

<template>
  <div class="page-shell hr-inbox">
    <h1 class="page-title">简历收件箱</h1>

    <div class="filter-bar">
      <el-select
        v-model="selectedJobId"
        placeholder="选择职位"
        class="job-select"
        @change="onJobChange"
      >
        <el-option
          v-for="j in jobs"
          :key="j.id"
          :label="`${j.title}（${j.status}）`"
          :value="j.id"
        />
      </el-select>

      <el-input
        v-model="keyword"
        placeholder="候选人姓名 / 邮箱"
        clearable
        class="keyword-input"
        @keyup.enter="loadList"
      >
        <template #append>
          <el-button @click="loadList">搜索</el-button>
        </template>
      </el-input>

      <el-checkbox v-model="showWithdrawn" @change="onToggleWithdrawn">
        显示已撤回
      </el-checkbox>

      <span class="count">共 {{ total }} 份投递</span>
    </div>

    <div v-if="loading" class="loading">加载中…</div>

    <div v-else-if="empty" class="empty">
      <p class="empty-title">暂无投递</p>
      <p class="empty-tip">{{ showWithdrawn ? '没有撤回的投递' : '选择职位后查看投递列表，或等待候选人投递' }}</p>
    </div>

    <div v-else class="list">
      <div
        v-for="item in records"
        :key="item.id"
        class="card"
        :class="{ 'card--withdrawn': item.withdrawn }"
        @click="goDetail(item)"
      >
        <div class="card-main">
          <h3 class="candidate">{{ item.candidateName || '—' }}</h3>
          <p class="meta">投递于 {{ item.appliedAt?.slice(0, 10) || '—' }}</p>
        </div>

        <div class="card-middle">
          <el-tag :type="statusType(item.status)" size="default">{{ statusLabel(item.status) }}</el-tag>
        </div>

        <div class="card-score">
          <div v-if="item.aiScore != null" class="score-wrap">
            <div class="score-num">{{ item.aiScore }}</div>
            <div class="score-label">AI 匹配度</div>
          </div>
          <div v-else class="score-wrap score-wrap--pending">
            <div class="score-label">待评分</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hr-inbox { padding-top: 96px; }
.page-title { font-size: 24px; font-weight: 700; margin-bottom: 18px; color: var(--text); }

.filter-bar {
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  box-shadow: var(--shadow-soft);
}
.job-select { width: 280px; }
.keyword-input { width: 280px; }
.count { margin-left: auto; font-size: 13px; color: var(--text-soft); }

.loading, .empty {
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 56px 24px;
  text-align: center;
  color: var(--text-soft);
}
.empty-title { font-size: 16px; margin-bottom: 8px; color: var(--text); }

.list { display: flex; flex-direction: column; gap: 10px; }
.card {
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 16px 22px;
  display: flex;
  align-items: center;
  gap: 24px;
  cursor: pointer;
  transition: all 0.18s ease;
  box-shadow: var(--shadow-soft);
}
.card:hover {
  border-color: var(--primary);
  transform: translateY(-1px);
}
.card--withdrawn { opacity: 0.55; }

.card-main { flex: 1; min-width: 0; }
.candidate { font-size: 16px; font-weight: 600; color: var(--text); margin: 0 0 4px; }
.meta { font-size: 12px; color: var(--text-soft); margin: 0; }

.card-middle { flex-shrink: 0; }
.card-score {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 84px;
}
.score-wrap {
  text-align: center;
  background: var(--primary-tint);
  border-radius: 12px;
  padding: 8px 12px;
  width: 100%;
}
.score-wrap--pending { background: var(--panel-soft); }
.score-num { font-size: 22px; font-weight: 700; color: var(--primary-deep); line-height: 1; }
.score-label { font-size: 11px; color: var(--text-soft); margin-top: 2px; }
</style>
