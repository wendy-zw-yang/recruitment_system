import http from './http'

/**
 * 个人中心 API 封装。
 *
 * 覆盖 Profile.vue 4 个 tab 对应的后端 endpoint。
 */
export const profileApi = {
  // 个人资料（所有人）
  getMe: () => http.get('/api/profile/me'),
  updateMe: (data) => http.put('/api/profile/me', data),
  changePassword: (data) => http.put('/api/profile/me/password', data),

  // 候选人：求职偏好
  getPreference: () => http.get('/api/profile/candidate/preference'),
  updatePreference: (data) => http.put('/api/profile/candidate/preference', data),

  // HR：公司自管理
  getCompany: () => http.get('/api/profile/company/me'),
  updateCompany: (data) => http.put('/api/profile/company/me', data)
}
