<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/useAuthStore'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mode = ref('password')
const loading = ref(false)
const passwordFormRef = ref(null)
const codeFormRef = ref(null)

const passwordForm = reactive({ email: '', password: '' })
const codeForm = reactive({ email: '', code: '' })

const passwordRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const codeRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为 6 位', trigger: 'blur' }
  ]
}

async function doPasswordLogin() {
  if (!passwordFormRef.value) return
  try {
    await passwordFormRef.value.validate()
  } catch {
    ElMessage.warning('请检查必填项')
    return
  }
  loading.value = true
  try {
    const result = await authApi.login(passwordForm)
    auth.setSession(result.token, result.userInfo)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/home')
  } finally {
    loading.value = false
  }
}

async function sendCode() {
  if (!codeForm.email) {
    ElMessage.warning('请填写邮箱')
    return
  }
  await authApi.sendLoginCode(codeForm.email)
  ElMessage.info('验证码已发送（开发态请查看后端 console）')
}

async function doCodeLogin() {
  if (!codeFormRef.value) return
  try {
    await codeFormRef.value.validate()
  } catch {
    ElMessage.warning('请检查必填项')
    return
  }
  loading.value = true
  try {
    const result = await authApi.codeLogin(codeForm)
    auth.setSession(result.token, result.userInfo)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/home')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-shell">
    <div class="auth-hero">
      <div class="auth-hero__brand">
        <span class="brand-mark">H</span>
        <span class="brand-name">汇聘</span>
      </div>
      <h2>欢迎回到汇聘</h2>
      <p>与心仪的职位相遇，与靠谱的同行者并肩。</p>
      <ul class="auth-hero__list">
        <li>· 海量职位，每日更新</li>
        <li>· 简历智能匹配，主动推荐</li>
        <li>· 与 HR 直接对话，沟通更高效</li>
      </ul>
    </div>

    <div class="auth-form-wrap">
      <el-card class="auth-card" shadow="never">
        <h1 class="auth-title">登录</h1>
        <p class="auth-subtitle">还没有账号？<router-link to="/register" class="link">立即注册</router-link></p>

        <el-tabs v-model="mode" class="auth-tabs">
          <el-tab-pane label="密码登录" name="password">
            <el-form
              ref="passwordFormRef"
              :model="passwordForm"
              :rules="passwordRules"
              label-position="top"
              @submit.prevent="doPasswordLogin"
            >
              <el-form-item label="邮箱" prop="email">
                <el-input v-model="passwordForm.email" placeholder="请输入邮箱" size="large" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input v-model="passwordForm.password" type="password" show-password placeholder="请输入密码" size="large" />
              </el-form-item>
              <el-button type="primary" :loading="loading" native-type="submit" size="large" class="submit-btn">登录</el-button>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="验证码登录" name="code">
            <el-form
              ref="codeFormRef"
              :model="codeForm"
              :rules="codeRules"
              label-position="top"
              @submit.prevent="doCodeLogin"
            >
              <el-form-item label="邮箱" prop="email">
                <el-input v-model="codeForm.email" placeholder="请输入邮箱" size="large" />
              </el-form-item>
              <el-form-item label="验证码" prop="code">
                <el-input v-model="codeForm.code" placeholder="6 位验证码" size="large">
                  <template #append>
                    <el-button @click="sendCode">获取</el-button>
                  </template>
                </el-input>
              </el-form-item>
              <el-button type="primary" :loading="loading" native-type="submit" size="large" class="submit-btn">登录</el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.auth-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.auth-hero {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 80px;
  background: linear-gradient(135deg, #1d6fd8 0%, #06b6d4 100%);
  color: #fff;
}

.auth-hero__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 32px;
}

.auth-hero__brand .brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  font-weight: 700;
  font-size: 22px;
}

.auth-hero__brand .brand-name {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.auth-hero h2 {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 12px;
}

.auth-hero p {
  font-size: 15px;
  opacity: 0.92;
  margin-bottom: 28px;
}

.auth-hero__list {
  font-size: 14px;
  opacity: 0.9;
}

.auth-hero__list li {
  padding: 6px 0;
}

.auth-form-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 40px;
  background: rgba(255, 255, 255, 0.7);
}

.auth-card {
  width: 100%;
  max-width: 420px;
  padding: 8px;
  background: transparent;
  border: none;
}

.auth-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 6px;
}

.auth-subtitle {
  font-size: 14px;
  color: var(--text-soft);
  margin-bottom: 24px;
}

.link {
  color: var(--primary);
  font-weight: 500;
}

.link:hover {
  text-decoration: underline;
}

.auth-tabs {
  margin-bottom: 8px;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
  font-weight: 600;
}

@media (max-width: 900px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }
  .auth-hero {
    padding: 48px 32px;
  }
  .auth-form-wrap {
    padding: 32px 24px;
  }
}
</style>
