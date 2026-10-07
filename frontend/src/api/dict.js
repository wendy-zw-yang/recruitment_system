import http from './http'

export const dictApi = {
  // ============ 读（公开） ============
  industries: () => http.get('/api/admin/dict/industries'),
  cities: () => http.get('/api/admin/dict/cities'),
  skillSuggestions: () => http.get('/api/admin/dict/skill-suggestions'),

  // ============ 写（ADMIN） ============

  // 行业
  addIndustry: (data) => http.post('/api/admin/dict/industries', data),
  updateIndustry: (id, data) => http.put(`/api/admin/dict/industries/${id}`, data),
  deleteIndustry: (id) => http.delete(`/api/admin/dict/industries/${id}`),

  // 城市
  addCity: (data) => http.post('/api/admin/dict/cities', data),
  updateCity: (id, data) => http.put(`/api/admin/dict/cities/${id}`, data),
  deleteCity: (id) => http.delete(`/api/admin/dict/cities/${id}`),

  // 技能建议池
  addSkillSuggestion: (data) => http.post('/api/admin/dict/skill-suggestions', data),
  updateSkillSuggestion: (id, data) => http.put(`/api/admin/dict/skill-suggestions/${id}`, data),
  deleteSkillSuggestion: (id) => http.delete(`/api/admin/dict/skill-suggestions/${id}`)
}