import http from './http'

export const jobApi = {
  // HR 端
  listMine: (params) => http.get('/api/jobs/mine', { params }),
  create: (data) => http.post('/api/jobs', data),
  update: (id, data) => http.put(`/api/jobs/${id}`, data),
  publish: (id) => http.post(`/api/jobs/${id}/publish`),
  offline: (id) => http.post(`/api/jobs/${id}/offline`),
  deleteJob: (id) => http.post(`/api/jobs/${id}/delete`),
  polish: (data) => http.post('/api/jobs/polish', data),

  // 候选人 / 公开
  list: (params) => http.get('/api/jobs', { params }),
  detail: (id) => http.get(`/api/jobs/${id}`),
  toggleFavorite: (id) => http.post(`/api/jobs/${id}/favorite`)
}