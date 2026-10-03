<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'

const router = useRouter()
const role = ref('CANDIDATE')
const loading = ref(false)

const candidateForm = reactive({
  email: '',
  password: '',
  confirmPassword: ''
})
const hrForm = reactive({
  email: '',
  password: '',
  confirmPassword: '',
  companyName: '',
  scale: '',
  description: ''
})

const candidateRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== candidateForm.password) {
          callback(new Error('两次密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const hrRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== hrForm.password) {
          callback(new Error('两次密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  companyName: [
    { required: true, message: '请输入公司名', trigger: 'blur' }
  ]
}

const candidateFormRef = ref(null)
const hrFormRef = ref(null)

async function register() {
  const refToUse = role.value === 'CANDIDATE' ? candidateFormRef.value : hrFormRef.value
  if (!refToUse) return
  try {
    await refToUse.validate()
  } catch (err) {
    ElMessage.warning('请检查必填项')
    return
  }
  loading.value = true
  try {
    if (role.value === 'CANDIDATE') {
      await authApi.register({
        email: candidateForm.email,
        password: candidateForm.password
      })
    } else {
      await authApi.registerHr({
        email: hrForm.email,
        password: hrForm.password,
        companyName: hrForm.companyName,
        scale: hrForm.scale,
        description: hrForm.description
      })
    }
    ElMessage.success('注册成功，请登录')
    router.push('/login')
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
      <h2>加入汇聘</h2>
      <p>无论你是寻找下一份工作，还是寻找下一位伙伴，都从这里开始。</p>
      <ul class="auth-hero__list">
        <li v-if="role === 'CANDIDATE'">· 智能匹配适合你的职位</li>
        <li v-if="role === 'CANDIDATE'">· HR 直接与你沟通，缩短求职链路</li>
        <li v-if="role === 'HR'">· 一键发布职位并借助模板生成 JD</li>
        <li v-if="role === 'HR'">· 查看候选人简历，与合适的人直接开聊</li>
      </ul>
    </div>

    <div class="auth-form-wrap">
      <el-card class="auth-card" shadow="never">
        <h1 class="auth-title">创建账号</h1>
        <p class="auth-subtitle">已有账号？<router-link to="/login" class="link">直接登录</router-link></p>

        <el-radio-group v-model="role" class="role-toggle">
          <el-radio-button value="CANDIDATE">我是求职者</el-radio-button>
          <el-radio-button value="HR">我是 HR</el-radio-button>
        </el-radio-group>

        <el-form
          v-if="role === 'CANDIDATE'"
          ref="candidateFormRef"
          :model="candidateForm"
          :rules="candidateRules"
          label-position="top"
          @submit.prevent="register"
        >
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="candidateForm.email" placeholder="请输入邮箱" size="large" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="candidateForm.password" type="password" show-password placeholder="至少 8 位" size="large" />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="candidateForm.confirmPassword" type="password" show-password placeholder="再次输入密码" size="large" />
          </el-form-item>
          <el-button type="primary" :loading="loading" native-type="submit" size="large" class="submit-btn">注册</el-button>
        </el-form>

        <el-form
          v-else
          ref="hrFormRef"
          :model="hrForm"
          :rules="hrRules"
          label-position="top"
          @submit.prevent="register"
        >
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="hrForm.email" placeholder="请输入邮箱" size="large" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="hrForm.password" type="password" show-password placeholder="至少 8 位" size="large" />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="hrForm.confirmPassword" type="password" show-password placeholder="再次输入密码" size="large" />
          </el-form-item>
          <el-form-item label="公司名" prop="companyName">
            <el-input v-model="hrForm.companyName" placeholder="请输入公司名" size="large" />
          </el-form-item>
          <el-form-item label="规模">
            <el-input v-model="hrForm.scale" placeholder="如：100-500 人（可选）" size="large" />
          </el-form-item>
          <el-form-item label="公司简介">
            <el-input v-model="hrForm.description" type="textarea" :rows="3" placeholder="公司简介（可选）" />
          </el-form-item>
          <el-button type="primary" :loading="loading" native-type="submit" size="large" class="submit-btn">注册</el-button>
        </el-form>
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
  overflow-y: auto;
}

.auth-card {
  width: 100%;
  max-width: 480px;
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

.role-toggle {
  display: flex;
  width: 100%;
  margin-bottom: 24px;
}

.role-toggle :deep(.el-radio-button) {
  flex: 1;
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
