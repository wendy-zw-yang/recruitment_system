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

  /**
   * 删除候选人本人的简历。后端软删 resume/attachment + 删除磁盘文件 + 自动归档遗留 ACTIVE。
   * 删除后强制重新拉一次 current，确保 store 与后端一致（避免「看起来没删」错觉）。
   */
  async function remove(id) {
    await resumeApi.remove(id)
    await fetchCurrent()
    uploadProgress.value = 0
  }

  return {
    current,
    loading,
    uploadProgress,
    fetchCurrent,
    upload,
    save,
    archive,
    remove
  }
})
