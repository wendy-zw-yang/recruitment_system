import { defineStore } from 'pinia'
import { ref } from 'vue'
import { applicationApi } from '@/api/application'

/**
 * 候选人投递 store。
 *
 * <p>管理 myApplications 列表 + currentApplication 详情 + 撤回操作。
 * HR 端数据不进本 store（HR 端组件内自管）。</p>
 */
export const useApplicationStore = defineStore('application', () => {
  const myApplications = ref([])
  /**
   * 候选人投递总数（独立于 pageSize，用于首页 stats 的"已投递"计数）。
   * 当 myApplications.length < myApplicationsTotal 时说明有更多分页。
   */
  const myApplicationsTotal = ref(0)
  const currentApplication = ref(null)
  const loading = ref(false)
  const submitting = ref(false)

  async function fetchMine(pageNum = 1, pageSize = 100) {
    loading.value = true
    try {
      const res = await applicationApi.listMine({ pageNum, pageSize })
      // axios 拦截器已自动解 Result，res 即 IPage<T>
      myApplications.value = res?.records || []
      myApplicationsTotal.value = res?.total || 0
      return res
    } finally {
      loading.value = false
    }
  }

  async function fetchDetail(id) {
    loading.value = true
    try {
      currentApplication.value = await applicationApi.detail(id)
      return currentApplication.value
    } finally {
      loading.value = false
    }
  }

  async function apply(data) {
    submitting.value = true
    try {
      const resp = await applicationApi.apply(data)
      // v0.7.4.4：投递成功后立即刷新我的投递列表。
      // 历史 bug：apply() 仅返回 applyId，未更新 store.myApplications；
      // 完全依赖 MyApplications.vue onMounted 重新拉取。
      // 在某些场景下 onMounted 不会触发（如 keep-alive 缓存 / router.push 同 path 参数变化），
      // 导致用户投递后看"我的投递"仍是旧数据。
      // 修复：apply 成功同步触发 fetchMine，消除对 onMounted 的强依赖。
      await fetchMine(1, 100)
      return resp
    } finally {
      submitting.value = false
    }
  }

  async function withdraw(id) {
    const resp = await applicationApi.withdraw(id)
    // 同步更新列表中的对应项（局部更新 + 全量刷新，二者兼顾）
    const idx = myApplications.value.findIndex(a => a.id === id)
    if (idx >= 0) {
      myApplications.value[idx] = { ...myApplications.value[idx], ...resp }
    }
    // v0.7.4.4：撤回后也全量刷新一次，保证其他派生字段（如 status / withdrawable）一致
    await fetchMine(1, 100)
    return resp
  }

  return {
    myApplications,
    myApplicationsTotal,
    currentApplication,
    loading,
    submitting,
    fetchMine,
    fetchDetail,
    apply,
    withdraw
  }
})
