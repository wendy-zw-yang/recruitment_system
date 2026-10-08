<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/useAuthStore'
import { resumeApi } from '@/api/resume'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const visible = ref(false)
/** 当前候选人简历是否未上传（ACTIVE 为 null） */
const hasNoResume = ref(false)

async function checkResume() {
  if (!auth.isCandidate) {
    visible.value = false
    return
  }
  try {
    const dto = await resumeApi.getCurrent()
    // dto 为 null（无 ACTIVE）或 dto.archived=true → 视为"未上传"
    hasNoResume.value = !dto || dto.archived === true
  } catch (e) {
    hasNoResume.value = false
  }
  visible.value = hasNoResume.value
}

function goUpload() {
  router.push('/resume')
}

function dismiss() {
  visible.value = false
  // 关闭后写入 sessionStorage，本次会话不再显示（避免粗暴 v1 重复骚扰）
  sessionStorage.setItem('rs-banner-resume-dismissed', '1')
}

// 每次进入 /home 检查一次；同时欢迎页 dimming 状态下不再出现
const dismissed = sessionStorage.getItem('rs-banner-resume-dismissed') === '1'

onMounted(() => {
  if (dismissed) return
  checkResume()
})

// 路由变化也重新检查（如候选人刚上传完简历返回 /home）
watch(() => route.path, () => {
  if (dismissed) return
  checkResume()
})
</script>

<template>
  <Transition name="banner-fade">
    <div v-if="visible" class="resume-banner">
      <div class="resume-banner__body">
        <span class="resume-banner__icon">📄</span>
        <span class="resume-banner__text">
          上传简历以获得更精准的 AI 推荐（基于技能 / 经验匹配）
        </span>
      </div>
      <div class="resume-banner__actions">
        <el-button type="primary" size="small" round @click="goUpload">立即上传</el-button>
        <el-button text size="small" @click="dismiss">不再提示</el-button>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.resume-banner {
  /* 固定定位在 top bar (72px) 下方，避免影响各页 page-shell 的 padding-top 计算 */
  position: fixed;
  top: 84px;
  right: 16px;
  left: 16px;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 20px;
  max-width: 1200px;
  margin: 0 auto;
  background: linear-gradient(90deg, #e0f2fe 0%, #ddd6fe 100%);
  border: 1px solid #c7d2fe;
  border-radius: 12px;
  color: #312e81;
  font-size: 13px;
  box-shadow: 0 1px 6px rgba(99, 102, 241, 0.12);
}
.resume-banner__body {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.resume-banner__icon {
  font-size: 18px;
  flex-shrink: 0;
}
.resume-banner__text {
  font-weight: 500;
}
.resume-banner__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.banner-fade-enter-active,
.banner-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.banner-fade-enter-from,
.banner-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 720px) {
  .resume-banner {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }
  .resume-banner__actions {
    justify-content: flex-end;
  }
}
</style>