<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { applicationApi } from '@/api/application'

const route = useRoute()
const router = useRouter()

const app = ref(null)
const snapshot = ref(null)
const notes = ref([])
const loading = ref(true)
const snapshotLoading = ref(false)

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
  VIEWED_BY_HR: '',
  RESUME_PASSED: 'primary',
  INTERVIEWING: 'warning',
  OFFERED: 'success',
  HIRED: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'info'
}

// 状态推进选项（按当前状态动态过滤）
const ALL_TRANSITIONS = {
  PENDING_REVIEW: [
    { value: 'VIEWED_BY_HR', label: '已查看' },
    { value: 'REJECTED', label: '拒绝' }
  ],
  VIEWED_BY_HR: [
    { value: 'RESUME_PASSED', label: '简历通过' },
    { value: 'REJECTED', label: '拒绝' }
  ],
  RESUME_PASSED: [
    { value: 'INTERVIEWING', label: '进入面试' },
    { value: 'REJECTED', label: '拒绝' }
  ],
  INTERVIEWING: [
    { value: 'OFFERED', label: '发 Offer' },
    { value: 'REJECTED', label: '拒绝' }
  ],
  OFFERED: [
    { value: 'HIRED', label: '已入职' },
    { value: 'REJECTED', label: '拒绝' }
  ]
}

const advanceOptions = computed(() => app.value ? (ALL_TRANSITIONS[app.value.status] || []) : [])
const canAdvance = computed(() => advanceOptions.value.length > 0)

const advanceForm = ref({ toStatus: '', note: '' })
const advancing = ref(false)

const noteForm = ref({ content: '' })
const editingNoteId = ref(null)
const editingNoteContent = ref('')

const showSnapshot = ref(false)

const structuredResume = computed(() => {
  const src = snapshot.value?.structuredJson || app.value?.resumeSnapshotJson
  if (!src) return null
  try {
    return JSON.parse(src)
  } catch {
    return null
  }
})

onMounted(loadDetail)

async function loadDetail() {
  loading.value = true
  try {
    const id = Number(route.params.id)
    app.value = await applicationApi.hrDetail(id)
    notes.value = app.value.notes || []
  } catch (e) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadSnapshot() {
  if (snapshot.value) {
    showSnapshot.value = true
    return
  }
  snapshotLoading.value = true
  try {
    const id = Number(route.params.id)
    snapshot.value = await applicationApi.resumeSnapshot(id)
    showSnapshot.value = true
  } catch (e) {
    ElMessage.error(e?.message || '加载简历失败')
  } finally {
    snapshotLoading.value = false
  }
}

async function onAdvance() {
  if (!advanceForm.value.toStatus) {
    ElMessage.warning('请选择目标状态')
    return
  }
  if (advanceForm.value.toStatus === 'REJECTED' && !advanceForm.value.note?.trim()) {
    ElMessage.warning('请填写拒绝理由')
    return
  }
  advancing.value = true
  try {
    await applicationApi.pushStatus(app.value.id, {
      toStatus: advanceForm.value.toStatus,
      note: advanceForm.value.note
    })
    ElMessage.success('状态已更新')
    advanceForm.value = { toStatus: '', note: '' }
    await loadDetail()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    advancing.value = false
  }
}

async function onAddNote() {
  if (!noteForm.value.content?.trim()) {
    ElMessage.warning('请填写备注')
    return
  }
  try {
    await applicationApi.addNote(app.value.id, { content: noteForm.value.content })
    noteForm.value.content = ''
    await loadDetail()
    ElMessage.success('备注已保存')
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  }
}

function startEditNote(n) {
  editingNoteId.value = n.id
  editingNoteContent.value = n.content
}

async function saveEditNote() {
  if (!editingNoteContent.value?.trim()) {
    ElMessage.warning('备注不能为空')
    return
  }
  try {
    await applicationApi.updateNote(app.value.id, editingNoteId.value, { content: editingNoteContent.value })
    editingNoteId.value = null
    editingNoteContent.value = ''
    await loadDetail()
    ElMessage.success('已更新')
  } catch (e) {
    ElMessage.error(e?.message || '更新失败')
  }
}

async function deleteNote(n) {
  try {
    await ElMessageBox.confirm('确认删除这条备注？', '提示', { type: 'warning' })
  } catch { return }
  try {
    await applicationApi.deleteNote(app.value.id, n.id)
    await loadDetail()
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

function onContactCandidate() {
  if (!app.value?.candidateId) return
  router.push({ path: '/messages', query: { peer: app.value.candidateId } })
}

function statusLabel(s) { return STATUS_LABEL[s] || s }
function statusType(s) { return STATUS_TYPE[s] || 'info' }

function fmtDateTime(dt) {
  if (!dt) return '—'
  return dt.replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <div class="page-shell hr-app-detail">
    <div class="back" @click="router.push('/hr/applications')">← 返回收件箱</div>

    <div v-if="loading && !app" class="loading">加载中…</div>
    <div v-else-if="!app" class="loading">投递不存在</div>

    <template v-else>
      <div class="head-card">
        <div class="head-main">
          <h1 class="title">{{ app.candidateName || '候选人' }}</h1>
          <p class="meta">
            <span>投递职位：{{ app.jobTitle }}</span>
            <span class="dot">·</span>
            <span>{{ app.companyName }}</span>
            <span class="dot">·</span>
            <span>{{ fmtDateTime(app.appliedAt) }}</span>
          </p>
          <div class="badges">
            <el-tag :type="statusType(app.status)" size="large">{{ statusLabel(app.status) }}</el-tag>
            <el-tag v-if="app.aiScore != null" type="primary" size="large">
              AI 匹配度 {{ app.aiScore }}
            </el-tag>
            <el-tag v-else type="info" size="large">评分中</el-tag>
          </div>
          <p v-if="app.candidateEmail" class="contact">📧 {{ app.candidateEmail }}</p>
          <p v-if="app.candidatePhone" class="contact">📱 {{ app.candidatePhone }}</p>
          <div class="head-actions">
            <el-button
              v-if="app.candidateId"
              type="primary"
              plain
              @click="onContactCandidate"
            >
              💬 联系候选人
            </el-button>
          </div>
        </div>
      </div>

      <div class="grid">
        <!-- 左主区 -->
        <div class="col-main">
          <div class="section">
            <h2 class="section-title">AI 评估</h2>
            <div v-if="app.aiScore != null" class="ai-block">
              <div class="ai-score">{{ app.aiScore }}</div>
              <p class="ai-reason">{{ app.aiReason || '（AI 未给出评分理由）' }}</p>
            </div>
            <div v-else class="ai-block ai-block--pending">
              <div class="ai-score ai-score--pending">—</div>
              <p class="ai-reason">AI 评分中，约 5-15 秒完成；可稍后刷新查看</p>
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
              >
                <div class="history-item">
                  <strong>{{ statusLabel(h.toStatus) }}</strong>
                  <span v-if="h.fromStatus" class="arrow">← {{ statusLabel(h.fromStatus) }}</span>
                  <span class="changer">{{ h.changedByName }}</span>
                  <p v-if="h.note" class="note">{{ h.note }}</p>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>

          <div class="section">
            <h2 class="section-title">简历快照</h2>
            <el-button type="primary" plain :loading="snapshotLoading" @click="loadSnapshot">
              {{ snapshot ? '查看简历快照' : '加载候选人简历' }}
            </el-button>
            <div v-if="showSnapshot && structuredResume" class="resume-card">
              <div class="resume-basic">
                <div class="name">{{ structuredResume.basicName || snapshot?.fullName || '—' }}</div>
                <div class="contact">
                  <span v-if="structuredResume.basicEmail || snapshot?.email">📧 {{ structuredResume.basicEmail || snapshot.email }}</span>
                  <span v-if="structuredResume.basicPhone || snapshot?.phone">📱 {{ structuredResume.basicPhone || snapshot.phone }}</span>
                </div>
              </div>
              <div v-if="structuredResume.selfIntro" class="block">
                <h4>自我介绍</h4>
                <p>{{ structuredResume.selfIntro }}</p>
              </div>
              <div v-if="structuredResume.skills?.length" class="block">
                <h4>技能</h4>
                <div class="skill-tags">
                  <el-tag v-for="s in structuredResume.skills" :key="s" type="info" effect="plain">{{ s }}</el-tag>
                </div>
              </div>
              <div v-if="structuredResume.education?.length" class="block">
                <h4>教育经历</h4>
                <div v-for="(e, i) in structuredResume.education" :key="i" class="block-item">
                  <div class="block-row">
                    <strong>{{ e.school || '—' }}</strong>
                    <span>{{ [e.startDate, e.endDate].filter(Boolean).join(' ~ ') }}</span>
                  </div>
                  <div class="block-sub">{{ [e.degree, e.major].filter(Boolean).join(' · ') }}</div>
                </div>
              </div>
              <div v-if="structuredResume.work?.length" class="block">
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
              <div v-if="structuredResume.projects?.length" class="block">
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
        </div>

        <!-- 右侧操作区 -->
        <div class="col-aside">
          <div v-if="canAdvance" class="section">
            <h2 class="section-title">推进状态</h2>
            <el-form label-position="top">
              <el-form-item label="目标状态">
                <el-select v-model="advanceForm.toStatus" placeholder="选择" style="width: 100%">
                  <el-option v-for="o in advanceOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="备注（拒绝时必填）">
                <el-input v-model="advanceForm.note" type="textarea" :rows="3" maxlength="500" show-word-limit />
              </el-form-item>
              <el-button type="primary" :loading="advancing" @click="onAdvance" style="width: 100%">
                推进
              </el-button>
            </el-form>
          </div>
          <div v-else class="section">
            <p class="aside-tip">当前状态不可推进（终态或撤回）</p>
          </div>

          <div class="section">
            <h2 class="section-title">HR 备注</h2>
            <el-form>
              <el-input v-model="noteForm.content" type="textarea" :rows="3" placeholder="新增备注（仅 HR 可见）" maxlength="2000" show-word-limit />
              <el-button type="primary" plain size="small" @click="onAddNote" style="margin-top: 8px; width: 100%">
                保存备注
              </el-button>
            </el-form>

            <div class="notes-list">
              <div v-for="n in notes" :key="n.id" class="note-item">
                <div class="note-header">
                  <span class="note-author">{{ n.hrUserName }}</span>
                  <span class="note-time">{{ fmtDateTime(n.createdAt) }}</span>
                </div>
                <div v-if="editingNoteId === n.id" class="note-edit">
                  <el-input v-model="editingNoteContent" type="textarea" :rows="3" maxlength="2000" show-word-limit />
                  <div class="note-actions">
                    <el-button size="small" @click="editingNoteId = null">取消</el-button>
                    <el-button size="small" type="primary" @click="saveEditNote">保存</el-button>
                  </div>
                </div>
                <p v-else class="note-content">{{ n.content }}</p>
                <div v-if="editingNoteId !== n.id" class="note-actions">
                  <el-button size="small" link @click="startEditNote(n)">编辑</el-button>
                  <el-button size="small" link type="danger" @click="deleteNote(n)">删除</el-button>
                </div>
              </div>
              <div v-if="notes.length === 0" class="notes-empty">暂无备注</div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.hr-app-detail { padding-top: 96px; }
.back { font-size: 13px; color: var(--text-soft); cursor: pointer; margin-bottom: 14px; display: inline-block; }
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
  padding: 24px 32px;
  margin-bottom: 18px;
  box-shadow: var(--shadow-soft);
}
.title { font-size: 22px; font-weight: 700; color: var(--text); margin: 0 0 8px; }
.meta { font-size: 13px; color: var(--text-soft); margin: 0 0 12px; display: flex; gap: 6px; flex-wrap: wrap; }
.meta .dot { color: var(--text-muted); }
.badges { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.contact { font-size: 13px; color: var(--text-soft); margin: 4px 0 0; }

.grid { display: grid; grid-template-columns: 1fr 320px; gap: 16px; }
@media (max-width: 960px) { .grid { grid-template-columns: 1fr; } }

.section {
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 20px 24px;
  margin-bottom: 14px;
  box-shadow: var(--shadow-soft);
}
.section-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--line);
}

.ai-block {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px;
  background: var(--primary-tint);
  border-radius: 12px;
}
.ai-block--pending { background: var(--panel-soft); }
.ai-score { font-size: 36px; font-weight: 700; color: var(--primary-deep); line-height: 1; }
.ai-score--pending { color: var(--text-muted); }
.ai-reason { margin: 0; font-size: 13px; color: var(--text); line-height: 1.7; flex: 1; }

.timeline { padding: 4px 0; }
.history-item { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.history-item strong { color: var(--text); }
.arrow { color: var(--text-muted); font-size: 13px; }
.changer { font-size: 13px; color: var(--text-soft); }
.note { width: 100%; margin: 4px 0 0; padding: 6px 10px; background: var(--panel-soft); border-radius: 6px; font-size: 13px; color: var(--text-soft); }

.resume-card { margin-top: 14px; display: flex; flex-direction: column; gap: 14px; }
.resume-basic { padding: 12px 14px; background: var(--panel-soft); border-radius: 10px; }
.name { font-size: 16px; font-weight: 600; color: var(--text); }
.contact { display: flex; gap: 16px; margin-top: 4px; font-size: 13px; color: var(--text-soft); flex-wrap: wrap; }
.block h4 { font-size: 13px; font-weight: 600; color: var(--text-soft); margin: 0 0 6px; }
.block p { margin: 0; font-size: 14px; color: var(--text); line-height: 1.7; white-space: pre-wrap; }
.skill-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.block-item { padding: 8px 0; border-bottom: 1px dashed var(--line); }
.block-item:last-child { border-bottom: none; }
.block-row { display: flex; justify-content: space-between; }
.block-row strong { color: var(--text); }
.block-row span { font-size: 12px; color: var(--text-soft); }
.block-sub { font-size: 13px; color: var(--text-soft); margin-top: 2px; }
.block-desc { font-size: 13px; color: var(--text); margin: 4px 0 0; line-height: 1.6; white-space: pre-wrap; }

.aside-tip { color: var(--text-soft); font-size: 13px; margin: 0; text-align: center; padding: 12px 0; }

.notes-list { margin-top: 16px; display: flex; flex-direction: column; gap: 12px; }
.note-item {
  padding: 12px;
  background: var(--panel-soft);
  border-radius: 10px;
}
.note-header { display: flex; justify-content: space-between; margin-bottom: 6px; }
.note-author { font-size: 12px; font-weight: 600; color: var(--text); }
.note-time { font-size: 11px; color: var(--text-muted); }
.note-content { margin: 0; font-size: 13px; color: var(--text); line-height: 1.6; white-space: pre-wrap; }
.note-edit { margin: 6px 0; }
.note-actions { margin-top: 6px; display: flex; gap: 4px; justify-content: flex-end; }
.notes-empty { font-size: 13px; color: var(--text-soft); text-align: center; padding: 12px; }
</style>
