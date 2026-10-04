import http from './http'

export const adminApi = {
  // ============ UC-34 用户管理 ============
  listUsers: (data) => http.post('/api/admin/users/list', data || {}),
  enableUser: (id) => http.post(`/api/admin/users/${id}/enable`),
  disableUser: (id) => http.post(`/api/admin/users/${id}/disable`),
  resetPassword: (id) => http.post(`/api/admin/users/${id}/reset-password`),
  changeRole: (id, data) => http.put(`/api/admin/users/${id}/role`, data),

  // ============ UC-36 公司审核 ============
  listCompanies: (params) => http.post('/api/admin/companies/list', null, { params }),
  verifyCompany: (id) => http.post(`/api/admin/companies/${id}/verify`),
  rejectCompany: (id, data) => http.post(`/api/admin/companies/${id}/reject`, data)

  // v0.4 删除：UC-35 职位审核（HR 自审自管）
}