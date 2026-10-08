import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * v0.7.3：AI 推荐打分结果（subscribe via SSE）。
 *
 * <p>后端异步触发 LLM 重排 → 写缓存 + SSE push
 * {@code event=recommendation_ready, payload={scores: {jobId: score}, jobsCount}}。
 * 前端收到事件后用 {@link #consumeLatestScores} 取一次数据，然后重拉推荐列表即可拿到新分数。</p>
 *
 * <p>典型用法（CandidateHome.vue）：</p>
 * <pre>
 *   const recommendStore = useRecommendStore()
 *   watch(() => recommendStore.lastEvent, (e) => {
 *     if (!e) return
 *     ElMessage.success(`AI 已优化推荐（最高 ${maxScore}）`)
 *     loadRecommended()  // 重拉，fillAiScore 会注入
 *     recommendStore.consumeLatestScores()
 *   })
 * </pre>
 */
export const useRecommendStore = defineStore('recommend', () => {
  /** 最新一次 AI 打分事件（含 scores + jobsCount）；消费后置 null */
  const lastEvent = ref(null)

  /**
   * 推荐 store 缓存当前 userId 的 AI scores（jobId → score）。
   * 由 CandidateHome.vue 读取并应用到当前列表。
   */
  const scores = ref({})

  function setEvent(event, scoresMap) {
    lastEvent.value = event
    scores.value = scoresMap || {}
  }

  function consumeLatestScores() {
    const s = scores.value
    scores.value = {}
    lastEvent.value = null
    return s
  }

  return {
    lastEvent,
    scores,
    setEvent,
    consumeLatestScores
  }
})