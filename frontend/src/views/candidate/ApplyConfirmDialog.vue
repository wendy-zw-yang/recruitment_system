<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useApplicationStore } from '@/stores/useApplicationStore'
import { useRouter } from 'vue-router'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  job: { type: Object, required: true }
})
const emit = defineEmits(['update:modelValue'])

const router = useRouter()
const store = useApplicationStore()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const form = reactive({
  coverLetter: ''
})
const submitting = ref(false)

async function onConfirm() {
  submitting.value = true
  try {
    const resp = await store.apply({ jobId: props.job.id, coverLetter: form.coverLetter })
    // v0.5：候选人侧不暴露 AI 评分（详见 AI集成.md §6.4.3），
    // toast 也只说"投递成功"，不暗示评分中状态。
    ElMessage.success('投递成功')
    visible.value = false
    form.coverLetter = ''
    if (resp?.applicationId) {
      router.push(`/applications/${resp.applicationId}`)
    }
  } catch (e) {
    ElMessage.error(e?.message || '投递失败')
  } finally {
    submitting.value = false
  }
}

function onCancel() {
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="确认投递"
    width="520px"
    :close-on-click-modal="false"
  >
    <div class="apply-confirm">
      <div class="job-summary">
        <h3 class="job-title">{{ job.title }}</h3>
        <p class="job-meta">
          <span>{{ job.companyName || '—' }}</span>
          <span v-if="job.province || job.cityName" class="dot">·</span>
          <span>{{ [job.province, job.cityName].filter(Boolean).join(' · ') }}</span>
        </p>
      </div>

      <el-form label-position="top">
        <el-form-item label="求职信（可选）">
          <el-input
            v-model="form.coverLetter"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
            placeholder="向 HR 简要介绍你的优势与求职动机（可选，1000 字以内）"
          />
        </el-form-item>
      </el-form>

      <div class="info">
        <p>· 投递后将自动使用你的 ACTIVE 简历快照，简历后续修改不影响本次投递</p>
        <p>· HR 会在工作日内处理你的投递，可在「我的投递」查看进度</p>
      </div>
    </div>

    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onConfirm">确认投递</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.apply-confirm { padding: 0 4px; }
.job-summary {
  background: var(--panel-soft);
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 16px;
}
.job-title { font-size: 16px; font-weight: 600; color: var(--text); margin: 0 0 6px; }
.job-meta {
  font-size: 13px;
  color: var(--text-soft);
  margin: 0;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.dot { color: var(--text-muted); }
.info {
  background: var(--primary-tint);
  border-radius: 10px;
  padding: 12px 14px;
  font-size: 12px;
  color: var(--text-soft);
  line-height: 1.8;
}
.info p { margin: 0; }
</style>
