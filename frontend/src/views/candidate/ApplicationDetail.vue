<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { applicationApi } from '@/api/application'

const route = useRoute()
const router = useRouter()

const app = ref(null)
const loading = ref(true)

const STATUS_LABEL = {
  PENDING_REVIEW: '待 HR 查看',
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
  VIEWED_BY_HR: '',
  RESUME_PASSED: 'primary',
  INTERVIEWING: 'warning',
  OFFERED: 'success',
  HIRED: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'info'
}

const structuredResume = computed(() => {
  if (!app.value?.resumeSnapshotJson) return null
  try {
    return JSON.parse(app.value.resumeSnapshotJson)
  } catch {
    return null
  }
})

// v0.5：移除 AI 评分轮询 + 标签 + 理由。候选人侧不感知 AI 评分。
// 旧实现：每 4s × 15 次轮询 GET /api/applications/{id} 等待 aiScore。
// 现在后端 listMine / getDetail 已不返回 aiScore，候选人只能看到状态时间线。

onMounted(loadDetail)

async function loadDetail() {
  loading.value = true
  try {
    const id = Number(route.params.id)
    app.value = await applicationApi.detail(id)
  } catch (e) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onWithdraw() {
  if (!window.confirm('确定要撤回这份投递吗？')) return
  try {
    await applicationApi.withdraw(app.value.id)
    ElMessage.success('已撤回')
    await loadDetail()
  } catch (e) {
    ElMessage.error(e?.message || '撤回失败')
  }
}

function statusLabel(s) { return STATUS_LABEL[s] || s }
function statusType(s) { return STATUS_TYPE[s] || 'info' }

function fmtDateTime(dt) {
  if (!dt) return '—'
  return dt.replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <div class="page-shell app-detail">
    <div class="back" @click="router.push('/applications/mine')">← 返回我的投递</div>

    <div v-if="loading && !app" class="loading">加载中…</div>
    <div v-else-if="!app" class="loading">投递不存在</div>

    <template v-else>
      <div class="head-card">
        <div class="head-main">
          <h1 class="title">{{ app.jobTitle }}</h1>
          <p class="meta">
            <span>{{ app.companyName || '—' }}</span>
            <span class="dot">·</span>
            <span>投递于 {{ fmtDateTime(app.appliedAt) }}</span>
          </p>
          <div class="badges">
            <el-tag :type="statusType(app.status)" size="large">{{ statusLabel(app.status) }}</el-tag>
          </div>
          <!-- v0.5：候选人侧不展示 AI 评分 / 评分理由（详见 AI集成.md §6.4.3） -->
        </div>
        <div class="head-actions">
          <el-button
            v-if="app.withdrawable"
            type="danger"
            plain
            @click="onWithdraw"
          >
            撤回投递
          </el-button>
        </div>
      </div>

      <div class="section">
        <h2 class="section-title">状态时间线</h2>
        <el-timeline class="timeline">
          <el-timeline-item
            v-for="(h, idx) in (app.history || [])"
            :key="idx"
            :timestamp="fmtDateTime(h.changedAt)"
            :type="statusType(h.toStatus)"
            placement="top"
          >
            <div class="history-item">
              <span class="status-name">{{ statusLabel(h.toStatus) }}</span>
              <span v-if="h.fromStatus" class="arrow">← {{ statusLabel(h.fromStatus) }}</span>
              <span class="changer">{{ h.changedByName }}</span>
              <p v-if="h.note" class="note">{{ h.note }}</p>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>

      <div class="section">
        <h2 class="section-title">投递简历快照</h2>
        <div class="resume-card">
          <div class="resume-basic">
            <div class="name">{{ app.resumeSnapshotName || '—' }}</div>
            <div class="contact">
              <span v-if="app.resumeSnapshotEmail">📧 {{ app.resumeSnapshotEmail }}</span>
              <span v-if="app.resumeSnapshotPhone">📱 {{ app.resumeSnapshotPhone }}</span>
            </div>
          </div>
          <div v-if="structuredResume?.selfIntro" class="self-intro">
            <h4>自我介绍</h4>
            <p>{{ structuredResume.selfIntro }}</p>
          </div>
          <div v-if="structuredResume?.skills?.length" class="skills">
            <h4>技能</h4>
            <div class="skill-tags">
              <el-tag v-for="s in structuredResume.skills" :key="s" type="info" effect="plain">{{ s }}</el-tag>
            </div>
          </div>
          <div v-if="structuredResume?.education?.length" class="block">
            <h4>教育经历</h4>
            <div v-for="(e, i) in structuredResume.education" :key="i" class="block-item">
              <div class="block-row">
                <strong>{{ e.school || '—' }}</strong>
                <span>{{ [e.startDate, e.endDate].filter(Boolean).join(' ~ ') }}</span>
              </div>
              <div class="block-sub">{{ [e.degree, e.major].filter(Boolean).join(' · ') }}</div>
            </div>
          </div>
          <div v-if="structuredResume?.work?.length" class="block">
            <h4>工作经历</h4>
            <div v-for="(w, i) in structuredResume.work" :key="i" class="block-item">
              <div class="block-row">
                <strong>{{ w.company || '—' }}</strong>
                <span>{{ [w.startDate, w.endDate].filter(Boolean).join(' ~ ') }}</span>
              </div>
              <div class="block-sub">{{ w.position || '—' }}</div>
              <p v-if="w.description" class="block-desc">{{ w.description }}</p>
            </div>
          </div>
          <div v-if="structuredResume?.projects?.length" class="block">
            <h4>项目经历</h4>
            <div v-for="(p, i) in structuredResume.projects" :key="i" class="block-item">
              <div class="block-row">
                <strong>{{ p.name || '—' }}</strong>
                <span>{{ [p.startDate, p.endDate].filter(Boolean).join(' ~ ') }}</span>
              </div>
              <div class="block-sub">{{ p.role || '—' }}</div>
              <p v-if="p.description" class="block-desc">{{ p.description }}</p>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.app-detail { padding-top: 96px; }
.back {
  font-size: 13px;
  color: var(--text-soft);
  cursor: pointer;
  margin-bottom: 14px;
  display: inline-block;
}
.back:hover { color: var(--primary); }

.loading {
  padding: 56px 24px;
  text-align: center;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  color: var(--text-soft);
}

.head-card {
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 28px 32px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  margin-bottom: 18px;
  box-shadow: var(--shadow-soft);
}
.head-main { flex: 1; min-width: 0; }
.title { font-size: 24px; font-weight: 700; color: var(--text); margin: 0 0 8px; }
.meta { font-size: 13px; color: var(--text-soft); margin: 0 0 12px; display: flex; gap: 6px; flex-wrap: wrap; }
.meta .dot { color: var(--text-muted); }
.badges { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.head-actions { display: flex; gap: 8px; flex-shrink: 0; }

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
  margin: 0 0 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--line);
}

.timeline { padding: 8px 0; }
.history-item { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.status-name { font-weight: 600; }
.arrow { color: var(--text-muted); font-size: 13px; }
.changer { font-size: 13px; color: var(--text-soft); }
.note { width: 100%; margin: 4px 0 0; padding: 6px 10px; background: var(--panel-soft); border-radius: 6px; font-size: 13px; color: var(--text-soft); }

.resume-card { display: flex; flex-direction: column; gap: 16px; }
.resume-basic { padding: 12px 16px; background: var(--panel-soft); border-radius: 10px; }
.name { font-size: 18px; font-weight: 600; color: var(--text); }
.contact { display: flex; gap: 16px; margin-top: 4px; font-size: 13px; color: var(--text-soft); }
.self-intro h4, .skills h4, .block h4 { font-size: 13px; font-weight: 600; color: var(--text-soft); margin: 0 0 6px; }
.self-intro p { margin: 0; font-size: 14px; color: var(--text); line-height: 1.7; white-space: pre-wrap; }
.skill-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.block-item { padding: 10px 0; border-bottom: 1px dashed var(--line); }
.block-item:last-child { border-bottom: none; }
.block-row { display: flex; justify-content: space-between; align-items: baseline; }
.block-row strong { color: var(--text); font-size: 14px; }
.block-row span { font-size: 12px; color: var(--text-soft); }
.block-sub { font-size: 13px; color: var(--text-soft); margin-top: 2px; }
.block-desc { font-size: 13px; color: var(--text); margin: 4px 0 0; line-height: 1.6; white-space: pre-wrap; }
</style>
