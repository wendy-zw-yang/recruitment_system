import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { messageApi } from '@/api/message'
import { createMessageSseClient } from '@/api/sse'
import { useAuthStore } from '@/stores/useAuthStore'

/**
 * §5 消息中心 Pinia store。
 *
 * <p>状态：</p>
 * <ul>
 *   <li>{@code conversations}：会话列表（按 lastMessageAt DESC）</li>
 *   <li>{@code currentConversationId / currentMessages}：当前打开的会话 + 消息流</li>
 *   <li>{@code totalUnread}：未读消息总数（首页 stats）</li>
 *   <li>{@code sseConnected}：SSE 是否在线</li>
 *   <li>{@code sending / loading}：发送 / 加载 loading</li>
 * </ul>
 *
 * <p>动作：</p>
 * <ul>
 *   <li>{@code bootstrapSse()}：登录后建立 SSE 连接（应用全局只需一次）</li>
 *   <li>{@code loadConversations()}：拉会话列表</li>
 *   <li>{@code openConversation(id)}：进入会话（拉消息 + 自动已读）</li>
 *   <li>{@code sendMessage(content, attachments?)}：发消息</li>
 *   <li>{@code closeConversation()}：离开会话页（不关闭 SSE）</li>
 * </ul>
 */
export const useMessageStore = defineStore('message', () => {
  const auth = useAuthStore()

  // 会话列表
  const conversations = ref([])

  // 当前打开的会话
  const currentConversationId = ref(null)
  const currentMessages = ref([])
  const currentPeer = ref(null)  // { id, name, role }

  // 未读
  const totalUnread = ref(0)

  // SSE 状态
  const sseConnected = ref(false)
  let sseClient = null

  // Loading
  const loadingList = ref(false)
  const loadingDetail = ref(false)
  const sending = ref(false)

  const hasCurrent = computed(() => currentConversationId.value != null)

  // ============ SSE 生命周期 ============

  /**
   * 建立 / 重连 SSE。登录后调用一次；token 变更时断开重连。
   */
  function bootstrapSse() {
    if (!auth.isLoggedIn) {
      teardownSse()
      return
    }
    if (sseClient) return  // 已连接
    sseClient = createMessageSseClient({
      onConnect: () => { sseConnected.value = true },
      onDisconnect: () => { sseConnected.value = false },
      onNewMessage: handleNewMessage,
      onReadReceipt: handleReadReceipt
    })
    sseClient.connect()
  }

  function teardownSse() {
    if (sseClient) {
      sseClient.disconnect()
      sseClient = null
    }
    sseConnected.value = false
  }

  /**
   * 收到 new_message 事件。
   *
   * <p>策略：</p>
   * <ul>
   *   <li>若命中当前会话 → 直接 append 消息流（SSE 不增加未读）</li>
   *   <li>若非当前会话 → 更新会话列表（lastMessagePreview + unreadCount +1）</li>
   * </ul>
   */
  function handleNewMessage(event) {
    const msg = event?.message
    const convId = event?.conversationId
    if (!msg || !convId) return

    // 找会话
    const conv = conversations.value.find((c) => c.id === convId)
    const previewText = msg.senderRole === 'SYSTEM'
      ? '[系统通知] ' + truncate(msg.content)
      : truncate(msg.content)

    if (convId === currentConversationId.value) {
      // 当前会话：直接 append
      const exists = currentMessages.value.some((m) => m.id === msg.id)
      if (!exists) {
        currentMessages.value = [...currentMessages.value, msg]
      }
      // 如果是对方发的，本地就即时标已读（auto-read 已在后端 open 触发；这里推送收的已经是新消息）
      // 不增加未读数（因为在当前会话看）
    } else {
      // 其他会话：更新 lastMessagePreview + unreadCount +1（除非发送方是当前用户）
      if (conv) {
        const senderIsMe = msg.senderId === auth.userInfo?.id
        conv.lastMessagePreview = previewText
        conv.lastMessageAt = msg.createdAt
        if (!senderIsMe) {
          conv.unreadCount = (conv.unreadCount || 0) + 1
          totalUnread.value++
        }
      } else {
        // 会话列表里没有 → 拉一次列表（保守兜底）
        loadConversations()
      }
    }
  }

  /**
   * 收到 read_receipt 事件：对方已读了当前用户的消息。
   *
   * <p>策略：把当前会话中对应消息的 readFlag 置 1（仅 UI 表现）。</p>
   */
  function handleReadReceipt(event) {
    const ids = event?.messageIds || []
    if (!ids.length) return
    const idSet = new Set(ids)
    currentMessages.value = currentMessages.value.map((m) =>
      idSet.has(m.id) ? { ...m, readFlag: 1 } : m
    )
  }

  // ============ API 调用 ============

  async function loadConversations() {
    loadingList.value = true
    try {
      const list = await messageApi.listConversations()
      conversations.value = list || []
      totalUnread.value = conversations.value.reduce((sum, c) => sum + (c.unreadCount || 0), 0)
    } catch (e) {
      console.warn('[messageStore] loadConversations failed', e?.message)
    } finally {
      loadingList.value = false
    }
  }

  async function loadUnreadStats() {
    try {
      const resp = await messageApi.unreadStats()
      totalUnread.value = resp?.total || 0
    } catch (e) {
      // 静默失败
    }
  }

  async function openConversation(id) {
    if (!id) return
    loadingDetail.value = true
    try {
      const resp = await messageApi.openConversation(id, 50)
      currentConversationId.value = id
      currentMessages.value = resp?.messages || []
      currentPeer.value = resp?.conversation ? {
        id: resp.conversation.peerId,
        name: resp.conversation.peerName,
        role: resp.conversation.peerRole
      } : null
      // 更新会话列表中的未读数（置 0）
      const conv = conversations.value.find((c) => c.id === id)
      if (conv && conv.unreadCount > 0) {
        totalUnread.value = Math.max(0, totalUnread.value - conv.unreadCount)
        conv.unreadCount = 0
      }
    } finally {
      loadingDetail.value = false
    }
  }

  function closeConversation() {
    currentConversationId.value = null
    currentMessages.value = []
    currentPeer.value = null
  }

  async function ensureConversationWithPeer(peerId) {
    const conv = await messageApi.createConversation(peerId)
    // 同步到列表（找不到就 push）
    const idx = conversations.value.findIndex((c) => c.id === conv.id)
    if (idx >= 0) {
      conversations.value[idx] = conv
    } else {
      conversations.value.unshift(conv)
    }
    return conv
  }

  async function sendMessage({ content, jobId, attachments }) {
    if (!currentConversationId.value) return null
    if (!content && (!attachments || attachments.length === 0)) return null
    sending.value = true
    try {
      const dto = await messageApi.sendMessage({
        conversationId: currentConversationId.value,
        content,
        jobId,
        attachments
      })
      // SSE 会推给自己；本地不主动追加（避免重复）
      return dto
    } finally {
      sending.value = false
    }
  }

  async function uploadAttachment(file) {
    return await messageApi.uploadAttachment(file)
  }

  function truncate(s) {
    if (!s) return ''
    return s.length > 40 ? s.slice(0, 40) + '…' : s
  }

  return {
    conversations,
    currentConversationId,
    currentMessages,
    currentPeer,
    totalUnread,
    sseConnected,
    loadingList,
    loadingDetail,
    sending,
    hasCurrent,
    bootstrapSse,
    teardownSse,
    loadConversations,
    loadUnreadStats,
    openConversation,
    closeConversation,
    ensureConversationWithPeer,
    sendMessage,
    uploadAttachment
  }
})
