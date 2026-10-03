<script setup>
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()

function comingSoon() {
  ElMessage.info('该功能即将上线')
}

function goPublish() {
  router.push('/hr/jobs?create=1')
}

const quickActions = [
  { icon: '+', title: '发布新职位', action: 'publish' },
  { icon: '📥', title: '收到的简历', action: 'coming' },
  { icon: '🔍', title: '搜索人才', action: 'coming' },
  { icon: '💬', title: '消息中心', action: 'coming' }
]

const stats = [
  { label: '在线职位', value: '—' },
  { label: '收到简历', value: '—' },
  { label: '今日新增', value: '—' },
  { label: '未读消息', value: '—' }
]
</script>

<template>
  <div class="page-shell home hr-home">
    <section class="hero">
      <div class="hero__bg"></div>
      <div class="hero__inner">
        <div class="hero__text">
          <span class="hero__eyebrow">招聘工作台</span>
          <h1 class="hero__title">高效招聘，找到合适的人才</h1>
          <p class="hero__subtitle">集中处理职位、简历与候选人沟通，把时间花在判断而非整理上</p>
        </div>
        <div class="hero__metric">
          <div class="metric-item" v-for="s in stats" :key="s.label">
            <span class="metric-num">{{ s.value }}</span>
            <span class="metric-label">{{ s.label }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="quick-actions">
      <div class="quick-bar">
        <button
          v-for="(item, idx) in quickActions"
          :key="idx"
          class="quick-btn"
          :class="{ 'quick-btn--primary': idx === 0 }"
          @click="item.action === 'publish' ? goPublish() : comingSoon()"
        >
          <span class="quick-btn__icon">{{ item.icon }}</span>
          <span class="quick-btn__title">{{ item.title }}</span>
        </button>
      </div>
    </section>

    <div class="main-grid">
      <section class="panel applications-panel">
        <div class="panel-head">
          <div class="panel-title">
            <h2>近期收到的候选人</h2>
            <span class="hint">按投递时间倒序，匹配度由简历自动评估</span>
          </div>
        </div>
        <div class="empty-state">
          <div class="empty-state__icon">📭</div>
          <h3 class="empty-state__title">暂无候选人投递</h3>
          <p class="empty-state__desc">发布职位后，候选人的投递将自动汇总到这里，按匹配度排序展示</p>
          <el-button type="primary" @click="goPublish()">去发布职位</el-button>
        </div>
      </section>

      <aside class="panel tips-panel">
        <div class="panel-head">
          <div class="panel-title">
            <h2>招聘小贴士</h2>
          </div>
        </div>
        <p class="tip">JD 中包含具体的技术栈与项目类型，能让匹配度评估更精准。</p>
        <p class="tip">对高匹配度候选人，建议 24 小时内回复，提升入职转化。</p>
        <p class="tip">及时更新职位状态，避免无效投递堆积。</p>
      </aside>
    </div>

    <section class="panel jobs-panel">
      <div class="panel-head">
        <div class="panel-title">
          <h2>我发布的职位</h2>
          <span class="hint">点击职位可查看投递列表与状态</span>
        </div>
        <el-button type="primary" @click="goPublish()">+ 发布新职位</el-button>
      </div>
      <div class="empty-state">
        <div class="empty-state__icon">📋</div>
        <h3 class="empty-state__title">还没有发布职位</h3>
        <p class="empty-state__desc">发布职位后，候选人可以搜索并投递；这里会展示所有你发布的职位与状态</p>
        <el-button type="primary" @click="goPublish()">立即发布</el-button>
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
.hr-home {
  padding-top: 96px;
}

.hero {
  position: relative;
  margin-bottom: 24px;
  padding: 40px 40px;
  border-radius: 24px;
  background: linear-gradient(120deg, #0f4fb0 0%, #1d6fd8 50%, #06b6d4 100%);
  color: #fff;
  overflow: hidden;
  isolation: isolate;
}

.hero__bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 85% 15%, rgba(255, 255, 255, 0.15), transparent 45%),
    radial-gradient(circle at 15% 90%, rgba(255, 255, 255, 0.1), transparent 50%);
  z-index: -1;
}

.hero__inner {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 32px;
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

.hero__metric {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 18px 24px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 16px;
  backdrop-filter: blur(10px);
}

.metric-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 60px;
}

.metric-num {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
}

.metric-label {
  font-size: 12px;
  opacity: 0.86;
  margin-top: 2px;
  white-space: nowrap;
}

.quick-actions {
  margin-bottom: 24px;
}

.quick-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
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
  font-weight: 700;
}

.quick-btn--primary {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}

.quick-btn--primary:hover {
  background: var(--primary-deep);
  border-color: var(--primary-deep);
  color: #fff;
}

.quick-btn--primary .quick-btn__icon {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.main-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
  margin-bottom: 24px;
}

.panel {
  padding: 24px;
  border-radius: 18px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-soft);
}

.panel-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 18px;
}

.panel-title h2 {
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 4px;
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
  line-height: 1.7;
}

.tips-panel .tip {
  font-size: 13px;
  color: var(--text-soft);
  line-height: 1.7;
  padding: 10px 12px;
  background: var(--bg);
  border-radius: 10px;
  margin-bottom: 8px;
}

.tips-panel .tip:last-child {
  margin-bottom: 0;
}

.footer {
  margin-top: 32px;
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
  .hero__inner {
    grid-template-columns: 1fr;
  }
  .hero__metric {
    flex-wrap: wrap;
    gap: 16px;
  }
  .quick-bar {
    grid-template-columns: repeat(2, 1fr);
  }
  .main-grid {
    grid-template-columns: 1fr;
  }
}
</style>
