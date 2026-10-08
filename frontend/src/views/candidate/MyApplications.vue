<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useApplicationStore } from '@/stores/useApplicationStore'

const route = useRoute()
const router = useRouter()
const store = useApplicationStore()

const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const STATUS_LABEL = {
  PENDING_REVIEW: '待 HR 查看',
  // v0.7.4.5：补全 VIEWED_BY_HR 映射（之前后端隐藏 + 前端无此 key 会显示原 enum 字符串）
  VIEWED_BY_HR:   '已查看',
  RESUME_PASSED: '简历通过',
  INTERVIEWING: '面试中',
  OFFERED: '已发 Offer',
  HIRED: '已入职',
  REJECTED: '已拒绝',
  WITHDRAWN: '已撤回'
}

const STATUS_TYPE = {
  PENDING_REVIEW: 'info',
  // v0.7.4.5：补全 + 修正（之前是 '' 空字符串会让 el-tag 渲染异常或回退默认）
  VIEWED_BY_HR:   'info',
  RESUME_PASSED: 'primary',
  INTERVIEWING: 'warning',
  OFFERED: 'success',
  HIRED: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'info'
}

onMounted(loadList)

/**
 * v0.7.4.4：监听 route.path，进入 /applications/mine 强制刷新列表。
 *
 * 历史 bug：完全依赖 onMounted 触发 fetchMine。某些场景下 onMounted 不触发：
 *  - 用户从 /applications/mine 通过 nav 跳到 /jobs，再投第二个职位后跳回 /applications/mine
 *    理论上会重新挂载并触发 onMounted，但实际可能因 Vue Router 行为差异未触发
 *  - keep-alive 缓存（未来可能加）
 * 修复：加 watch 兜底，确保 store 数据最新。
 * 注：store.apply 也在 v0.7.4.4 改为成功后立即 fetchMine，双保险。
 */
watch(
  () => route.path,
  (newPath) => {
    if (newPath === '/applications/mine') {
      pageNum.value = 1   // 切回首页时回到第 1 页
      loadList()
    }
  }
)

async function loadList() {
  try {
    const res = await store.fetchMine(pageNum.value, pageSize.value)
    total.value = res?.total || 0
  } catch (e) {
    ElMessage.error(e?.message || '加载失败')
  }
}

function onPageChange(p) {
  pageNum.value = p
  loadList()
}

async function onWithdraw(item) {
  if (!window.confirm('确定要撤回这份投递吗？撤回后无法恢复。')) return
  try {
    await store.withdraw(item.id)
    ElMessage.success('已撤回')
    // v0.7.4.4：store.withdraw 内部已 fetchMine 全量刷新，此处 loadList 仍调用（覆盖分页参数）
    await loadList()
  } catch (e) {
    ElMessage.error(e?.message || '撤回失败')
  }
}

function goDetail(item) {
  router.push(`/applications/${item.id}`)
}

function statusLabel(s) { return STATUS_LABEL[s] || s }
function statusType(s) { return STATUS_TYPE[s] || 'info' }
</script>

<template>
  <div class="page-shell my-applications">
    <h1 class="page-title">我的投递</h1>

    <div v-if="store.loading && store.myApplications.length === 0" class="loading">加载中…</div>

    <div v-else-if="store.myApplications.length === 0" class="empty">
      <p class="empty-title">暂无投递记录</p>
      <p class="empty-tip">去 <router-link to="/jobs">职位列表</router-link> 看看合适的岗位吧</p>
    </div>

    <div v-else class="list">
      <div
        v-for="item in store.myApplications"
        :key="item.id"
        class="card"
        :class="{ 'card--withdrawn': item.withdrawn }"
        @click="goDetail(item)"
      >
        <div class="card-main">
          <h3 class="job-title">{{ item.jobTitle || '—' }}</h3>
          <p class="company">{{ item.companyName || '—' }}</p>
          <p class="meta">投递于 {{ item.appliedAt?.slice(0, 10) || '—' }}</p>
          <!-- v0.5：候选人列表不展示 AI 评分（详见 AI集成.md §6.4.3） -->
        </div>
        <div class="card-aside">
          <el-tag :type="statusType(item.status)" size="large">{{ statusLabel(item.status) }}</el-tag>
          <el-button
            v-if="item.withdrawable"
            type="danger"
            plain
            size="small"
            @click.stop="onWithdraw(item)"
          >
            撤回
          </el-button>
        </div>
      </div>

      <el-pagination
        v-if="total > pageSize"
        layout="prev, pager, next, total"
        :page-size="pageSize"
        :total="total"
        :current-page="pageNum"
        @current-change="onPageChange"
        class="pager"
      />
    </div>
  </div>
</template>

<style scoped>
.my-applications { padding-top: 96px; }
.page-title { font-size: 24px; font-weight: 700; margin-bottom: 18px; color: var(--text); }

.loading, .empty {
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 56px 24px;
  text-align: center;
  color: var(--text-soft);
}
.empty-title { font-size: 16px; margin-bottom: 8px; color: var(--text); }
.empty-tip a { color: var(--primary); text-decoration: none; }
.empty-tip a:hover { text-decoration: underline; }

.list { display: flex; flex-direction: column; gap: 12px; }

.card {
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 20px 24px;
  display: flex;
  justify-content: space-between;
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
.card--withdrawn { opacity: 0.6; }

.card-main { flex: 1; min-width: 0; }
.job-title { font-size: 17px; font-weight: 600; color: var(--text); margin: 0 0 4px; }
.company { font-size: 13px; color: var(--text-soft); margin: 0 0 4px; }
.meta { font-size: 12px; color: var(--text-muted); margin: 0 0 8px; }

.card-aside { display: flex; flex-direction: column; align-items: flex-end; gap: 10px; flex-shrink: 0; }
.pager { display: flex; justify-content: center; margin-top: 16px; }
</style>
