<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'

const loading = ref(false)
const items = ref([])
const total = ref(0)

const filter = reactive({
  role: '',
  status: '',
  keyword: '',
  pageNum: 1,
  pageSize: 10
})

async function load() {
  loading.value = true
  try {
    // axios 拦截器已自动解 Result 包装，res 即 IPage<UserListItem>
    const res = await adminApi.listUsers({ ...filter })
    items.value = res?.records || []
    total.value = res?.total || 0
  } catch (e) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function handleFilterChange() {
  filter.pageNum = 1
  load()
}

function changePage(p) {
  filter.pageNum = p
  load()
}

async function doEnable(row) {
  try {
    await ElMessageBox.confirm(`确认启用「${row.email}」？`, '启用账号', { type: 'success' })
    await adminApi.enableUser(row.id)
    ElMessage.success('已启用')
    load()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function doDisable(row) {
  if (row.roleCode === 'ADMIN') {
    ElMessage.warning('管理员账号不可禁用')
    return
  }
  try {
    await ElMessageBox.confirm(`确认禁用「${row.email}」？该用户将被强制下线。`, '禁用账号', { type: 'warning' })
    await adminApi.disableUser(row.id)
    ElMessage.success('已禁用')
    load()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function doResetPassword(row) {
  try {
    await ElMessageBox.confirm(
      `确认重置「${row.email}」的密码？重置后会生成 6 位临时密码（仅显示一次）。`,
      '重置密码',
      { type: 'warning' }
    )
    const res = await adminApi.resetPassword(row.id)
    await ElMessageBox.alert(
      `临时密码：${res?.tempPassword}\n（请复制并转告用户，本次显示后不可再查）`,
      '密码已重置',
      { confirmButtonText: '我已记录' }
    )
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function doChangeRole(row) {
  if (row.roleCode === 'ADMIN') {
    ElMessage.warning('管理员账号不可改角色')
    return
  }
  const target = row.roleCode === 'CANDIDATE' ? 'HR' : 'CANDIDATE'
  try {
    await ElMessageBox.confirm(
      `确认将「${row.email}」从 ${row.roleCode} 切换为 ${target}？`,
      '切换角色',
      { type: 'warning' }
    )
    await adminApi.changeRole(row.id, { roleCode: target })
    ElMessage.success('已切换')
    load()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

const roleTagType = computed(() => (r) => {
  if (r === 'ADMIN') return 'danger'
  if (r === 'HR') return 'warning'
  return 'info'
})

const statusTagType = computed(() => (s) => (s === 'ENABLED' ? 'success' : 'danger'))
</script>

<template>
  <div class="page-shell audit-page">
    <header class="page-head">
      <h2 class="page-title">用户管理</h2>
      <p class="page-desc">启停账号、重置密码、切换角色</p>
    </header>

    <section class="filter-bar">
      <el-input
        v-model="filter.keyword"
        placeholder="按邮箱模糊搜索"
        clearable
        class="filter-input"
        @keyup.enter="handleFilterChange"
        @clear="handleFilterChange"
      />
      <el-select v-model="filter.role" placeholder="角色" clearable class="filter-select" @change="handleFilterChange">
        <el-option label="求职者" value="CANDIDATE" />
        <el-option label="HR" value="HR" />
        <el-option label="管理员" value="ADMIN" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable class="filter-select" @change="handleFilterChange">
        <el-option label="已启用" value="ENABLED" />
        <el-option label="已禁用" value="DISABLED" />
      </el-select>
      <el-button @click="handleFilterChange">查询</el-button>
    </section>

    <section class="table-card">
      <el-table :data="items" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="email" label="邮箱" min-width="220" />
        <el-table-column prop="username" label="昵称" min-width="120" />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.roleCode)" size="small">{{ row.roleCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="createdAt" label="注册时间" width="180" />
        <el-table-column label="操作" min-width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'ENABLED'" size="small" type="success" @click="doEnable(row)">启用</el-button>
            <el-button v-else-if="row.roleCode !== 'ADMIN'" size="small" type="danger" @click="doDisable(row)">禁用</el-button>
            <el-button v-if="row.roleCode !== 'ADMIN'" size="small" @click="doResetPassword(row)">重置密码</el-button>
            <el-button v-if="row.roleCode !== 'ADMIN'" size="small" type="primary" plain @click="doChangeRole(row)">
              {{ row.roleCode === 'CANDIDATE' ? '→ HR' : '→ CANDIDATE' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-state">
            <span class="empty-state__icon">👥</span>
            <p>暂无匹配用户</p>
          </div>
        </template>
      </el-table>

      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :total="total"
          :page-size="filter.pageSize"
          :current-page="filter.pageNum"
          @current-change="changePage"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.audit-page {
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
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filter-input {
  width: 260px;
}
.filter-select {
  width: 140px;
}
.table-card {
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 16px;
  box-shadow: var(--shadow-soft);
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 0;
  color: var(--text-soft);
}
.empty-state__icon {
  font-size: 36px;
  opacity: 0.5;
  margin-bottom: 8px;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>