import { useAuthStore } from '@/stores/useAuthStore'

/**
 * §5 消息中心 SSE 客户端。
 *
 * <p>基于浏览器原生 EventSource（自动重连），不适合 axios 拦截器统一处理。</p>
 *
 * <p>事件协议（与 {@code SseEmitterManager.pushToUser} 一致）：</p>
 * <ul>
 *   <li>{@code new_message} — 新消息（含 conversationId + message 全字段）</li>
 *   <li>{@code read_receipt} — 对方已读（含 conversationId + readerId + messageIds）</li>
 * </ul>
 *
 * <p>用法：</p>
 * <pre>
 *   const client = createMessageSseClient({
 *     onNewMessage: (event) => { ... },
 *     onReadReceipt: (event) => { ... },
 *     onConnect: () => { ... },
 *     onDisconnect: () => { ... }
 *   })
 *   client.connect()
 *   // 卸载时：
 *   client.disconnect()
 * </pre>
 */
export function createMessageSseClient(handlers = {}) {
  let source = null

  function connect() {
    const auth = useAuthStore()
    if (!auth.token) {
      // 未登录：不连
      return
    }
    if (source) {
      // 已连接：先断开
      try { source.close() } catch { /* ignore */ }
    }
    // EventSource 不支持自定义 header；token 走 query 参数避免被日志记录
    // 注：当前后端 /api/sse/subscribe 从 SecurityContext 取 token；如需支持 URL 携带
    // 应在 JwtFilter 增加查询参数分支。为简化，此处沿用 Cookie / Bearer 拦截器期望。
    // 实际：浏览器 EventSource 不能携带 Authorization header，所以采用 URL query 参数。
    const url = `/api/sse/subscribe?t=${encodeURIComponent(auth.token)}`
    source = new EventSource(url)

    source.addEventListener('ready', () => handlers.onConnect && handlers.onConnect())
    source.addEventListener('new_message', (e) => {
      try {
        const payload = JSON.parse(e.data)
        handlers.onNewMessage && handlers.onNewMessage(payload)
      } catch (err) {
        console.warn('[messageSse] parse new_message failed', err)
      }
    })
    source.addEventListener('read_receipt', (e) => {
      try {
        const payload = JSON.parse(e.data)
        handlers.onReadReceipt && handlers.onReadReceipt(payload)
      } catch (err) {
        console.warn('[messageSse] parse read_receipt failed', err)
      }
    })
    source.onerror = () => {
      handlers.onDisconnect && handlers.onDisconnect()
      // EventSource 自动重连；不要手动 close
    }
  }

  function disconnect() {
    if (source) {
      try { source.close() } catch { /* ignore */ }
      source = null
    }
  }

  return { connect, disconnect }
}
