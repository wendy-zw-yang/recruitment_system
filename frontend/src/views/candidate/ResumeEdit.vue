<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useResumeStore } from '@/stores/useResumeStore'

const store = useResumeStore()

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

const skillInput = ref('')
const fileInput = ref(null)

onMounted(async () => {
  await store.fetchCurrent()
  if (store.current) syncFormFromStore()
})

function syncFormFromStore() {
  const c = store.current
  form.basicName = c.basicName || ''
  form.basicPhone = c.basicPhone || ''
  form.basicEmail = c.basicEmail || ''
  form.selfIntro = c.selfIntro || ''
  form.education = c.education || []
  form.work = c.work || []
  form.projects = c.projects || []
  form.skills = c.skills || []
}

const hasActive = computed(() => !!store.current && !store.current.archived)

async function onUploadChange(uploadFile) {
  const file = uploadFile.raw
  if (!file) return
  try {
    const result = await store.upload(file)
    if (result.aiParsed) {
      ElMessage.success('AI 解析完成，请校对字段')
    } else {
      ElMessage.warning('AI 解析失败，请手动填写')
    }
    syncFormFromStore()
  } catch (e) {
    ElMessage.error('上传失败：' + e.message)
  }
}

function addSkill() {
  const v = skillInput.value.trim()
  if (v && !form.skills.includes(v)) {
    form.skills.push(v)
    skillInput.value = ''
  }
}

function removeSkill(s) {
  form.skills = form.skills.filter((x) => x !== s)
}

async function save() {
  if (!store.current) return
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
  } catch (e) {
    ElMessage.error('保存失败：' + e.message)
  }
}

async function archive() {
  if (!store.current) return
  try {
    await ElMessageBox.confirm('归档当前简历后将无法再编辑，需上传新简历。确认继续？', '提示', {
      type: 'warning'
    })
  } catch {
    return
  }
  await store.archive(store.current.id)
  ElMessage.success('已归档，可上传新简历')
}
</script>

<template>
  <div class="resume-page">
    <el-card v-if="!hasActive">
      <template #header>
        <span>上传简历，开启求职之旅</span>
      </template>
      <p class="hint">上传一份 PDF 或 Word 简历，平台会帮你把内容自动整理到下面的表单里。</p>
      <el-upload
        ref="fileInput"
        :auto-upload="false"
        :show-file-list="false"
        accept=".pdf,.docx"
        :on-change="onUploadChange"
        drag
      >
        <el-icon style="font-size: 48px"><el-icon-upload-filled /></el-icon>
        <div class="el-upload__text">拖拽简历到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 PDF / .docx，最大 20 MB</div>
        </template>
      </el-upload>
      <el-progress v-if="store.uploadProgress > 0 && store.uploadProgress < 100" :percentage="store.uploadProgress" />
    </el-card>

    <el-card v-else>
      <template #header>
        <div class="card-header">
          <span>我的简历</span>
          <div class="actions">
            <el-button @click="archive">重新上传（先归档当前）</el-button>
            <el-button type="primary" @click="save">保存</el-button>
          </div>
        </div>
      </template>

      <el-form label-width="100px">
        <el-form-item label="附件">
          <span>{{ store.current.attachmentFileName || '无' }}</span>
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.basicName" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.basicPhone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.basicEmail" />
        </el-form-item>
        <el-form-item label="教育经历">
          <div v-for="(item, idx) in form.education" :key="idx" class="block">
            <el-input v-model="item.school" placeholder="学校" class="mr-1" />
            <el-input v-model="item.major" placeholder="专业" class="mr-1" />
            <el-input v-model="item.degree" placeholder="学历" class="mr-1" />
            <el-button type="danger" link @click="form.education.splice(idx, 1)">删除</el-button>
          </div>
          <el-button @click="form.education.push({})">+ 添加</el-button>
        </el-form-item>
        <el-form-item label="工作经历">
          <div v-for="(item, idx) in form.work" :key="idx" class="block">
            <el-input v-model="item.company" placeholder="公司" class="mr-1" />
            <el-input v-model="item.position" placeholder="职位" class="mr-1" />
            <el-button type="danger" link @click="form.work.splice(idx, 1)">删除</el-button>
          </div>
          <el-button @click="form.work.push({})">+ 添加</el-button>
        </el-form-item>
        <el-form-item label="项目经历">
          <div v-for="(item, idx) in form.projects" :key="idx" class="block">
            <el-input v-model="item.name" placeholder="项目名" class="mr-1" />
            <el-input v-model="item.role" placeholder="角色" class="mr-1" />
            <el-button type="danger" link @click="form.projects.splice(idx, 1)">删除</el-button>
          </div>
          <el-button @click="form.projects.push({})">+ 添加</el-button>
        </el-form-item>
        <el-form-item label="技能">
          <div class="skill-row">
            <el-tag v-for="s in form.skills" :key="s" closable @close="removeSkill(s)" class="skill-tag">
              {{ s }}
            </el-tag>
            <el-input v-model="skillInput" placeholder="输入技能后回车" class="skill-input" @keyup.enter="addSkill" />
          </div>
        </el-form-item>
        <el-form-item label="自我介绍">
          <el-input v-model="form.selfIntro" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.resume-page { max-width: 960px; margin: 0 auto; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.actions { display: flex; gap: 8px; }
.hint { color: #909399; margin-bottom: 16px; }
.block { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.mr-1 { margin-right: 8px; }
.skill-row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.skill-tag { margin-right: 4px; }
.skill-input { width: 200px; }
</style>
