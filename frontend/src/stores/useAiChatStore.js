import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { chatApi } from '@/api/chat'

const SESSION_STORAGE_KEY = 'rs-ai-chat-session'

function genUuid() {
  // 简单 UUID v4 生成（不依赖 crypto.randomUUID 以兼容老浏览器）
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/**
 * AI-4 智能客服 Pinia store。
 *
 * <p>状态：</p>
 * <ul>
 *   <li>{@code currentSessionId}：会话 ID，从 localStorage 读取；首次使用自动创建</li>
 *   <li>{@code chatHistory}：当前会话历史（{@code [{role, content, createdAt}]}）</li>
 *   <li>{@code isStreaming}：是否正在流式接收（用于禁用发送按钮）</li>
 *   <li>{@code lastError}：最近一次错误（用于 UI 显示）</li>
 * </ul>
 *
 * <p>动作：</p>
 * <ul>
 *   <li>{@code bootstrap()}：初始化（从 localStorage 拉 sessionId；如有 token 则拉历史）</li>
 *   <li>{@code loadHistory()}：从后端拉最近 N 条历史</li>
 *   <li>{@code sendMessage(q)}：发送 + 接收 + append 流式回复</li>
 *   <li>{@code clearHistory()}：清空当前 session 历史</li>
 *   <li>{@code resetSession()}：新建会话（清空 sessionId + 历史）</li>
 * </ul>
 */
export const useAiChatStore = defineStore('aiChat', () => {
  /** 当前会话 ID（持久化 localStorage） */
  const currentSessionId = ref(localStorage.getItem(SESSION_STORAGE_KEY) || genUuid())

  /** 消息列表（按 createdAt ASC） */
  const chatHistory = ref([])

  /** 是否正在流式接收 */
  const isStreaming = ref(false)

  /** 最近一次错误（null 表示无） */
  const lastError = ref(null)

  /** 拉取历史 loading */
  const historyLoading = ref(false)

  /**
   * v0.6.2：阶段化状态，让前端能在 streamCall 阻塞时即时反馈
   * 阶段：idle → ready → searching → search_done → llm_start → streaming → done / error
   */
  const stage = ref('idle')
  const stageMeta = ref(null)

  /** abort controller（流式中断用） */
  let abortController = null

  function persistSession() {
    try {
      localStorage.setItem(SESSION_STORAGE_KEY, currentSessionId.value)
    } catch { /* 忽略 quota 异常 */ }
  }

  function bootstrap() {
    if (!currentSessionId.value) {
      currentSessionId.value = genUuid()
      persistSession()
    }
  }

  async function loadHistory(limit = 20) {
    bootstrap()
    historyLoading.value = true
    lastError.value = null
    try {
      const resp = await chatApi.history(currentSessionId.value, limit)
      chatHistory.value = resp?.messages || []
    } catch (e) {
      lastError.value = e?.message || '加载历史失败'
    } finally {
      historyLoading.value = false
    }
  }

  /**
   * 发送消息并流式接收 AI 回复。
   *
   * @param {string} question 用户问题
   * @returns {Promise<void>}
   */
  async function sendMessage(question) {
    bootstrap()
    const q = (question || '').trim()
    if (!q || isStreaming.value) return

    // 乐观追加 USER 消息（占位 createdAt，等后端入库后会更新；本地 UI 不阻塞）
    const userMsg = {
      id: `tmp-user-${Date.now()}`,
      role: 'USER',
      content: q,
      createdAt: new Date().toISOString(),
      pending: true
    }
    chatHistory.value.push(userMsg)

    // 流式 ASSISTANT 占位（首字到达前为空字符串）
    const asstMsg = {
      id: `tmp-asst-${Date.now()}`,
      role: 'ASSISTANT',
      content: '',
      createdAt: new Date().toISOString(),
      streaming: true,
      error: false
    }
    chatHistory.value.push(asstMsg)

    isStreaming.value = true
    lastError.value = null

    return new Promise((resolve) => {
      stage.value = 'ready'
      abortController = chatApi.streamChat(q, currentSessionId.value, {
        onReady: (sid) => {
          stage.value = 'searching'
        },
        onSearchDone: (hits) => {
          stage.value = 'search_done'
          stageMeta.value = hits
        },
        onLlmStart: () => {
          stage.value = 'llm_start'
        },
        onChunk: (text) => {
          stage.value = 'streaming'
          // 通过响应式代理更新，避免 asstMsg（局部变量，原对象）改不触发
          const idx = chatHistory.value.length - 1
          const cur = chatHistory.value[idx]
          chatHistory.value[idx] = { ...cur, content: cur.content + text }
        },
        onDone: (payload) => {
          stage.value = 'done'
          stageMeta.value = null
          asstMsg.streaming = false
          asstMsg.id = payload?.messageId ?? asstMsg.id
          asstMsg.createdAt = new Date().toISOString()
          userMsg.pending = false
          chatHistory.value = [...chatHistory.value]
          isStreaming.value = false
          abortController = null
          resolve()
        },
        onError: (msg) => {
          stage.value = 'error'
          stageMeta.value = msg
          asstMsg.error = true
          asstMsg.streaming = false
          // 如已收到部分内容，提示在末尾
          asstMsg.content = asstMsg.content
            ? asstMsg.content + `\n\n**[出错]** ${msg}`
            : `**[出错]** ${msg}`
          userMsg.pending = false
          chatHistory.value = [...chatHistory.value]
          isStreaming.value = false
          lastError.value = msg
          abortController = null
          resolve()
        }
      })
    })
  }

  function abortStream() {
    if (abortController) {
      try {
        abortController.abort()
      } catch { /* ignore */ }
      abortController = null
      isStreaming.value = false
      stage.value = 'idle'
      stageMeta.value = null
    }
  }

  async function clearHistory() {
    if (!currentSessionId.value) return
    try {
      await chatApi.clearHistory(currentSessionId.value)
    } catch { /* 忽略错误，本地也清 */ }
    chatHistory.value = []
    stage.value = 'idle'
    stageMeta.value = null
  }

  function resetSession() {
    abortStream()
    currentSessionId.value = genUuid()
    persistSession()
    chatHistory.value = []
    lastError.value = null
    stage.value = 'idle'
    stageMeta.value = null
  }

  const isEmpty = computed(() => chatHistory.value.length === 0)

  return {
    currentSessionId,
    chatHistory,
    isStreaming,
    lastError,
    historyLoading,
    stage,
    stageMeta,
    isEmpty,
    bootstrap,
    loadHistory,
    sendMessage,
    abortStream,
    clearHistory,
    resetSession
  }
})