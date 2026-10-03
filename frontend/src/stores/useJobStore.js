import { defineStore } from 'pinia'
import { ref } from 'vue'
import { jobApi } from '@/api/job'

export const useJobStore = defineStore('job', () => {
  const list = ref([])
  const total = ref(0)
  const loading = ref(false)

  async function fetchList(params) {
    loading.value = true
    try {
      const page = await jobApi.list(params)
      list.value = page.records || []
      total.value = page.total || 0
    } finally {
      loading.value = false
    }
  }

  async function fetchMine(params) {
    loading.value = true
    try {
      const page = await jobApi.listMine(params)
      list.value = page.records || []
      total.value = page.total || 0
    } finally {
      loading.value = false
    }
  }

  async function toggleFavorite(id) {
    const res = await jobApi.toggleFavorite(id)
    return res.favorited
  }

  return {
    list,
    total,
    loading,
    fetchList,
    fetchMine,
    toggleFavorite
  }
})
