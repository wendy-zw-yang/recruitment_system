<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const searchKeyword = ref('')
const searchLocation = ref('')

const hotCities = ['北京', '上海', '广州', '深圳', '杭州', '成都', '南京', '武汉']

const quickActions = [
  { icon: '📄', title: '完善简历', desc: '让企业主动找到你', to: '/resume' },
  { icon: '🔍', title: '浏览职位', desc: '发现匹配的机会', to: '/jobs' },
  { icon: '💬', title: '消息中心', desc: '查看 HR 回复与邀请', to: '/home#messages' }
]

const stats = [
  { label: '已投递', value: '—' },
  { label: '被查看', value: '—' },
  { label: '面试邀请', value: '—' }
]

function doSearch() {
  router.push({ path: '/home', query: { q: searchKeyword.value, city: searchLocation.value } })
}
</script>

<template>
  <div class="page-shell home candidate-home">
    <section class="hero">
      <div class="hero__bg"></div>
      <div class="hero__inner">
        <h1 class="hero__title">发现更适合你的下一份工作</h1>
        <p class="hero__subtitle">覆盖互联网、金融、制造业等热门行业，每日更新上万优质岗位</p>
        <div class="search-bar">
          <el-input v-model="searchKeyword" placeholder="搜索职位、公司或关键词" size="large" class="search-bar__input">
            <template #prefix><span class="search-icon">⌕</span></template>
          </el-input>
          <el-input v-model="searchLocation" placeholder="城市" size="large" class="search-bar__city" />
          <el-button type="primary" size="large" class="search-bar__btn" @click="doSearch">搜索</el-button>
        </div>
        <div class="hot-cities">
          <span class="hot-cities__label">热门城市：</span>
          <a v-for="c in hotCities" :key="c" class="hot-cities__item" @click="searchLocation = c">{{ c }}</a>
        </div>
      </div>
    </section>

    <section class="quick-actions">
      <div class="card-grid">
        <div v-for="(item, idx) in quickActions" :key="idx" class="action-card" @click="router.push(item.to)">
          <div class="action-card__icon">{{ item.icon }}</div>
          <div class="action-card__body">
            <h3>{{ item.title }}</h3>
            <p>{{ item.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <section class="stats">
      <div class="stat-card" v-for="s in stats" :key="s.label">
        <span class="stat-card__value">{{ s.value }}</span>
        <span class="stat-card__label">{{ s.label }}</span>
      </div>
    </section>

    <section class="panel recommended-panel">
      <div class="panel-head">
        <div class="panel-title">
          <h2>推荐职位</h2>
          <span class="hint">按你的简历与偏好智能匹配</span>
        </div>
        <el-button text type="primary" @click="router.push('/home#jobs')">查看更多 →</el-button>
      </div>
      <div class="empty-state">
        <div class="empty-state__icon">🔍</div>
        <h3 class="empty-state__title">完善简历后获取专属推荐</h3>
        <p class="empty-state__desc">上传简历后，平台会基于你的经历与偏好为你匹配合适的职位</p>
        <el-button type="primary" @click="router.push('/resume')">去完善简历</el-button>
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
.candidate-home {
  padding-top: 96px;
}

.hero {
  position: relative;
  padding: 64px 32px;
  margin-bottom: 32px;
  border-radius: 24px;
  background: linear-gradient(120deg, #1d6fd8 0%, #06b6d4 100%);
  color: #fff;
  overflow: hidden;
  isolation: isolate;
}

.hero__bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 80% 20%, rgba(255, 255, 255, 0.18), transparent 40%),
    radial-gradient(circle at 20% 80%, rgba(255, 255, 255, 0.12), transparent 45%);
  z-index: -1;
}

.hero__inner {
  max-width: 880px;
  margin: 0 auto;
  text-align: center;
}

.hero__title {
  font-size: 36px;
  font-weight: 700;
  letter-spacing: 0.04em;
  margin-bottom: 12px;
}

.hero__subtitle {
  font-size: 15px;
  opacity: 0.92;
  margin-bottom: 32px;
}

.search-bar {
  display: grid;
  grid-template-columns: 1fr 200px 120px;
  gap: 12px;
  background: #fff;
  padding: 12px;
  border-radius: 16px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.12);
}

.search-bar :deep(.el-input__wrapper) {
  padding: 4px 12px;
  border-radius: 10px;
  box-shadow: none;
}

.search-bar :deep(.el-input__wrapper):hover {
  box-shadow: 0 0 0 1px var(--primary-tint-strong);
}

.search-bar :deep(.el-input__inner) {
  height: 40px;
  font-size: 15px;
}

.search-bar__btn {
  height: 48px;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 600;
}

.search-icon {
  font-size: 20px;
  color: var(--text-muted);
}

.hot-cities {
  margin-top: 16px;
  font-size: 13px;
  opacity: 0.92;
}

.hot-cities__label {
  margin-right: 6px;
}

.hot-cities__item {
  margin-right: 12px;
  cursor: pointer;
  opacity: 0.92;
}

.hot-cities__item:hover {
  opacity: 1;
  text-decoration: underline;
}

.quick-actions {
  margin-bottom: 32px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.action-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 22px;
  border-radius: 18px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-soft);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.action-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow);
  border-color: var(--primary-tint-strong);
}

.action-card__icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--primary) 0%, var(--accent) 100%);
  color: #fff;
  font-size: 24px;
  font-weight: 600;
}

.action-card__body h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 4px;
}

.action-card__body p {
  font-size: 13px;
  color: var(--text-soft);
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 22px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(244, 248, 253, 0.6));
  border: 1px solid var(--line);
}

.stat-card__value {
  font-size: 32px;
  font-weight: 700;
  color: var(--primary-deep);
  line-height: 1.2;
}

.stat-card__label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-soft);
}

.panel {
  padding: 24px;
  border-radius: 18px;
  background: var(--panel-strong);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-soft);
  margin-bottom: 32px;
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
  padding: 56px 24px;
  text-align: center;
}

.empty-state__icon {
  font-size: 56px;
  margin-bottom: 12px;
  opacity: 0.6;
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

@media (max-width: 960px) {
  .search-bar {
    grid-template-columns: 1fr;
  }
  .card-grid {
    grid-template-columns: 1fr;
  }
  .stats {
    grid-template-columns: 1fr;
  }
}
</style>
