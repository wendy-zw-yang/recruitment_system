import { defineStore } from 'pinia'
import { ref } from 'vue'
import { resumeApi } from '@/api/resume'

export const useResumeStore = defineStore('resume', () => {
  const current = ref(null)
  const loading = ref(false)
  const uploadProgress = ref(0)

  async function fetchCurrent() {
    loading.value = true
    try {
      current.value = await resumeApi.getCurrent()
    } finally {
      loading.value = false
    }
  }

  async function upload(file) {
    uploadProgress.value = 0
    const result = await resumeApi.upload(file, (e) => {
      if (e.total) {
        uploadProgress.value = Math.round((e.loaded * 100) / e.total)
      }
    })
    current.value = result.resume
    uploadProgress.value = 100
    return result
  }

  async function save(id, data) {
    current.value = await resumeApi.update(id, data)
  }

  async function archive(id) {
    await resumeApi.archive(id)
    current.value = null
  }

  return {
    current,
    loading,
    uploadProgress,
    fetchCurrent,
    upload,
    save,
    archive
  }
})
