import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/useAuthStore'

const http = axios.create({
  baseURL: '/',
  timeout: 60000
})

http.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) {
        return body.data
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const backendMsg = error.response?.data?.message
    let message = backendMsg || error.message || '网络错误'
    if (!backendMsg) {
      if (status === 401) message = '请先登录'
      else if (status === 403) message = '无权限访问'
      else if (status === 404) message = '资源不存在'
      else if (status >= 500) message = '服务器异常，请稍后重试'
      else if (error.code === 'ERR_NETWORK') message = '网络连接失败，请检查后端服务'
    }
    if (status === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      router.push({ name: 'login' })
    }
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  }
)

export default http
