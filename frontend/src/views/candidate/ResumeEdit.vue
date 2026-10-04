<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { useResumeStore } from '@/stores/useResumeStore'

const store = useResumeStore()

/**
 * 模式：
 *  - 'upload'：未上传状态，展示拖拽上传卡
 *  - 'view'：查看模式，卡片式排版（无 input 边框），底部按钮 [重新上传] [编辑] [删除]
 *  - 'edit'：编辑模式，表单字段 enabled，底部按钮 [取消] [保存]
 *
 * 关键设计：
 *  - 单一 page 容器，view / edit 通过不同子区域切换
 *  - 编辑已保存简历 vs 编辑新上传简历，区分「取消」行为：
 *      · isNewResume=true  → 取消 = 删除这条简历 + 回到 upload
 *      · isNewResume=false → 取消 = 撤销编辑 + 回到 view
 */
const mode = ref('upload')
const isNewResume = ref(false) // 当前 edit 的简历是否还未保存过
const dirty = ref(false)
const aiParsing = ref(false)

const form = reactive({
  basicName: '',
  basicPhone: '',
  basicEmail: '',
  selfIntro: '',
  education: [],
  work: [],
  projects: [],
  skills: []
})

const fileInput = ref(null)
const skillInputRef = ref(null)
const newSkill = ref('')

onMounted(async () => {
  await store.fetchCurrent()
  syncFromStore()
  if (store.current && !store.current.archived) {
    mode.value = 'view'
    isNewResume.value = false
  } else {
    mode.value = 'upload'
  }
  console.log('[ResumeEdit] mounted, mode =', mode.value, 'hasActive =', hasActive.value)
})

function syncFromStore() {
  const c = store.current
  if (!c) {
    form.basicName = ''
    form.basicPhone = ''
    form.basicEmail = ''
    form.selfIntro = ''
    form.education = []
    form.work = []
    form.projects = []
    form.skills = []
  } else {
    form.basicName = c.basicName || ''
    form.basicPhone = c.basicPhone || ''
    form.basicEmail = c.basicEmail || ''
    form.selfIntro = c.selfIntro || ''
    form.education = deepCopy(c.education || [])
    form.work = deepCopy(c.work || [])
    form.projects = deepCopy(c.projects || [])
    form.skills = deepCopy(c.skills || [])
  }
  dirty.value = false
}

function deepCopy(v) {
  return JSON.parse(JSON.stringify(v))
}

const hasActive = computed(() => !!store.current && !store.current.archived)
const isEditing = computed(() => mode.value === 'edit')

async function onUploadChange(uploadFile) {
  const file = uploadFile.raw
  if (!file) return
  console.log('[ResumeEdit] onUploadChange start, file =', file.name)
  aiParsing.value = true
  const loadingMsg = ElMessage({
    message: 'AI 解析中，请稍候（约 5-15 秒）...',
    type: 'info',
    duration: 0
  })
  try {
    const result = await store.upload(file)
    loadingMsg.close()
    console.log('[ResumeEdit] upload result', {
      aiParsed: result?.aiParsed,
      educationCount: result?.resume?.education?.length,
      workCount: result?.resume?.work?.length,
      projectsCount: result?.resume?.projects?.length,
    })
    if (result.aiParsed) {
      ElMessage.success('AI 解析完成，请校对字段后保存')
    } else {
      ElMessage.warning('AI 解析失败，请手动填写')
    }
    syncFromStore()
    isNewResume.value = true
    mode.value = 'edit'
    dirty.value = false
    console.log('[ResumeEdit] upload done, mode -> edit, isNewResume=true')
  } catch (e) {
    loadingMsg.close()
    console.error('[ResumeEdit] upload failed', e)
    ElMessage.error('上传失败：' + (e?.message || '未知错误'))
  } finally {
    aiParsing.value = false
  }
}

function enterEdit() {
  console.log('[ResumeEdit] enterEdit, mode =', mode.value)
  if (mode.value !== 'view') return
  syncFromStore()
  isNewResume.value = false // 编辑已保存的简历
  mode.value = 'edit'
  dirty.value = false
}

async function cancelEdit() {
  console.log('[ResumeEdit] cancelEdit, isNewResume =', isNewResume.value)
  if (isNewResume.value) {
    // 新上传未保存 → 删除这条简历，回到 upload
    if (!window.confirm('已上传但未保存的简历将被删除，确认放弃？')) {
      console.log('[ResumeEdit] cancel cancelled')
      return
    }
    try {
      const targetId = store.current?.id
      if (targetId) {
        await store.remove(targetId)
      }
      ElMessage.info('已放弃上传')
      window.location.reload()
    } catch (e) {
      console.error('[ResumeEdit] cancel failed', e)
      ElMessage.error('取消失败：' + (e?.message || '未知错误'))
    }
  } else {
    // 已保存后进入编辑 → 撤销编辑，回到 view
    syncFromStore()
    mode.value = 'view'
  }
}

async function save() {
  if (!store.current) {
    ElMessage.warning('没有可保存的简历')
    return
  }
  console.log('[ResumeEdit] save, resumeId =', store.current.id, 'dirty =', dirty.value)
  try {
    await store.save(store.current.id, {
      basicName: form.basicName,
      basicPhone: form.basicPhone,
      basicEmail: form.basicEmail,
      selfIntro: form.selfIntro,
      education: form.education,
      work: form.work,
      projects: form.projects,
      skills: form.skills
    })
    ElMessage.success('保存成功')
    syncFromStore()
    isNewResume.value = false // 保存后变成「已保存」
    mode.value = 'view'
  } catch (e) {
    console.error('[ResumeEdit] save failed', e)
    ElMessage.error('保存失败：' + (e?.message || '未知错误'))
  }
}

async function reupload() {
  console.log('[ResumeEdit] reupload click, currentId =', store.current?.id)
  if (!store.current) {
    ElMessage.warning('当前没有简历，无需重新上传')
    return
  }
  if (!window.confirm('重新上传将删除当前简历并触发新的 AI 解析，确认继续？')) {
    console.log('[ResumeEdit] reupload cancelled')
    return
  }
  try {
    const targetId = store.current.id
    await store.remove(targetId)
    ElMessage.success('当前简历已删除，请上传新版本')
    window.location.reload()
  } catch (e) {
    console.error('[ResumeEdit] reupload failed', e)
    ElMessage.error('操作失败：' + (e?.message || '未知错误'))
  }
}

async function remove() {
  console.log('[ResumeEdit] delete click, currentId =', store.current?.id)
  if (!store.current) {
    ElMessage.warning('当前没有简历可删除')
    return
  }
  if (!window.confirm('删除当前简历？删除后必须重新上传才能继续投递。')) {
    console.log('[ResumeEdit] delete cancelled')
    return
  }
  try {
    const targetId = store.current.id
    await store.remove(targetId)
    ElMessage.success('简历已删除，请上传新简历')
    window.location.reload()
  } catch (e) {
    console.error('[ResumeEdit] delete failed', e)
    ElMessage.error('删除失败：' + (e?.message || '未知错误'))
  }
}

function markDirty() {
  if (mode.value === 'edit') dirty.value = true
}

function addSkill() {
  if (mode.value !== 'edit') return
  const v = newSkill.value.trim()
  if (v && !form.skills.includes(v)) {
    form.skills.push(v)
    newSkill.value = ''
    dirty.value = true
  }
}

function removeSkill(s) {
  form.skills = form.skills.filter((x) => x !== s)
  dirty.value = true
}

function addEducation() {
  form.education.push({})
  dirty.value = true
}
function removeEducation(idx) {
  form.education.splice(idx, 1)
  dirty.value = true
}
function addWork() {
  form.work.push({ tags: [] })
  dirty.value = true
}
function removeWork(idx) {
  form.work.splice(idx, 1)
  dirty.value = true
}
function addProject() {
  form.projects.push({ techStack: [] })
  dirty.value = true
}
function removeProject(idx) {
  form.projects.splice(idx, 1)
  dirty.value = true
}

function joinList(arr, sep = ' / ') {
  if (!arr || arr.length === 0) return ''
  return arr.join(sep)
}

function formatDateRange(start, end) {
  const s = start || '?'
  const e = end || '至今'
  return `${s} ~ ${e}`
}

function isBlank(v) {
  return v === null || v === undefined || v === ''
}
</script>

<template>
  <div class="resume-page">
    <!-- 模式 1：未上传 -->
    <div v-if="mode === 'upload'" data-testid="mode-upload">
      <el-card>
        <template #header>
          <span>上传简历</span>
        </template>
        <p class="hint">
          上传一份 PDF 或 Word 简历，平台会调用 AI 把内容自动整理到下面的表单里，你可以再手动修改后保存。
        </p>
        <el-upload
          ref="fileInput"
          :auto-upload="false"
          :show-file-list="false"
          accept=".pdf,.docx"
          :on-change="onUploadChange"
          drag
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽简历到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 PDF / .docx，最大 20 MB</div>
          </template>
        </el-upload>
        <el-progress
          v-if="store.uploadProgress > 0 && store.uploadProgress < 100"
          :percentage="store.uploadProgress"
        />
      </el-card>
    </div>

    <!-- 模式 2：view 模式（卡片式展示，无 input 边框） -->
    <div v-else-if="mode === 'view'" data-testid="mode-view">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>我的简历</span>
            <span class="muted small">{{ store.current?.attachmentFileName ? '附件：' + store.current.attachmentFileName : '无附件' }}</span>
          </div>
        </template>

        <div class="resume-view">
          <div class="basic">
            <div class="name">{{ store.current.basicName || '未填写姓名' }}</div>
            <div class="contact">
              <span>{{ store.current.basicPhone || '电话未填写' }}</span>
              <span class="divider">|</span>
              <span>{{ store.current.basicEmail || '邮箱未填写' }}</span>
            </div>
          </div>

          <section class="block">
            <h3>教育经历</h3>
            <div v-if="!store.current.education || store.current.education.length === 0" class="empty">暂无</div>
            <div v-else>
              <div v-for="(e, idx) in store.current.education" :key="idx" class="item">
                <div class="item-head">
                  <strong>{{ e.school || '学校未填写' }}</strong>
                  <span class="muted">
                    {{ e.major || '' }}{{ e.degree ? ' · ' + e.degree : '' }}
                  </span>
                </div>
                <div class="muted small">{{ formatDateRange(e.startDate, e.endDate) }}</div>
                <div v-if="!isBlank(e.description)" class="desc">{{ e.description }}</div>
              </div>
            </div>
          </section>

          <section class="block">
            <h3>工作经历</h3>
            <div v-if="!store.current.work || store.current.work.length === 0" class="empty">暂无</div>
            <div v-else>
              <div v-for="(w, idx) in store.current.work" :key="idx" class="item">
                <div class="item-head">
                  <strong>{{ w.company || '公司未填写' }}</strong>
                  <span class="muted">{{ w.position || '' }}</span>
                </div>
                <div class="muted small">{{ formatDateRange(w.startDate, w.endDate) }}</div>
                <div v-if="!isBlank(w.description)" class="desc">{{ w.description }}</div>
              </div>
            </div>
          </section>

          <section class="block">
            <h3>项目经历</h3>
            <div v-if="!store.current.projects || store.current.projects.length === 0" class="empty">暂无</div>
            <div v-else>
              <div v-for="(p, idx) in store.current.projects" :key="idx" class="item">
                <div class="item-head">
                  <strong>{{ p.name || '项目名未填写' }}</strong>
                  <span class="muted">{{ p.role || '' }}</span>
                </div>
                <div class="muted small">{{ formatDateRange(p.startDate, p.endDate) }}</div>
                <div v-if="!isBlank(p.description)" class="desc">{{ p.description }}</div>
              </div>
            </div>
          </section>

          <section class="block">
            <h3>技能</h3>
            <div v-if="!store.current.skills || store.current.skills.length === 0" class="empty">暂无</div>
            <div v-else class="tags">
              <el-tag v-for="s in store.current.skills" :key="s">{{ s }}</el-tag>
            </div>
          </section>

          <section class="block">
            <h3>自我介绍</h3>
            <div v-if="!store.current.selfIntro" class="empty">暂无</div>
            <div v-else class="self-intro">{{ store.current.selfIntro }}</div>
          </section>
        </div>

        <div class="footer-actions" data-testid="footer-actions">
          <el-button
            type="primary"
            plain
            @click="reupload"
            data-testid="btn-reupload"
          >重新上传</el-button>
          <el-button
            type="primary"
            @click="enterEdit"
            data-testid="btn-edit"
          >编辑</el-button>
          <el-button
            type="danger"
            plain
            @click="remove"
            data-testid="btn-delete"
          >删除</el-button>
        </div>
      </el-card>
    </div>

    <!-- 模式 3：edit 模式（表单，可编辑） -->
    <div v-else-if="mode === 'edit'" data-testid="mode-edit">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>{{ isNewResume ? '校对 AI 解析结果（未保存）' : '编辑简历' }}</span>
            <span class="muted small">
              <span v-if="dirty" class="dirty-dot">●</span>
              {{ isNewResume ? '点保存生效；点取消会删除这条简历' : '有改动会标记' }}
            </span>
          </div>
        </template>

        <el-form label-width="100px" label-position="right">
          <el-form-item label="附件">
            <span>{{ store.current?.attachmentFileName || '无' }}</span>
            <span class="muted small ml-2">
              编辑不触发 AI；如需更换附件请点底部「取消」后重新上传
            </span>
          </el-form-item>

          <el-form-item label="姓名">
            <el-input v-model="form.basicName" placeholder="姓名" @input="markDirty" />
          </el-form-item>
          <el-form-item label="电话">
            <el-input v-model="form.basicPhone" placeholder="电话" @input="markDirty" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="form.basicEmail" placeholder="邮箱" @input="markDirty" />
          </el-form-item>

          <el-form-item label="教育经历">
            <div v-if="form.education.length === 0" class="empty-line">暂无</div>
            <div v-for="(item, idx) in form.education" :key="idx" class="entry">
              <div class="entry-row">
                <el-input v-model="item.school" placeholder="学校" class="cell" @input="markDirty" />
                <el-input v-model="item.major" placeholder="专业" class="cell" @input="markDirty" />
                <el-input v-model="item.degree" placeholder="学历" class="cell" @input="markDirty" />
                <el-input v-model="item.startDate" placeholder="开始 YYYY-MM" class="cell-sm" @input="markDirty" />
                <el-input v-model="item.endDate" placeholder="结束 YYYY-MM" class="cell-sm" @input="markDirty" />
                <el-button type="danger" link @click="removeEducation(idx)">删除</el-button>
              </div>
              <el-input
                v-model="item.description"
                type="textarea"
                :rows="2"
                placeholder="描述"
                class="entry-textarea"
                @input="markDirty"
              />
            </div>
            <el-button @click="addEducation" class="add-btn">+ 添加教育经历</el-button>
          </el-form-item>

          <el-form-item label="工作经历">
            <div v-if="form.work.length === 0" class="empty-line">暂无</div>
            <div v-for="(item, idx) in form.work" :key="idx" class="entry">
              <div class="entry-row">
                <el-input v-model="item.company" placeholder="公司" class="cell" @input="markDirty" />
                <el-input v-model="item.position" placeholder="职位" class="cell" @input="markDirty" />
                <el-input v-model="item.startDate" placeholder="开始 YYYY-MM" class="cell-sm" @input="markDirty" />
                <el-input v-model="item.endDate" placeholder="结束 YYYY-MM" class="cell-sm" @input="markDirty" />
                <el-button type="danger" link @click="removeWork(idx)">删除</el-button>
              </div>
              <el-input
                v-model="item.description"
                type="textarea"
                :rows="2"
                placeholder="描述"
                class="entry-textarea"
                @input="markDirty"
              />
            </div>
            <el-button @click="addWork" class="add-btn">+ 添加工作经历</el-button>
          </el-form-item>

          <el-form-item label="项目经历">
            <div v-if="form.projects.length === 0" class="empty-line">暂无</div>
            <div v-for="(item, idx) in form.projects" :key="idx" class="entry">
              <div class="entry-row">
                <el-input v-model="item.name" placeholder="项目名" class="cell" @input="markDirty" />
                <el-input v-model="item.role" placeholder="角色" class="cell" @input="markDirty" />
                <el-input v-model="item.startDate" placeholder="开始 YYYY-MM" class="cell-sm" @input="markDirty" />
                <el-input v-model="item.endDate" placeholder="结束 YYYY-MM" class="cell-sm" @input="markDirty" />
                <el-button type="danger" link @click="removeProject(idx)">删除</el-button>
              </div>
              <el-input
                v-model="item.description"
                type="textarea"
                :rows="2"
                placeholder="描述"
                class="entry-textarea"
                @input="markDirty"
              />
            </div>
            <el-button @click="addProject" class="add-btn">+ 添加项目经历</el-button>
          </el-form-item>

          <el-form-item label="技能">
            <div class="skill-row">
              <el-tag
                v-for="s in form.skills"
                :key="s"
                closable
                class="skill-tag"
                @close="removeSkill(s)"
              >
                {{ s }}
              </el-tag>
              <el-input
                ref="skillInputRef"
                v-model="newSkill"
                placeholder="输入技能后回车（支持中文/英文）"
                class="skill-input"
                @keyup.enter="addSkill"
              />
            </div>
          </el-form-item>

          <el-form-item label="自我介绍">
            <el-input
              v-model="form.selfIntro"
              type="textarea"
              :rows="4"
              placeholder="自我介绍"
              @input="markDirty"
            />
          </el-form-item>
        </el-form>

        <div class="footer-actions" data-testid="footer-actions">
          <el-button @click="cancelEdit" data-testid="btn-cancel">取消</el-button>
          <el-button
            type="primary"
            :loading="aiParsing"
            @click="save"
            data-testid="btn-save"
          >保存</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.resume-page {
  max-width: 880px;
  margin: 0 auto;
  padding: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.card-header .dirty-dot {
  color: #f56c6c;
  margin-right: 4px;
}
.muted { color: #909399; }
.small { font-size: 12px; }
.ml-2 { margin-left: 8px; }
.hint { color: #909399; margin-bottom: 16px; }

.upload-icon {
  font-size: 48px;
  color: var(--primary, #1d6fd8);
  margin-bottom: 8px;
}

/* view 模式：卡片式展示（无 input 边框） */
.resume-view .basic {
  padding: 8px 0 16px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 16px;
}
.resume-view .basic .name {
  font-size: 22px;
  font-weight: 600;
  color: var(--text, #303133);
}
.resume-view .basic .contact {
  margin-top: 6px;
  color: var(--text-soft, #606266);
  font-size: 14px;
}
.resume-view .block {
  padding: 12px 0;
  border-bottom: 1px dashed #ebeef5;
}
.resume-view .block:last-child { border-bottom: 0; }
.resume-view h3 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--primary, #1d6fd8);
}
.resume-view .empty {
  color: var(--text-muted, #909399);
  font-size: 13px;
}
.resume-view .item {
  margin-bottom: 12px;
  padding: 10px 14px;
  background: #fafbfc;
  border-radius: 6px;
}
.resume-view .item:last-child { margin-bottom: 0; }
.resume-view .item-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}
.resume-view .item-head strong {
  font-size: 14px;
  color: var(--text, #303133);
}
.resume-view .desc {
  margin-top: 4px;
  color: var(--text-soft, #606266);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.resume-view .tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
.resume-view .self-intro {
  color: var(--text-soft, #606266);
  line-height: 1.7;
  white-space: pre-wrap;
}
.divider { margin: 0 8px; color: #dcdfe6; }

/* edit 模式：表单编辑 */
.entry {
  width: 100%;
  margin-bottom: 12px;
  padding: 12px;
  background: #fafbfc;
  border-radius: 6px;
}
.entry-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.entry-textarea {
  width: 100%;
  margin-top: 8px;
}
.cell { min-width: 140px; flex: 1; }
.cell-sm { width: 130px; }
.empty-line {
  color: var(--text-muted, #909399);
  font-size: 13px;
  padding: 4px 0;
}
.add-btn { margin-top: 4px; }

.skill-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.skill-tag { margin-right: 4px; }
.skill-input { width: 200px; }

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid var(--line, #ebeef5);
}
</style>
