import http from './http'

export const dictApi = {
  industries: () => http.get('/api/admin/dict/industries'),
  cities: () => http.get('/api/admin/dict/cities'),
  skillSuggestions: () => http.get('/api/admin/dict/skill-suggestions')
}
