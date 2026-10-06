import http from './http'

/**
 * §5 消息中心 API 封装。
 */
export const messageApi = {
  // ============ 会话 ============

  /**
   * 创建或获取会话（按 (hr, candidate) 二元组）。
   * @param {number} peerId 对方用户 ID
   * @returns {Promise<ConversationDto>}
   */
  createConversation(peerId) {
    return http.post('/api/conversations', { peerId })
  },

  /**
   * 我的会话列表（按 last_message_at DESC）。
   * @returns {Promise<ConversationDto[]>}
   */
  listConversations() {
    return http.get('/api/conversations/mine')
  },

  /**
   * 进入会话：返回会话 + 最近消息 + 自动已读。
   * @param {number} id 会话 ID
   * @param {number} recentLimit 默认 50
   * @returns {Promise<{conversation: ConversationDto, messages: MessageDto[]}>}
   */
  openConversation(id, recentLimit = 50) {
    return http.get(`/api/conversations/${id}`, { params: { recentLimit } })
  },

  /**
   * 未读消息总数（首页 stats 用）。
   */
  unreadStats() {
    return http.get('/api/conversations/unread-stats')
  },

  // ============ 消息 ============

  /**
   * 发消息（文本 / 附件）。
   * @param {object} payload { conversationId, content?, jobId?, attachments?: [{fileName,filePath,fileSize,mimeType}] }
   * @returns {Promise<MessageDto>}
   */
  sendMessage(payload) {
    return http.post('/api/messages', payload)
  },

  /**
   * 拉取会话消息（POST /api/messages/conversation/{id}/list）。
   * @returns {Promise<{pageNum, pageSize, total, records: MessageDto[]}>}
   */
  listMessages(conversationId, pageNum = 1, pageSize = 50) {
    return http.post(`/api/messages/conversation/${conversationId}/list`, { pageNum, pageSize })
  },

  // ============ 附件 ============

  /**
   * 上传消息附件（仅落盘，返回临时引用）。
   * @param {File} file
   * @returns {Promise<{fileName,filePath,fileSize,mimeType}>}
   */
  uploadAttachment(file) {
    const fd = new FormData()
    fd.append('file', file)
    return http.post('/api/messages/attachment/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}
