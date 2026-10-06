<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/useAuthStore'
import { useMessageStore } from '@/stores/useMessageStore'
import ChatListItem from './ChatListItem.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const store = useMessageStore()

const peerIdFromQuery = computed(() => {
  const v = route.query.peer
  return v ? Number(v) : null
})

const convIdFromQuery = computed(() => {
  const v = route.query.conv
  return v ? Number(v) : null
})

const input = ref('')
const attachments = ref([]) // [{fileName,filePath,fileSize,mimeType}]
const messagesRef = ref(null)  // for scroll-to-bottom

onMounted(async () => {
  store.bootstrapSse()
  await store.loadConversations()

  // 处理 ?peer= 触发新建会话（来自投递详情页"联系 HR / 候选人"）
  if (peerIdFromQuery.value) {
    try {
      const conv = await store.ensureConversationWithPeer(peerIdFromQuery.value)
      await store.openConversation(conv.id)
      scrollToBottom()
      // 清掉 query（避免重复触发）
      router.replace({ query: {} })
      return
    } catch (e) {
      ElMessage.error(e?.message || '创建会话失败')
    }
  }

  // 处理 ?conv= 直接打开会话（来自其他页面深链）
  if (convIdFromQuery.value) {
    await store.openConversation(convIdFromQuery.value)
    scrollToBottom()
  }
})

onUnmounted(() => {
  // 离开页面：清掉当前会话上下文（不关 SSE）
  store.closeConversation()
})

// 当 store.currentMessages 变化时滚到底部
watch(() => store.currentMessages.length, () => {
  nextTick(scrollToBottom)
})

async function onSend() {
  const text = input.value.trim()
  if (!text && attachments.value.length === 0) return
  if (store.sending) return
  try {
    await store.sendMessage({ content: text, attachments: attachments.value })
    input.value = ''
    attachments.value = []
    nextTick(scrollToBottom)
  } catch (e) {
    ElMessage.error(e?.message || '发送失败')
  }
}

async function onFileSelect(event) {
  const file = event.target.files?.[0]
  if (!file) return
  try {
    const ref = await store.uploadAttachment(file)
    attachments.value.push(ref)
  } catch (e) {
    ElMessage.error(e?.message || '上传失败')
  } finally {
    event.target.value = ''
  }
}

function removeAttachment(idx) {
  attachments.value.splice(idx, 1)
}

function onSelectConversation(conv) {
  store.openConversation(conv.id)
}

function fmtTime(dt) {
  if (!dt) return ''
  const d = new Date(dt)
  return d.toTimeString().slice(0, 5)
}

function scrollToBottom() {
  const el = messagesRef.value
  if (el) el.scrollTop = el.scrollHeight
}

const myRole = computed(() => auth.role)

function isMine(msg) {
  if (msg.senderRole === 'SYSTEM') return 'system'
  if (myRole.value === 'HR' && msg.senderRole === 'HR') return 'mine'
  if (myRole.value === 'CANDIDATE' && msg.senderRole === 'CANDIDATE') return 'mine'
  return 'peer'
}

function fmtSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}
</script>

<template>
  <div class="page-shell chat-page">
    <div class="chat-layout">
      <!-- 左：会话列表 -->
      <aside class="chat-sidebar">
        <div class="sidebar-header">
          <h2>消息</h2>
          <span v-if="store.totalUnread > 0" class="header-badge">{{ store.totalUnread }}</span>
        </div>
        <div v-if="store.loadingList && store.conversations.length === 0" class="sidebar-loading">加载中…</div>
        <div v-else-if="store.conversations.length === 0" class="sidebar-empty">
          <p>暂无会话</p>
          <p class="hint">投递后可在投递详情页"联系 HR / 候选人"开始对话</p>
        </div>
        <div v-else class="sidebar-list">
          <ChatListItem
            v-for="c in store.conversations"
            :key="c.id"
            :conversation="c"
            :active="store.currentConversationId === c.id"
            @click="onSelectConversation(c)"
          />
        </div>
      </aside>

      <!-- 右：消息流 -->
      <section class="chat-main">
        <div v-if="!store.hasCurrent" class="main-empty">
          <div class="empty-icon">💬</div>
          <p>选择左侧的会话开始聊天</p>
        </div>

        <template v-else>
          <div class="main-header">
            <div class="peer-info">
              <span class="peer-name">{{ store.currentPeer?.name || '未知用户' }}</span>
              <span class="peer-role">{{ store.currentPeer?.role === 'HR' ? 'HR' : '求职者' }}</span>
            </div>
            <div class="conn-status">
              <span class="dot" :class="store.sseConnected ? 'on' : 'off'"></span>
              <span class="conn-text">{{ store.sseConnected ? '实时连接' : '连接中…' }}</span>
            </div>
          </div>

          <div ref="messagesRef" class="messages">
            <div
              v-for="m in store.currentMessages"
              :key="m.id"
              class="bubble-row"
              :class="`bubble-row--${isMine(m)}`"
            >
              <!-- SYSTEM 消息居中 -->
              <template v-if="m.senderRole === 'SYSTEM'">
                <div class="bubble-system">
                  <span class="sys-icon">ℹ</span>
                  <span>{{ m.content }}</span>
                </div>
              </template>

              <template v-else>
                <div class="bubble" :class="`bubble--${isMine(m)}`">
                  <div v-if="m.jobTitle" class="job-tag">关于「{{ m.jobTitle }}」</div>
                  <div v-if="m.content" class="text">{{ m.content }}</div>
                  <div v-if="m.attachments?.length" class="attachments">
                    <a
                      v-for="a in m.attachments"
                      :key="a.id"
                      :href="a.downloadUrl"
                      target="_blank"
                      class="attachment"
                    >
                      <span class="att-icon">📎</span>
                      <span class="att-name">{{ a.fileName }}</span>
                      <span class="att-size">{{ fmtSize(a.fileSize) }}</span>
                    </a>
                  </div>
                  <div class="meta">
                    <span class="time">{{ fmtTime(m.createdAt) }}</span>
                    <span v-if="isMine(m) === 'mine' && m.readFlag === 1" class="read-flag">已读</span>
                  </div>
                </div>
              </template>
            </div>
            <div v-if="store.loadingDetail" class="loading-row">加载中…</div>
          </div>

          <!-- 附件预览 -->
          <div v-if="attachments.length > 0" class="attach-preview">
            <div v-for="(a, i) in attachments" :key="i" class="attach-chip">
              <span>📎 {{ a.fileName }}</span>
              <button class="chip-x" type="button" @click="removeAttachment(i)">×</button>
            </div>
          </div>

          <div class="composer">
            <label class="file-btn" title="添加附件">
              📎
              <input type="file" hidden @change="onFileSelect" />
            </label>
            <textarea
              v-model="input"
              class="textarea"
              rows="2"
              placeholder="输入消息内容（Ctrl+Enter 发送）"
              @keydown.ctrl.enter.prevent="onSend"
            ></textarea>
            <button class="send-btn" type="button" :disabled="store.sending" @click="onSend">
              {{ store.sending ? '发送中…' : '发送' }}
            </button>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  padding: 24px;
}
.chat-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  height: calc(100vh - 160px);
  min-height: 540px;
  background: var(--panel, #fff);
  border-radius: 16px;
  border: 1px solid var(--line, #e4e7ed);
  overflow: hidden;
}
.chat-sidebar {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--line, #e4e7ed);
  background: var(--panel-soft, #fafbfc);
}
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid var(--line, #e4e7ed);
}
.sidebar-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text, #303133);
}
.header-badge {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--danger, #f56c6c);
  color: #fff;
  font-size: 11px;
  line-height: 20px;
  text-align: center;
  font-weight: 500;
}
.sidebar-loading, .sidebar-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted, #909399);
  font-size: 13px;
  padding: 24px;
}
.sidebar-empty {
  flex-direction: column;
  gap: 8px;
}
.sidebar-empty .hint {
  font-size: 12px;
  text-align: center;
}
.sidebar-list {
  flex: 1;
  overflow-y: auto;
}
.chat-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.main-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted, #909399);
  gap: 12px;
}
.empty-icon {
  font-size: 48px;
  opacity: 0.4;
}
.main-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--line, #e4e7ed);
  background: var(--panel, #fff);
}
.peer-info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.peer-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text, #303133);
}
.peer-role {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--primary-tint, rgba(64, 158, 255, 0.1));
  color: var(--primary, #409eff);
}
.conn-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted, #909399);
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.dot.on { background: #67c23a; }
.dot.off { background: #e6a23c; }
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  background: var(--chat-bg, #f5f7fa);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.bubble-row {
  display: flex;
}
.bubble-row--mine { justify-content: flex-end; }
.bubble-row--peer { justify-content: flex-start; }
.bubble-row--system { justify-content: center; }
.bubble {
  max-width: 60%;
  padding: 10px 14px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid var(--line, #e4e7ed);
  position: relative;
  word-break: break-word;
}
.bubble--mine {
  background: var(--primary, #409eff);
  color: #fff;
  border-color: var(--primary, #409eff);
}
.bubble--mine .meta {
  color: rgba(255, 255, 255, 0.7);
}
.bubble--peer {
  background: #fff;
}
.job-tag {
  font-size: 11px;
  color: var(--text-muted, #909399);
  background: rgba(0, 0, 0, 0.04);
  padding: 2px 6px;
  border-radius: 4px;
  display: inline-block;
  margin-bottom: 6px;
}
.bubble--mine .job-tag {
  background: rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.85);
}
.text {
  white-space: pre-wrap;
  font-size: 14px;
  line-height: 1.5;
}
.attachments {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.attachment {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.05);
  color: inherit;
  text-decoration: none;
  font-size: 12px;
}
.bubble--mine .attachment {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}
.att-icon { font-size: 14px; }
.att-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.att-size { font-size: 11px; opacity: 0.7; }
.meta {
  margin-top: 6px;
  font-size: 11px;
  color: var(--text-muted, #909399);
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}
.read-flag {
  color: #67c23a;
}
.bubble--mine .read-flag {
  color: rgba(255, 255, 255, 0.85);
}
.bubble-system {
  background: rgba(0, 0, 0, 0.04);
  color: var(--text-soft, #606266);
  padding: 6px 14px;
  border-radius: 12px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-style: italic;
}
.sys-icon {
  opacity: 0.7;
}
.loading-row {
  text-align: center;
  color: var(--text-muted, #909399);
  font-size: 12px;
  padding: 8px;
}
.attach-preview {
  padding: 8px 20px;
  border-top: 1px solid var(--line, #e4e7ed);
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  background: var(--panel-soft, #fafbfc);
}
.attach-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid var(--line, #e4e7ed);
  border-radius: 14px;
  background: #fff;
  font-size: 12px;
}
.chip-x {
  border: none;
  background: transparent;
  color: var(--text-muted, #909399);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  padding: 0 2px;
}
.composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 20px;
  border-top: 1px solid var(--line, #e4e7ed);
  background: #fff;
}
.file-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  cursor: pointer;
  border-radius: 8px;
  border: 1px solid var(--line, #e4e7ed);
  background: var(--panel-soft, #fafbfc);
  flex-shrink: 0;
  transition: background 0.18s ease;
}
.file-btn:hover {
  background: var(--primary-tint, rgba(64, 158, 255, 0.08));
}
.textarea {
  flex: 1;
  border: 1px solid var(--line, #e4e7ed);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  resize: none;
  font-family: inherit;
  outline: none;
  transition: border-color 0.18s ease;
}
.textarea:focus {
  border-color: var(--primary, #409eff);
}
.send-btn {
  height: 36px;
  padding: 0 18px;
  border: none;
  border-radius: 8px;
  background: var(--primary, #409eff);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.18s ease;
  flex-shrink: 0;
}
.send-btn:hover:not(:disabled) {
  background: var(--primary-deep, #337ecc);
}
.send-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 720px) {
  .chat-page { padding: 8px; }
  .chat-layout {
    grid-template-columns: 1fr;
    height: calc(100vh - 120px);
  }
  .chat-sidebar {
    display: none;
  }
  .bubble { max-width: 80%; }
}
</style>
