<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/useAuthStore'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const navItems = computed(() => {
  const items = [{ label: '首页', to: '/home' }]
  if (route.path === '/home') return items

  if (auth.isCandidate) {
    items.push({ label: '简历', to: '/resume' })
    items.push({ label: '职位', to: '/jobs' })
    items.push({ label: '消息', placeholder: true })
  } else if (auth.isHR) {
    items.push({ label: '职位管理', to: '/hr/jobs' })
    items.push({ label: '收到的简历', placeholder: true })
    items.push({ label: '消息', placeholder: true })
  }
  return items
})

function comingSoon() {
  ElMessage.info('该功能即将上线')
}

function goProfile() {
  router.push('/profile')
}

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="top-bar-shell">
    <header class="top-bar">
      <div class="top-bar__left">
        <div class="brand" @click="router.push('/home')">
          <span class="brand-mark">H</span>
          <span class="brand-name">汇聘</span>
        </div>
        <nav class="nav">
          <a
            v-for="item in navItems"
            :key="item.label"
            :href="item.placeholder ? '#' : item.to"
            class="nav-item"
            :class="{ 'nav-item--active': route.path === item.to && !item.placeholder, 'nav-item--placeholder': item.placeholder }"
            @click.prevent="item.placeholder ? comingSoon() : router.push(item.to)"
          >
            {{ item.label }}
          </a>
        </nav>
      </div>

      <div class="top-bar__right">
        <template v-if="!auth.isLoggedIn">
          <a class="link-btn" href="/login" @click.prevent="router.push('/login')">登录</a>
          <a class="primary-btn" href="/register" @click.prevent="router.push('/register')">免费注册</a>
        </template>
        <template v-else>
          <button class="profile-btn" type="button" @click="goProfile">
            <span class="avatar">{{ (auth.userInfo?.username || auth.userInfo?.email || '?').slice(0, 1).toUpperCase() }}</span>
            <span class="profile-name">{{ auth.userInfo?.username || '我' }}</span>
          </button>
          <button class="logout-btn" type="button" @click="logout">退出</button>
        </template>
      </div>
    </header>
  </div>

  <main class="main-content">
    <slot />
  </main>
</template>

<style scoped>
.top-bar-shell {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 100;
  width: 100%;
  height: 72px;
  background: linear-gradient(180deg,
      var(--topbar-mask-start) 0%,
      var(--topbar-mask-middle) 60%,
      var(--topbar-mask-end) 100%);
  pointer-events: none;
}

.top-bar {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  pointer-events: auto;
  width: min(1200px, calc(100% - 32px));
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-radius: 16px;
  border: 1px solid var(--line);
  background: var(--topbar-panel);
  backdrop-filter: blur(14px);
  box-shadow: var(--shadow-soft);
}

.top-bar__left,
.top-bar__right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--primary) 0%, var(--accent) 100%);
  color: #fff;
  font-weight: 700;
  font-size: 17px;
  letter-spacing: -0.5px;
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
  letter-spacing: 0.04em;
}

.nav {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 24px;
}

.nav-item {
  display: inline-flex;
  align-items: center;
  height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  color: var(--text-soft);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease;
}

.nav-item:hover {
  color: var(--primary);
  background: var(--tab-hover);
}

.nav-item--active {
  color: var(--primary);
  background: var(--primary-tint);
  font-weight: 600;
}

.nav-item--placeholder {
  color: var(--text-muted);
  opacity: 0.7;
}

.nav-item--placeholder:hover {
  color: var(--primary);
  opacity: 1;
}

.profile-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 14px 4px 4px;
  height: 38px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--panel-strong);
  color: var(--text);
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease;
}

.profile-btn:hover {
  border-color: var(--primary);
  background: var(--primary-tint);
}

.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--primary) 0%, var(--accent) 100%);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.profile-name {
  font-size: 14px;
  font-weight: 500;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link-btn {
  font-size: 14px;
  color: var(--text-soft);
  padding: 0 12px;
  cursor: pointer;
  transition: color 0.18s ease;
}

.link-btn:hover {
  color: var(--primary);
}

.primary-btn {
  display: inline-flex;
  align-items: center;
  height: 36px;
  padding: 0 18px;
  border-radius: 999px;
  background: var(--primary);
  color: var(--button-text);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.18s ease, transform 0.18s ease;
}

.primary-btn:hover {
  background: var(--primary-deep);
  transform: translateY(-1px);
}

.logout-btn {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  padding: 0 8px;
}

.logout-btn:hover {
  color: var(--danger);
}

.main-content {
  min-height: 100vh;
}

@media (max-width: 720px) {
  .top-bar {
    padding: 0 14px;
    height: auto;
    min-height: 56px;
  }
  .top-bar__left {
    gap: 8px;
  }
  .nav {
    margin-left: 8px;
    gap: 0;
  }
  .nav-item {
    padding: 0 10px;
    font-size: 13px;
  }
  .brand-name {
    display: none;
  }
  .profile-name {
    display: none;
  }
  .logout-btn {
    display: none;
  }
}
</style>
