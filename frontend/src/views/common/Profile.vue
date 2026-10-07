<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/useAuthStore'
import { profileApi } from '@/api/profile'
import { dictApi } from '@/api/dict'

const router = useRouter()
const auth = useAuthStore()

const activeTab = ref('profile')
const loading = ref(false)
const industries = ref([])
const cities = ref([])

const tabs = computed(() => {
  const base = [
    { name: 'profile', label: '个人资料' },
    { name: 'security', label: '账号安全' }
  ]
  if (auth.isCandidate) base.push({ name: 'preference', label: '求职偏好' })
  if (auth.isHR) base.push({ name: 'company', label: '我的公司' })
  return base
})

const profile = ref({
  username: auth.userInfo?.username || '',
  email: auth.userInfo?.email || '',
  phone: ''
})

const phonePlaceholder = computed(() => {
  if (auth.isHR) return '便于候选人联系到你'
  if (auth.isAdmin) return '内部联系使用'
  return '便于 HR 联系到你'
})

const security = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// v0.7.3：偏好字段类型改为 id（行业/城市）+ 文本（按币种 + 关键词）
const preference = ref({
  expectedPosition: '',
  expectedIndustryId: null,
  expectedProvince: '',
  expectedCityId: null
})

// 省份下拉选项（从 dict_city 派生去重）
const provinces = computed(() => {
  const set = new Set()
  cities.value.forEach((c) => {
    if (c.province && c.province.trim()) set.add(c.province)
  })
  return Array.from(set).sort()
})

// 城市下拉选项（按省份过滤）
const cityOptions = computed(() => {
  if (!preference.value.expectedProvince) return cities.value
  return cities.value.filter(
    (c) => c.province === preference.value.expectedProvince
  )
})

// 监听省份变化，清空城市（避免省份-城市错配）
function onProvinceChange() {
  preference.value.expectedCityId = null
}

const company = ref({
  id: null,
  name: '',
  industryId: null,
  industryName: '',
  scale: '',
  description: '',
  authStatus: 'PENDING',
  authNote: ''
})

// ============ 数据加载 ============

async function loadAll() {
  loading.value = true
  try {
    // 个人资料（候选人 / HR / admin 都有）
    const me = await profileApi.getMe()
    profile.value.username = me.username || ''
    profile.value.email = me.email || ''
    profile.value.phone = me.phone || ''
    // 同步 auth store 的 userInfo（昵称改了立刻反映）
    if (auth.userInfo) auth.userInfo.username = me.username || ''

    // HR 端"我的公司"tab 需要行业字典
    if (auth.isHR && industries.value.length === 0) {
      try {
        industries.value = (await dictApi.industries()) || []
      } catch (e) {
        console.warn('Load industries failed:', e)
      }
    }

    if (auth.isCandidate) {
      // 候选人的偏好 tab：行业 + 城市字典
      if (industries.value.length === 0) {
        industries.value = (await dictApi.industries()) || []
      }
      if (cities.value.length === 0) {
        cities.value = (await dictApi.cities()) || []
      }
      const pref = await profileApi.getPreference()
      preference.value.expectedPosition = pref.expectedPosition || ''
      preference.value.expectedIndustryId = pref.expectedIndustryId || null
      preference.value.expectedProvince = pref.expectedProvince || ''
      preference.value.expectedCityId = pref.expectedCityId || null
    }
    if (auth.isHR) {
      const comp = await profileApi.getCompany()
      company.value = {
        id: comp.id,
        name: comp.name || '',
        industryId: comp.industryId,
        industryName: comp.industryName || '',
        scale: comp.scale || '',
        description: comp.description || '',
        authStatus: comp.authStatus || 'PENDING',
        authNote: comp.authNote || ''
      }
    }
  } catch (e) {
    console.error('Profile load failed:', e)
    // 静默：保留默认空值
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)

// ============ 4 个保存动作（v0.5：全部对接后端） ============

async function saveProfile() {
  try {
    const updated = await profileApi.updateMe({
      username: profile.value.username,
      phone: profile.value.phone
    })
    profile.value.username = updated.username || ''
    profile.value.phone = updated.phone || ''
    if (auth.userInfo) auth.userInfo.username = updated.username || ''
    ElMessage.success('个人资料已保存')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function changePassword() {
  if (!security.value.oldPassword) {
    ElMessage.warning('请输入当前密码')
    return
  }
  if (!security.value.newPassword || security.value.newPassword.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  if (security.value.newPassword !== security.value.confirmPassword) {
    ElMessage.warning('两次密码不一致')
    return
  }
  try {
    await profileApi.changePassword({
      oldPassword: security.value.oldPassword,
      newPassword: security.value.newPassword
    })
    ElMessage.success('密码已修改')
    security.value.oldPassword = ''
    security.value.newPassword = ''
    security.value.confirmPassword = ''
  } catch (e) {
    ElMessage.error(e.message || '密码修改失败')
  }
}

// v0.7.3：偏好保存——行业 / 省份 / 城市 改为 id 关联
async function savePreference() {
  try {
    await profileApi.updatePreference({
      expectedPosition: preference.value.expectedPosition,
      expectedIndustryId: preference.value.expectedIndustryId,
      expectedProvince: preference.value.expectedProvince,
      expectedCityId: preference.value.expectedCityId
    })
    ElMessage.success('求职偏好已保存')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function saveCompany() {
  try {
    const updated = await profileApi.updateCompany({
      name: company.value.name,
      industryId: company.value.industryId,
      scale: company.value.scale,
      description: company.value.description
    })
    company.value.authStatus = updated.authStatus || company.value.authStatus
    company.value.authNote = updated.authNote || ''
    company.value.industryName = updated.industryName || ''
    ElMessage.success('公司信息已保存')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="page-shell profile-page">
    <div class="profile-layout">
      <aside class="profile-side">
        <div class="profile-card">
          <div class="avatar-large">{{ (auth.userInfo?.username || auth.userInfo?.email || '?').slice(0, 1).toUpperCase() }}</div>
          <h2 class="profile-name">{{ auth.userInfo?.username || '未设置昵称' }}</h2>
          <p class="profile-email">{{ auth.userInfo?.email }}</p>
        </div>

        <nav class="profile-nav">
          <a
            v-for="t in tabs"
            :key="t.name"
            class="profile-nav__item"
            :class="{ 'profile-nav__item--active': activeTab === t.name }"
            @click="activeTab = t.name"
          >
            {{ t.label }}
          </a>
          <a class="profile-nav__item profile-nav__item--danger" @click="handleLogout">退出登录</a>
        </nav>
      </aside>

      <section class="profile-content">
        <div v-if="activeTab === 'profile'" class="content-card">
          <h2 class="content-title">个人资料</h2>
          <p class="content-desc">这里展示的是公开信息，企业在查看你简历时会看到这些字段。</p>
          <el-form label-width="100px" class="profile-form">
            <el-form-item label="昵称">
              <el-input v-model="profile.username" placeholder="请输入昵称" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profile.email" disabled />
            </el-form-item>
            <el-form-item label="手机">
              <el-input v-model="profile.phone" :placeholder="phonePlaceholder" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveProfile">保存修改</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div v-if="activeTab === 'security'" class="content-card">
          <h2 class="content-title">账号安全</h2>
          <p class="content-desc">定期更换密码可以提升账号安全性。</p>
          <el-form label-width="100px" class="profile-form">
            <el-form-item label="当前密码">
              <el-input v-model="security.oldPassword" type="password" show-password placeholder="输入当前密码" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="security.newPassword" type="password" show-password placeholder="至少 8 位" />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="security.confirmPassword" type="password" show-password placeholder="再次输入" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="changePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div v-if="activeTab === 'preference'" class="content-card">
          <h2 class="content-title">求职偏好</h2>
          <p class="content-desc">完善偏好后，系统会优先为你推荐匹配的职位；职位标题会按空白拆词后匹配职位标题 / JD 要求 / 关键词。</p>
          <el-form label-width="100px" class="profile-form">
            <el-form-item label="期望职位">
              <el-input v-model="preference.expectedPosition" placeholder="例如：Java 工程师（多个词用空格分隔）" />
            </el-form-item>
            <el-form-item label="期望行业">
              <el-select v-model="preference.expectedIndustryId" placeholder="请选择行业" clearable filterable>
                <el-option
                  v-for="i in industries"
                  :key="i.id"
                  :label="i.name"
                  :value="i.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="期望省份">
              <el-select
                v-model="preference.expectedProvince"
                placeholder="请选择省份"
                clearable
                filterable
                @change="onProvinceChange"
              >
                <el-option
                  v-for="p in provinces"
                  :key="p"
                  :label="p"
                  :value="p"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="期望城市">
              <el-select
                v-model="preference.expectedCityId"
                placeholder="请先选择省份"
                :disabled="!preference.expectedProvince"
                clearable
                filterable
              >
                <el-option
                  v-for="c in cityOptions"
                  :key="c.id"
                  :label="c.name"
                  :value="c.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="savePreference">保存偏好</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div v-if="activeTab === 'company'" class="content-card">
          <h2 class="content-title">我的公司</h2>
          <p class="content-desc">公司信息会展示在每个职位的招聘详情页，企业认证通过后可被更多求职者看到。</p>
          <el-form label-width="100px" class="profile-form">
            <el-form-item label="公司名">
              <el-input v-model="company.name" placeholder="请输入公司名" />
            </el-form-item>
            <el-form-item label="行业">
              <el-select v-model="company.industryId" placeholder="请选择行业" clearable filterable>
                <el-option
                  v-for="i in industries"
                  :key="i.id"
                  :label="i.name"
                  :value="i.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="规模">
              <el-input v-model="company.scale" placeholder="例如：100-500 人" />
            </el-form-item>
            <el-form-item label="公司简介">
              <el-input v-model="company.description" type="textarea" :rows="4" />
            </el-form-item>
            <el-form-item label="认证状态">
              <el-tag :type="company.authStatus === 'VERIFIED' ? 'success' : company.authStatus === 'REJECTED' ? 'danger' : 'warning'">
                {{ company.authStatus === 'VERIFIED' ? '已认证' : company.authStatus === 'REJECTED' ? '已驳回' : '认证中' }}
              </el-tag>
            </el-form-item>
            <el-form-item v-if="company.authStatus === 'REJECTED' && company.authNote">
              <el-alert :title="`驳回理由：${company.authNote}`" type="error" :closable="false" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveCompany">保存</el-button>
            </el-form-item>
          </el-form>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  padding-top: 104px;
}

.profile-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 24px;
}

.profile-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.profile-card {
  padding: 28px 20px;
  border-radius: 18px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  text-align: center;
  box-shadow: var(--shadow-soft);
}

.avatar-large {
  width: 72px;
  height: 72px;
  margin: 0 auto 12px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, var(--primary) 0%, var(--accent) 100%);
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--text);
}

.profile-email {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 4px;
}

.profile-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border-radius: 16px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-soft);
}

.profile-nav__item {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 14px;
  border-radius: 10px;
  font-size: 14px;
  color: var(--text-soft);
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease;
}

.profile-nav__item:hover {
  background: var(--primary-tint);
  color: var(--primary);
}

.profile-nav__item--active {
  background: var(--primary-tint);
  color: var(--primary);
  font-weight: 600;
}

.profile-nav__item--danger {
  color: var(--text-muted);
  margin-top: 8px;
  border-top: 1px solid var(--line);
  border-radius: 0 0 10px 10px;
}

.profile-nav__item--danger:hover {
  color: var(--danger);
  background: rgba(217, 69, 69, 0.08);
}

.profile-content {
  min-height: 480px;
}

.content-card {
  padding: 32px;
  border-radius: 18px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-soft);
}

.content-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 6px;
}

.content-desc {
  font-size: 14px;
  color: var(--text-soft);
  margin-bottom: 24px;
}

.profile-form {
  max-width: 540px;
}

@media (max-width: 900px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }
}
</style>
