import http from './http'

export const authApi = {
  register: (data) => http.post('/api/auth/register', data),
  registerHr: (data) => http.post('/api/auth/register-hr', data),
  login: (data) => http.post('/api/auth/login', data),
  sendLoginCode: (email) => http.post('/api/auth/send-login-code', null, { params: { email } }),
  codeLogin: (data) => http.post('/api/auth/code-login', data)
}
