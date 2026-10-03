<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { jobApi } from '@/api/job'
import { dictApi } from '@/api/dict'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  job: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const form = reactive({
  title: '',
  industryId: null,
  province: '',
  cityId: null,
  salaryMin: null,
  salaryMax: null,
  description: '',
  requirements: '',
  keywords: ''
})

const industries = ref([])
const cities = ref([])
const polishing = ref(false)
const submitting = ref(false)

const drawerTitle = computed(() => (props.job?.id ? '编辑职位' : '新建职位'))
const isEdit = computed(() => Boolean(props.job?.id))
const industryName = computed(() => industries.value.find((i) => i.id === form.industryId)?.name || '')
const cityName = computed(() => cities.value.find((c) => c.id === form.cityId)?.name || '')

const provinceOptions = computed(() => {
  const set = new Set(cities.value.map((c) => c.province).filter(Boolean))
  return Array.from(set).sort((a, b) => a.localeCompare(b, 'zh-CN'))
})

const cityOptions = computed(() => {
  if (!form.province) return cities.value
  return cities.value.filter((c) => c.province === form.province)
})

const canPolish = computed(() => Boolean(form.title.trim()))

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    if (industries.value.length === 0) {
      const [ind, cit] = await Promise.all([dictApi.industries(), dictApi.cities()])
      industries.value = ind || []
      cities.value = cit || []
    }
    if (props.job) {
      Object.assign(form, {
        title: props.job.title || '',
        industryId: props.job.industryId || null,
        province: props.job.province || '',
        cityId: props.job.cityId || null,
        salaryMin: props.job.salaryMin ?? null,
        salaryMax: props.job.salaryMax ?? null,
        description: props.job.description || '',
        requirements: props.job.requirements || '',
        keywords: props.job.keywords || ''
      })
    } else {
      Object.assign(form, {
        title: '', industryId: null, province: '', cityId: null,
        salaryMin: null, salaryMax: null,
        description: '', requirements: '', keywords: ''
      })
    }
  }
)

watch(() => form.province, (val) => {
  if (val && form.cityId) {
    const matched = cityOptions.value.find((c) => c.id === form.cityId)
    if (!matched) form.cityId = null
  }
})

function close() {
  emit('update:modelValue', false)
}

async function onPolish() {
  if (!canPolish.value) {
    ElMessage.warning('请先填写职位标题')
    return
  }
  polishing.value = true
  try {
    const res = await jobApi.polish({
      title: form.title.trim(),
      industryName: industryName.value,
      cityName: cityName.value,
      salaryMin: form.salaryMin,
      salaryMax: form.salaryMax,
      description: form.description,
      requirements: form.requirements,
      keywords: form.keywords
    })
    if (res.description) form.description = res.description
    if (res.requirements) form.requirements = res.requirements
    if (res.keywords) form.keywords = res.keywords
    ElMessage.success('AI 润色完成，请校对 3 个字段')
  } catch (e) {
    ElMessage.error(e.message || 'AI 润色失败')
  } finally {
    polishing.value = false
  }
}

async function onSubmit() {
  if (!form.title.trim()) return ElMessage.warning('请填写职位标题')
  if (!form.province) return ElMessage.warning('请选择省份')
  if (!form.cityId) return ElMessage.warning('请选择城市')
  if (!form.description.trim()) return ElMessage.warning('请填写岗位职责')
  if (!form.requirements.trim()) return ElMessage.warning('请填写任职要求')
  if (form.salaryMin != null && form.salaryMax != null && form.salaryMin > form.salaryMax) {
    return ElMessage.warning('薪资下限不能大于上限')
  }
  submitting.value = true
  try {
    const payload = {
      title: form.title.trim(),
      industryId: form.industryId || null,
      province: form.province,
      cityId: form.cityId,
      salaryMin: form.salaryMin,
      salaryMax: form.salaryMax,
      description: form.description.trim(),
      requirements: form.requirements.trim(),
      keywords: form.keywords.trim() || null
    }
    if (isEdit.value) {
      await jobApi.update(props.job.id, payload)
    } else {
      await jobApi.create(payload)
    }
    ElMessage.success(isEdit.value ? '已保存，状态回到待审核' : '已保存为草稿')
    emit('saved')
    close()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="drawerTitle"
    direction="rtl"
    size="720px"
    :close-on-click-modal="false"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <el-form label-width="100px" class="job-form">
      <!-- ============ 段 1：基本信息（元数据，不参与润色） ============ -->
      <div class="section-title">基本信息</div>

      <el-form-item label="职位标题" required>
        <el-input v-model="form.title" placeholder="例如：Java 后端开发工程师" maxlength="80" show-word-limit />
      </el-form-item>

      <el-form-item label="行业">
        <el-select v-model="form.industryId" placeholder="请选择行业" clearable filterable>
          <el-option v-for="i in industries" :key="i.id" :label="i.name" :value="i.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="省份" required>
        <el-select v-model="form.province" placeholder="请选择省份" filterable clearable>
          <el-option v-for="p in provinceOptions" :key="p" :label="p" :value="p" />
        </el-select>
      </el-form-item>

      <el-form-item label="城市" required>
        <el-select v-model="form.cityId" placeholder="请先选择省份" :disabled="!form.province" filterable clearable>
          <el-option v-for="c in cityOptions" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="薪资范围">
        <div class="salary-row">
          <el-input-number v-model="form.salaryMin" :min="0" :step="1" placeholder="K" controls-position="right" />
          <span class="dash">—</span>
          <el-input-number v-model="form.salaryMax" :min="0" :step="1" placeholder="K" controls-position="right" />
          <span class="unit">K / 月</span>
        </div>
      </el-form-item>

      <!-- ============ 段 2：内容描述（AI 润色目标） ============ -->
      <div class="section-title section-title--with-action">
        <span>内容描述</span>
        <el-button
          type="primary"
          plain
          :icon="undefined"
          :loading="polishing"
          :disabled="!canPolish"
          @click="onPolish"
        >
          ✨ 一键润色全部内容
        </el-button>
      </div>
      <p class="section-hint">填写职位标题后启用；点击后 AI 会基于现有草稿与上下文整体润色下方 3 个字段，可手动校对。</p>

      <el-form-item label="岗位职责" required>
        <el-input v-model="form.description" type="textarea" :rows="8" placeholder="至少 3 条职责" />
      </el-form-item>

      <el-form-item label="任职要求" required>
        <el-input v-model="form.requirements" type="textarea" :rows="8" placeholder="至少 3 条要求" />
      </el-form-item>

      <el-form-item label="关键词 / 补充">
        <el-input v-model="form.keywords" type="textarea" :rows="3" placeholder="可选：行业技能、工具、经验要求等，逗号分隔" />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">
          {{ isEdit ? '保存修改' : '保存为草稿' }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.job-form { padding: 0 8px; }
.section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
  padding: 16px 0 8px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 16px;
}
.section-title--with-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin: -8px 0 16px;
}
.salary-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.salary-row .dash { color: var(--text-muted); }
.salary-row .unit { color: var(--text-soft); margin-left: 4px; }
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
