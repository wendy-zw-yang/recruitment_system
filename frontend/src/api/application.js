import http from './http'

/**
 * 投递 API 封装。
 *
 * <p>axios 拦截器已自动解 Result 包装（{@link ./http.js}），返回的 res 即 data 本身。
 * 分页接口返回 IPage<T>，访问 res.records / res.total。</p>
 */
export const applicationApi = {
  // ============ 候选人端 ============
  apply: (data) => http.post('/api/applications', data),
  listMine: (params) => http.get('/api/applications/mine', { params }),
  detail: (id) => http.get(`/api/applications/${id}`),
  withdraw: (id) => http.post(`/api/applications/${id}/withdraw`),

  // ============ HR 端 ============
  hrList: (data) => http.post('/api/applications/hr/list', data),
  hrDetail: (id) => http.get(`/api/applications/hr/${id}`),
  pushStatus: (id, data) => http.post(`/api/applications/hr/${id}/status`, data),
  listNotes: (id) => http.get(`/api/applications/hr/${id}/notes`),
  addNote: (id, data) => http.post(`/api/applications/hr/${id}/notes`, data),
  updateNote: (id, noteId, data) => http.put(`/api/applications/hr/${id}/notes/${noteId}`, data),
  deleteNote: (id, noteId) => http.delete(`/api/applications/hr/${id}/notes/${noteId}`),
  resumeSnapshot: (id) => http.get(`/api/applications/hr/${id}/resume-snapshot`)
}
