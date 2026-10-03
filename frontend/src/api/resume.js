import http from './http'

export const resumeApi = {
  getCurrent: () => http.get('/api/resumes/current'),
  upload: (file, onUploadProgress) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post('/api/resumes/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress
    })
  },
  update: (id, data) => http.put(`/api/resumes/${id}`, data),
  archive: (id) => http.post(`/api/resumes/${id}/archive`),
  attachmentDownloadUrl: (id) => `/api/resumes/attachment/${id}/download`
}
