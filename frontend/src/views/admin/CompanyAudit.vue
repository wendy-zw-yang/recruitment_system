<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'

const loading = ref(false)
const items = ref([])
const total = ref(0)

const filter = reactive({
  authStatus: 'PENDING',
  pageNum: 1,
  pageSize: 10
})

const rejectDialog = reactive({
  visible: false,
  companyId: null,
  companyName: '',
  note: '',
  disableHr: false
})

async function load() {
  loading.value = true
  try {
    // axios 拦截器已自动解 Result 包装，res 即 IPage<CompanyAuditItem>
    const res = await adminApi.listCompanies({
      authStatus: filter.authStatus,
      pageNum: filter.pageNum,
      pageSize: filter.pageSize
    })
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

async function doVerify(row) {
  try {
    await ElMessageBox.confirm(`确认通过「${row.name}」的资质审核？`, '审核通过', { type: 'success' })
    await adminApi.verifyCompany(row.id)
    ElMessage.success('已通过')
    load()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

function openRejectDialog(row) {
  rejectDialog.companyId = row.id
  rejectDialog.companyName = row.name
  rejectDialog.note = ''
  rejectDialog.disableHr = false
  rejectDialog.visible = true
}

async function submitReject() {
  if (!rejectDialog.note || !rejectDialog.note.trim()) {
    ElMessage.warning('请填写驳回理由')
    return
  }
  try {
    await adminApi.rejectCompany(rejectDialog.companyId, {
      note: rejectDialog.note.trim(),
      disableHr: rejectDialog.disableHr
    })
    const msg = rejectDialog.disableHr ? '已驳回并同步禁用 HR 账号' : '已驳回'
    ElMessage.success(msg)
    rejectDialog.visible = false
    load()
  } catch (e) {
    ElMessage.error(e?.message || '驳回失败')
  }
}

function statusTagType(s) {
  if (s === 'VERIFIED') return 'success'
  if (s === 'REJECTED') return 'danger'
  return 'warning'
}
</script>

<template>
  <div class="page-shell audit-page">
    <header class="page-head">
      <h2 class="page-title">公司审核</h2>
      <p class="page-desc">HR 注册后进入待审核。HR 注册即用，管理员事后巡查（异步审核）</p>
    </header>

    <section class="filter-bar">
      <el-select v-model="filter.authStatus" placeholder="审核状态" class="filter-select" @change="handleFilterChange">
        <el-option label="待审核 PENDING" value="PENDING" />
        <el-option label="已通过 VERIFIED" value="VERIFIED" />
        <el-option label="已拒绝 REJECTED" value="REJECTED" />
      </el-select>
      <el-button @click="handleFilterChange">查询</el-button>
    </section>

    <section class="table-card">
      <el-table :data="items" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="公司名" min-width="180" />
        <el-table-column prop="hrEmail" label="HR 邮箱" min-width="200" />
        <el-table-column prop="industryName" label="行业" width="120">
          <template #default="{ row }">
            <span v-if="row.industryName">{{ row.industryName }}</span>
            <span v-else class="text-soft">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="scale" label="规模" width="110" />
        <el-table-column prop="authStatus" label="审核" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.authStatus)" size="small">{{ row.authStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="180" />
        <el-table-column label="操作" min-width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.authStatus === 'PENDING'">
              <el-button size="small" type="success" @click="doVerify(row)">通过</el-button>
              <el-button size="small" type="danger" @click="openRejectDialog(row)">驳回</el-button>
            </template>
            <span v-else class="text-soft">已处理</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-state">
            <span class="empty-state__icon">🏢</span>
            <p>暂无 {{ filter.authStatus }} 状态的公司</p>
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

    <el-dialog
      v-model="rejectDialog.visible"
      :title="`驳回「${rejectDialog.companyName}」`"
      width="480px"
    >
      <el-form @submit.prevent>
        <el-form-item label="驳回理由" required>
          <el-input
            v-model="rejectDialog.note"
            type="textarea"
            :rows="4"
            maxlength="512"
            show-word-limit
            placeholder="请说明驳回理由"
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="rejectDialog.disableHr">
            同步禁用该 HR 账号（如果 HR 信息严重不实，可勾选）
          </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialog.visible = false">取消</el-button>
        <el-button type="danger" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>
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
}
.filter-select {
  width: 200px;
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
.text-soft {
  color: var(--text-soft);
}
</style>