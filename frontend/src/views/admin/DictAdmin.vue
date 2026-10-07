<script setup>
import { onMounted, reactive, ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dictApi } from '@/api/dict'

const activeTab = ref('industry')

// ============ 行业 ============
const industries = ref([])
const industriesLoading = ref(false)

async function loadIndustries() {
  industriesLoading.value = true
  try {
    industries.value = (await dictApi.industries()) || []
  } catch (e) {
    ElMessage.error(e?.message || '加载行业失败')
  } finally {
    industriesLoading.value = false
  }
}

// 一级行业列表（用于弹窗的"父级"下拉）
const topLevelIndustries = computed(() =>
  industries.value.filter((i) => !i.parentId)
)

// 行业名称映射（用于显示二级行业的父级名）
const industryNameMap = computed(() => {
  const m = {}
  industries.value.forEach((i) => { m[i.id] = i })
  return m
})

// 行业表格数据：插入"父级名称"展示列
const industryTableData = computed(() =>
  industries.value.map((i) => ({
    ...i,
    parentName: i.parentId ? (industryNameMap.value[i.parentId]?.name || '—') : '—'
  }))
)

const industryDialog = reactive({
  visible: false,
  mode: 'add', // 'add' | 'edit'
  form: { id: null, name: '', parentId: null, sortOrder: 0 },
  errors: {}
})

function openAddIndustry() {
  industryDialog.mode = 'add'
  industryDialog.form = { id: null, name: '', parentId: null, sortOrder: 0 }
  industryDialog.errors = {}
  industryDialog.visible = true
}

function openEditIndustry(row) {
  industryDialog.mode = 'edit'
  industryDialog.form = {
    id: row.id,
    name: row.name || '',
    parentId: row.parentId || null,
    sortOrder: row.sortOrder || 0
  }
  industryDialog.errors = {}
  industryDialog.visible = true
}

function validateIndustryForm() {
  const e = {}
  if (!industryDialog.form.name || !industryDialog.form.name.trim()) {
    e.name = '名称不能为空'
  } else if (industryDialog.form.name.length > 128) {
    e.name = '名称过长（≤128）'
  }
  // 一级行业不能选 parentId 为其他顶级行业或自己
  if (industryDialog.form.parentId) {
    const parent = industryTreeMap.value[industryDialog.form.parentId]
    if (parent && parent.parentId) {
      e.parentId = '父级必须为一级行业'
    }
    if (industryDialog.form.id && industryDialog.form.parentId === industryDialog.form.id) {
      e.parentId = '不能将自己设为父级'
    }
  }
  industryDialog.errors = e
  return Object.keys(e).length === 0
}

// 行业 → 子级 映射（用于"删除前校验是否被引用"）
const industryTreeMap = computed(() => {
  const m = {}
  industries.value.forEach((i) => { m[i.id] = i })
  return m
})

async function submitIndustry() {
  if (!validateIndustryForm()) return
  const payload = {
    name: industryDialog.form.name.trim(),
    parentId: industryDialog.form.parentId || null,
    sortOrder: industryDialog.form.sortOrder || 0
  }
  try {
    if (industryDialog.mode === 'add') {
      await dictApi.addIndustry(payload)
      ElMessage.success('已新增行业')
    } else {
      await dictApi.updateIndustry(industryDialog.form.id, payload)
      ElMessage.success('已更新')
    }
    industryDialog.visible = false
    loadIndustries()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function deleteIndustry(row) {
  // 校验：是否被二级行业引用
  const hasChild = industries.value.some((i) => i.parentId === row.id)
  if (hasChild) {
    return ElMessage.warning('请先删除该行业下的子行业')
  }
  try {
    await ElMessageBox.confirm(
      `确认删除行业「${row.name}」？已关联数据保留原值，仅隐藏新选择`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await dictApi.deleteIndustry(row.id)
    ElMessage.success('已删除')
    loadIndustries()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

// ============ 城市 ============
const cities = ref([])
const citiesLoading = ref(false)

async function loadCities() {
  citiesLoading.value = true
  try {
    cities.value = (await dictApi.cities()) || []
  } catch (e) {
    ElMessage.error(e?.message || '加载城市失败')
  } finally {
    citiesLoading.value = false
  }
}

const cityDialog = reactive({
  visible: false,
  mode: 'add',
  form: { id: null, name: '', province: '', sortOrder: 0 },
  errors: {}
})

function openAddCity() {
  cityDialog.mode = 'add'
  cityDialog.form = { id: null, name: '', province: '', sortOrder: 0 }
  cityDialog.errors = {}
  cityDialog.visible = true
}

function openEditCity(row) {
  cityDialog.mode = 'edit'
  cityDialog.form = {
    id: row.id,
    name: row.name || '',
    province: row.province || '',
    sortOrder: row.sortOrder || 0
  }
  cityDialog.errors = {}
  cityDialog.visible = true
}

function validateCityForm() {
  const e = {}
  if (!cityDialog.form.name || !cityDialog.form.name.trim()) {
    e.name = '城市名不能为空'
  } else if (cityDialog.form.name.length > 128) {
    e.name = '名称过长（≤128）'
  }
  if (!cityDialog.form.province || !cityDialog.form.province.trim()) {
    e.province = '省份不能为空'
  } else if (cityDialog.form.province.length > 64) {
    e.province = '省份过长（≤64）'
  }
  cityDialog.errors = e
  return Object.keys(e).length === 0
}

async function submitCity() {
  if (!validateCityForm()) return
  const payload = {
    name: cityDialog.form.name.trim(),
    province: cityDialog.form.province.trim(),
    sortOrder: cityDialog.form.sortOrder || 0
  }
  try {
    if (cityDialog.mode === 'add') {
      await dictApi.addCity(payload)
      ElMessage.success('已新增城市')
    } else {
      await dictApi.updateCity(cityDialog.form.id, payload)
      ElMessage.success('已更新')
    }
    cityDialog.visible = false
    loadCities()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function deleteCity(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除城市「${row.province} · ${row.name}」？`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await dictApi.deleteCity(row.id)
    ElMessage.success('已删除')
    loadCities()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

// ============ 技能建议池 ============
const skills = ref([])
const skillsLoading = ref(false)

async function loadSkills() {
  skillsLoading.value = true
  try {
    skills.value = (await dictApi.skillSuggestions()) || []
  } catch (e) {
    ElMessage.error(e?.message || '加载技能失败')
  } finally {
    skillsLoading.value = false
  }
}

const skillDialog = reactive({
  visible: false,
  mode: 'add',
  form: { id: null, name: '', sortOrder: 0 },
  errors: {}
})

function openAddSkill() {
  skillDialog.mode = 'add'
  skillDialog.form = { id: null, name: '', sortOrder: 0 }
  skillDialog.errors = {}
  skillDialog.visible = true
}

function openEditSkill(row) {
  skillDialog.mode = 'edit'
  skillDialog.form = {
    id: row.id,
    name: row.name || '',
    sortOrder: row.sortOrder || 0
  }
  skillDialog.errors = {}
  skillDialog.visible = true
}

function validateSkillForm() {
  const e = {}
  if (!skillDialog.form.name || !skillDialog.form.name.trim()) {
    e.name = '名称不能为空'
  } else if (skillDialog.form.name.length > 128) {
    e.name = '名称过长（≤128）'
  }
  skillDialog.errors = e
  return Object.keys(e).length === 0
}

async function submitSkill() {
  if (!validateSkillForm()) return
  const payload = {
    name: skillDialog.form.name.trim(),
    sortOrder: skillDialog.form.sortOrder || 0
  }
  try {
    if (skillDialog.mode === 'add') {
      await dictApi.addSkillSuggestion(payload)
      ElMessage.success('已新增技能')
    } else {
      await dictApi.updateSkillSuggestion(skillDialog.form.id, payload)
      ElMessage.success('已更新')
    }
    skillDialog.visible = false
    loadSkills()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function deleteSkill(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除技能「${row.name}」？`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await dictApi.deleteSkillSuggestion(row.id)
    ElMessage.success('已删除')
    loadSkills()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

// ============ tab 切换懒加载 ============
watch(activeTab, (tab) => {
  if (tab === 'industry' && industries.value.length === 0) loadIndustries()
  if (tab === 'city' && cities.value.length === 0) loadCities()
  if (tab === 'skill' && skills.value.length === 0) loadSkills()
})

// 首次进入：加载第一个 tab
onMounted(() => {
  loadIndustries()
})
</script>

<template>
  <div class="page-shell dict-page">
    <header class="page-head">
      <h2 class="page-title">字典维护</h2>
      <p class="page-desc">维护行业 / 城市 / 技能建议池数据源；候选人 / HR 端的下拉框与筛选选项均依赖此处</p>
    </header>

    <section class="dict-card">
      <el-tabs v-model="activeTab" class="dict-tabs">
        <!-- 行业 tab -->
        <el-tab-pane label="行业" name="industry">
          <div class="tab-bar">
            <el-button type="primary" @click="openAddIndustry">+ 新增行业</el-button>
            <span class="tab-hint">共 {{ industries.length }} 条 · 一级 + 二级</span>
          </div>
          <el-table :data="industryTableData" v-loading="industriesLoading" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" label="行业名" min-width="200" />
            <el-table-column label="父级行业" width="180">
              <template #default="{ row }">
                <span v-if="row.parentId">{{ row.parentName }}</span>
                <el-tag v-else type="info" size="small">一级</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="100" />
            <el-table-column prop="updatedAt" label="更新时间" width="180">
              <template #default="{ row }">
                <span class="text-soft">{{ row.updatedAt?.replace('T', ' ').slice(0, 19) || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openEditIndustry(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="deleteIndustry(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty-state">暂无行业数据</div>
            </template>
          </el-table>
        </el-tab-pane>

        <!-- 城市 tab -->
        <el-tab-pane label="城市" name="city">
          <div class="tab-bar">
            <el-button type="primary" @click="openAddCity">+ 新增城市</el-button>
            <span class="tab-hint">共 {{ cities.length }} 条 · 含省份</span>
          </div>
          <el-table :data="cities" v-loading="citiesLoading" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" label="城市" min-width="150" />
            <el-table-column prop="province" label="省份 / 直辖市" min-width="160" />
            <el-table-column prop="sortOrder" label="排序" width="100" />
            <el-table-column prop="updatedAt" label="更新时间" width="180">
              <template #default="{ row }">
                <span class="text-soft">{{ row.updatedAt?.replace('T', ' ').slice(0, 19) || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openEditCity(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="deleteCity(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty-state">暂无城市数据</div>
            </template>
          </el-table>
        </el-tab-pane>

        <!-- 技能建议池 tab -->
        <el-tab-pane label="技能建议池" name="skill">
          <div class="tab-bar">
            <el-button type="primary" @click="openAddSkill">+ 新增技能</el-button>
            <span class="tab-hint">共 {{ skills.length }} 条 · 前端消费预留</span>
          </div>
          <el-table :data="skills" v-loading="skillsLoading" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" label="技能名" min-width="200" />
            <el-table-column prop="sortOrder" label="排序" width="100" />
            <el-table-column prop="updatedAt" label="更新时间" width="180">
              <template #default="{ row }">
                <span class="text-soft">{{ row.updatedAt?.replace('T', ' ').slice(0, 19) || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openEditSkill(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="deleteSkill(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <div class="empty-state">暂无技能数据</div>
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <!-- 行业弹窗 -->
    <el-dialog
      v-model="industryDialog.visible"
      :title="industryDialog.mode === 'add' ? '新增行业' : '编辑行业'"
      width="480px"
    >
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="行业名" required :error="industryDialog.errors.name">
          <el-input v-model="industryDialog.form.name" maxlength="128" show-word-limit placeholder="例如：互联网/IT" />
        </el-form-item>
        <el-form-item label="父级行业" :error="industryDialog.errors.parentId">
          <el-select v-model="industryDialog.form.parentId" placeholder="留空 = 一级行业" clearable filterable>
            <el-option
              v-for="i in topLevelIndustries"
              :key="i.id"
              :label="i.name"
              :value="i.id"
            />
          </el-select>
          <span class="form-hint">二级行业需选择一个一级行业作为父级</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="industryDialog.form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="industryDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitIndustry">保存</el-button>
      </template>
    </el-dialog>

    <!-- 城市弹窗 -->
    <el-dialog
      v-model="cityDialog.visible"
      :title="cityDialog.mode === 'add' ? '新增城市' : '编辑城市'"
      width="480px"
    >
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="城市名" required :error="cityDialog.errors.name">
          <el-input v-model="cityDialog.form.name" maxlength="128" show-word-limit placeholder="例如：杭州" />
        </el-form-item>
        <el-form-item label="省份 / 直辖市" required :error="cityDialog.errors.province">
          <el-input v-model="cityDialog.form.province" maxlength="64" show-word-limit placeholder="例如：浙江" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="cityDialog.form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cityDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitCity">保存</el-button>
      </template>
    </el-dialog>

    <!-- 技能弹窗 -->
    <el-dialog
      v-model="skillDialog.visible"
      :title="skillDialog.mode === 'add' ? '新增技能' : '编辑技能'"
      width="480px"
    >
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="技能名" required :error="skillDialog.errors.name">
          <el-input v-model="skillDialog.form.name" maxlength="128" show-word-limit placeholder="例如：Kubernetes" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="skillDialog.form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="skillDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitSkill">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dict-page {
  padding-top: 96px;
}
.page-head {
  margin-bottom: 20px;
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 4px;
}
.page-desc {
  font-size: 13px;
  color: var(--text-soft);
}
.dict-card {
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 8px 16px 16px;
  box-shadow: var(--shadow-soft);
}
.dict-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}
.tab-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.tab-hint {
  font-size: 12px;
  color: var(--text-muted);
}
.empty-state {
  padding: 32px 0;
  text-align: center;
  color: var(--text-soft);
  font-size: 13px;
}
.text-soft {
  color: var(--text-soft);
}
.form-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}
</style>