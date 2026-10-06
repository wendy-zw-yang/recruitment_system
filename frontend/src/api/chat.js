import http from './http'
import { useAuthStore } from '@/stores/useAuthStore'

/**
 * AI-4 智能客服 API 封装。
 *
 * <p>流式端点不走 axios（axios 不支持 SSE），改用 fetch + ReadableStream。</p>
 */
export const chatApi = {
  /**
   * 历史消息（GET /api/ai/chat/history?sessionId=&limit=）
   * @returns {Promise<{sessionId:string, messages:Array<{id:number, role:string, content:string, createdAt:string}>}>}
   */
  history(sessionId, limit = 20) {
    return http.get('/api/ai/chat/history', { params: { sessionId, limit } })
  },

  /**
   * 清空当前 session 历史（DELETE /api/ai/chat/history?sessionId=）
   * @returns {Promise<number>} 受影响行数
   */
  clearHistory(sessionId) {
    return http.delete('/api/ai/chat/history', { params: { sessionId } })
  },

  /**
   * 流式问答（GET /api/ai/chat/stream?question=&sessionId= 走 SSE）。
   *
   * <p>v0.6.2 新事件：ready / search_done / llm_start，让前端在 streamCall 阻塞等 LLM 时
   * 能即时显示"正在思考"状态。</p>
   *
   * <p>SSE 事件协议：</p>
   * <ul>
   *   <li>{@code event: ready       data: {"sessionId": "..."}} — 连接建立</li>
   *   <li>{@code event: search_done data: {"hits": N}} — 关键词检索完成</li>
   *   <li>{@code event: llm_start   data: {}} — LLM 调用开始</li>
   *   <li>{@code event: delta       data: {"text": "..."}} — 每 chunk；触发 {@code onChunk}</li>
   *   <li>{@code event: done        data: {"sessionId": "...", "messageId": N}} — 完成；触发 {@code onDone}</li>
   *   <li>{@code event: error       data: {"message": "..."}} — 失败；触发 {@code onError}</li>
   * </ul>
   *
   * @param {string} question  用户问题
   * @param {string} sessionId 会话 UUID
   * @param {object} callbacks { onReady, onSearchDone, onLlmStart, onChunk, onDone, onError }
   * @returns {AbortController} 可调 .abort() 中断流
   */
  streamChat(question, sessionId, { onReady, onSearchDone, onLlmStart, onChunk, onDone, onError } = {}) {
    const auth = useAuthStore()
    const controller = new AbortController()
    const params = new URLSearchParams()
    params.set('question', question)
    if (sessionId) params.set('sessionId', sessionId)

    // 标记是否已收到 done/error；若流关闭但都未收到 → 视为异常
    let finished = false
    let gotAnyChunk = false

    const finishWithError = (msg) => {
      if (finished) return
      finished = true
      clearTimeout(watchdog)
      onError && onError(msg)
    }
    const finishWithDone = (payload) => {
      if (finished) return
      finished = true
      clearTimeout(watchdog)
      onDone && onDone(payload)
    }

    // v0.6.3：客户端超时守护。30 秒无 done/error 事件 → 强制结束
    const CHAT_IDLE_TIMEOUT_MS = 30000
    const watchdog = setTimeout(() => {
      if (finished) return
      const msg = gotAnyChunk
        ? '客服响应超时，已收到部分内容'
        : '客服无响应（客户端超时）'
      finishWithError(msg)
    }, CHAT_IDLE_TIMEOUT_MS)

    fetch(`/api/ai/chat/stream?${params.toString()}`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${auth.token || ''}`,
        Accept: 'text/event-stream'
      },
      signal: controller.signal
    })
      .then(async (resp) => {
        if (!resp.ok) {
          const text = await resp.text().catch(() => '')
          finishWithError(`HTTP ${resp.status}${text ? '：' + text.slice(0, 200) : ''}`)
          return
        }
        if (!resp.body) {
          finishWithError('浏览器不支持 ReadableStream')
          return
        }

        const reader = resp.body.getReader()
        const decoder = new TextDecoder('utf-8')
        let buffer = ''

        try {
          while (true) {
            const { value, done } = await reader.read()
            if (done) break
            buffer += decoder.decode(value, { stream: true })

            const { complete, remainder } = splitSseBlocks(buffer)
            buffer = remainder
            if (complete.length === 0) continue

            for (const block of complete) {
              let eventName = 'message'
              const dataParts = []
              for (const line of block.split(/\r?\n/)) {
                if (line.startsWith('event:')) {
                  eventName = line.slice(6).trim()
                } else if (line.startsWith('data:')) {
                  dataParts.push(line.slice(5).trim())
                }
              }
              const dataStr = dataParts.join('\n')
              if (!dataStr) continue
              let payload = null
              try {
                payload = JSON.parse(dataStr)
              } catch {
                payload = { raw: dataStr }
              }
              if (eventName === 'ready' && payload.sessionId) {
                onReady && onReady(payload.sessionId)
              } else if (eventName === 'search_done' && payload.hits != null) {
                onSearchDone && onSearchDone(payload.hits)
              } else if (eventName === 'llm_start') {
                onLlmStart && onLlmStart()
              } else if (eventName === 'delta' && payload.text != null) {
                gotAnyChunk = true
                onChunk && onChunk(payload.text)
              } else if (eventName === 'done') {
                finishWithDone(payload)
                return
              } else if (eventName === 'error') {
                finishWithError(payload.message || '客服暂不可用')
                return
              }
            }
          }

          // 流结束但未收到 done/error
          if (!finished) {
            // 残留的尾部（可能含未解析 done 块）尝试解析
            if (buffer.trim().length > 0) {
              try {
                const payload = JSON.parse(buffer.trim())
                if (payload.text) {
                  gotAnyChunk = true
                  onChunk && onChunk(payload.text)
                }
              } catch { /* ignore */ }
            }
            if (!gotAnyChunk) {
              finishWithError('连接中断，未收到回复')
            } else {
              // 收到过 chunk 但无 done 事件 → 视为完成（兜底）
              finishWithDone({ messageId: null, sessionId: null })
            }
          }
        } catch (readErr) {
          finishWithError(readErr.message || '流式读取失败')
        }
      })
      .catch((err) => {
        if (err.name === 'AbortError') {
          // 用户主动停止不视为错误
          if (!finished) {
            finished = true
            onDone && onDone({ aborted: true })
          }
          return
        }
        finishWithError(err.message || '网络异常')
      })

    return controller
  }
}

/**
 * SSE 事件块按 `\n\n` 或 `\r\n\r\n` 切分。
 * 返回 { complete: 完整块数组, remainder: 未结束尾部 }。
 * 完整块必须以行分隔结尾；未结束剩余进入下一轮 reader.read() 后处理。
 */
function splitSseBlocks(raw) {
  const blocks = []
  let start = 0
  for (let i = 0; i < raw.length - 1; i++) {
    if (raw[i] === '\n' && raw[i + 1] === '\n') {
      blocks.push(raw.slice(start, i))
      start = i + 2
      i = start - 1
    } else if (raw[i] === '\r' && raw[i + 1] === '\n' && raw[i + 2] === '\r' && raw[i + 3] === '\n') {
      blocks.push(raw.slice(start, i))
      start = i + 4
      i = start - 1
    }
  }
  const remainder = start < raw.length ? raw.slice(start) : ''
  return { complete: blocks, remainder }
}