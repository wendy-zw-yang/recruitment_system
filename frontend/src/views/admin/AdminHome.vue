<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api/admin'

const router = useRouter()

const stats = reactive({
  pendingCompanies: 0,
  totalUsers: 0
})

const quickActions = [
  { icon: '👥', title: '用户管理', desc: '启停账号 / 重置密码 / 改角色', route: '/admin/users/audit' },
  { icon: '🏢', title: '公司审核', desc: '核实 HR 公司资质', route: '/admin/companies/audit' },
  { icon: '📚', title: '字典维护', desc: '维护行业 / 城市 / 技能建议池', action: 'coming' }
]

function goAudit(item) {
  if (item.route) {
    router.push(item.route)
  }
}

function comingSoon() {
  ElMessage.info('该功能即将上线')
}

async function loadStats() {
  try {
    // axios 拦截器已自动解 Result 包装，res 即 IPage<T>
    const [usersRes, companiesRes] = await Promise.all([
      adminApi.listUsers({ role: 'CANDIDATE', pageNum: 1, pageSize: 1 }),
      adminApi.listCompanies({ authStatus: 'PENDING', pageNum: 1, pageSize: 1 })
    ])
    stats.totalUsers = usersRes?.total || 0
    stats.pendingCompanies = companiesRes?.total || 0
  } catch (e) {
    console.warn('AdminHome stats load failed:', e)
  }
}

onMounted(loadStats)
</script>

<template>
  <div class="page-shell home admin-home">
    <section class="hero">
      <div class="hero__bg"></div>
      <div class="hero__inner">
        <div class="hero__text">
          <span class="hero__eyebrow">运营工作台</span>
          <h1 class="hero__title">平台运营与审核中心</h1>
          <p class="hero__subtitle">维护账号秩序、保障招聘体验、及时处理异常情况</p>
        </div>
        <div class="hero__alert" v-if="stats.pendingCompanies > 0">
          <span class="alert-icon">⚠</span>
          <div class="alert-body">
            <span class="alert-text">你有 {{ stats.pendingCompanies }} 项待办</span>
            <span class="alert-sub">{{ stats.pendingCompanies }} 个公司待审</span>
          </div>
          <el-button type="primary" round @click="router.push('/admin/companies/audit')">立即处理</el-button>
        </div>
      </div>
    </section>

    <section class="quick-actions">
      <div class="quick-bar">
        <button
          v-for="(item, idx) in quickActions"
          :key="idx"
          class="quick-btn"
          @click="item.route ? goAudit(item) : comingSoon()"
        >
          <span class="quick-btn__icon">{{ item.icon }}</span>
          <span class="quick-btn__title">{{ item.title }}</span>
        </button>
      </div>
    </section>

    <section class="stats">
      <div class="stat-card stat-card--accent" @click="router.push('/admin/users/audit')">
        <span class="stat-card__value">{{ stats.totalUsers }}</span>
        <span class="stat-card__label">平台用户总数</span>
        <span class="stat-card__badge">查看</span>
      </div>
      <div class="stat-card stat-card--accent" @click="router.push('/admin/companies/audit')">
        <span class="stat-card__value">{{ stats.pendingCompanies }}</span>
        <span class="stat-card__label">待审公司</span>
        <span v-if="stats.pendingCompanies > 0" class="stat-card__badge">待办</span>
      </div>
      <div class="stat-card" @click="comingSoon()">
        <span class="stat-card__value">—</span>
        <span class="stat-card__label">字典 / 日志</span>
      </div>
    </section>

    <section class="panel audit-panel">
      <div class="panel-head">
        <div class="panel-title">
          <h2>审核中心</h2>
          <span class="hint">点击下方快捷入口进入对应审核模块</span>
        </div>
      </div>

      <div class="empty-state">
        <div class="empty-state__icon">🛡️</div>
        <h3 class="empty-state__title">2 类审核已上线</h3>
        <p class="empty-state__desc">
          用户管理 + 公司审核（职位由 HR 自审自管，无需管理员介入）
        </p>
        <el-button type="primary" round @click="router.push('/admin/users/audit')">进入用户管理</el-button>
      </div>
    </section>

    <footer class="footer">
      <div class="footer__inner">
        <span>© 2026 汇聘 · 让每一次相遇都更值得</span>
        <nav class="footer__links">
          <a>关于我们</a>
          <a>联系客服</a>
          <a>使用条款</a>
        </nav>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.admin-home {
  padding-top: 96px;
}

.hero {
  position: relative;
  margin-bottom: 24px;
  padding: 40px 40px;
  border-radius: 24px;
  background: linear-gradient(120deg, #0a2a5e 0%, #0f4fb0 60%, #1d6fd8 100%);
  color: #fff;
  overflow: hidden;
  isolation: isolate;
}
.hero__bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 88% 12%, rgba(6, 182, 212, 0.22), transparent 45%),
    radial-gradient(circle at 12% 88%, rgba(255, 255, 255, 0.1), transparent 50%);
  z-index: -1;
}
.hero__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  flex-wrap: wrap;
}
.hero__eyebrow {
  display: inline-block;
  font-size: 12px;
  letter-spacing: 0.16em;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  margin-bottom: 14px;
}
.hero__title {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 0.02em;
  margin-bottom: 10px;
}
.hero__subtitle {
  font-size: 15px;
  opacity: 0.92;
}
.hero__alert {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 14px;
  backdrop-filter: blur(10px);
}
.alert-icon {
  font-size: 22px;
  color: #ffd54f;
}
.alert-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.alert-text {
  font-size: 14px;
  font-weight: 600;
}
.alert-sub {
  font-size: 12px;
  opacity: 0.85;
}

.quick-actions {
  margin-bottom: 24px;
}
.quick-bar {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 12px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 16px;
  box-shadow: var(--shadow-soft);
}
.quick-btn {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 56px;
  padding: 0 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg);
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}
.quick-btn:hover {
  background: var(--primary-tint);
  border-color: var(--primary);
  color: var(--primary-deep);
  transform: translateY(-1px);
}
.quick-btn__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--panel-strong);
  color: var(--primary);
  font-size: 16px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}
.stat-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 22px 24px;
  border-radius: 16px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.stat-card--accent {
  border-color: rgba(245, 158, 11, 0.35);
  background: linear-gradient(135deg, #fff8eb 0%, #ffffff 100%);
}
.stat-card:hover {
  border-color: var(--primary-tint-strong);
  box-shadow: var(--shadow-tab);
}
.stat-card__value {
  font-size: 30px;
  font-weight: 700;
  color: var(--text);
  line-height: 1.2;
}
.stat-card--accent .stat-card__value {
  color: #d97706;
}
.stat-card__label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-soft);
}
.stat-card__badge {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(245, 158, 11, 0.18);
  color: #b45309;
  font-size: 11px;
  font-weight: 600;
}

.panel {
  padding: 24px;
  border-radius: 18px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-soft);
  margin-bottom: 24px;
}
.panel-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 16px;
}
.panel-title h2 {
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
}
.hint {
  font-size: 13px;
  color: var(--text-soft);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px;
  text-align: center;
}
.empty-state__icon {
  font-size: 48px;
  margin-bottom: 12px;
  opacity: 0.55;
}
.empty-state__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8px;
}
.empty-state__desc {
  font-size: 13px;
  color: var(--text-soft);
  margin-bottom: 18px;
  max-width: 420px;
}

.footer {
  margin-top: 16px;
  padding: 24px 0;
  border-top: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.5);
}
.footer__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-muted);
}
.footer__links {
  display: flex;
  gap: 24px;
}
.footer__links a {
  cursor: pointer;
  transition: color 0.18s ease;
}
.footer__links a:hover {
  color: var(--primary);
}

@media (max-width: 1100px) {
  .quick-bar {
    grid-template-columns: repeat(2, 1fr);
  }
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>