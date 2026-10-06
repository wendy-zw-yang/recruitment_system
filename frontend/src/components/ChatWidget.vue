<script setup>
import { ref, computed, nextTick, watch, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useAiChatStore } from '@/stores/useAiChatStore'

const store = useAiChatStore()

/** 面板是否展开 */
const panelOpen = ref(false)
/** 输入框内容 */
const inputText = ref('')
/** 引用：消息列表 DOM（用于滚到底部） */
const messageListRef = ref(null)
/** 引用：输入框 */
const inputRef = ref(null)

async function togglePanel() {
  panelOpen.value = !panelOpen.value
  if (panelOpen.value) {
    await nextTick()
    await store.loadHistory(20)
    scrollToBottom()
    inputRef.value?.focus()
  }
}

async function send() {
  const q = inputText.value.trim()
  if (!q || store.isStreaming) return
  inputText.value = ''
  await store.sendMessage(q)
  scrollToBottom()
}

async function onAbort() {
  store.abortStream()
}

async function onClear() {
  try {
    await ElMessageBox.confirm('确定要清空当前会话的所有历史吗？此操作不可恢复。', '提示', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await store.clearHistory()
  ElMessageBox.alert('已清空', '提示', { type: 'success' }).catch(() => {})
}

async function onNewSession() {
  try {
    await ElMessageBox.confirm('新建会话将清空当前对话内容（保留历史）。是否继续？', '提示', {
      type: 'info',
      confirmButtonText: '新建',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  store.resetSession()
  scrollToBottom()
}

function scrollToBottom() {
  nextTick(() => {
    const el = messageListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

/** 监听历史变化，自动滚到底部 */
watch(
  () => store.chatHistory.map((m) => m.content).join('|'),
  () => scrollToBottom()
)

/** 简易 markdown → HTML（仅支持 **bold** / 换行 / 代码块） */
function renderMarkdown(text) {
  if (!text) return ''
  // 转义 HTML
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  // **bold**
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  // 换行
  html = html.replace(/\n/g, '<br>')
  return html
}

onMounted(() => {
  store.bootstrap()
})

// v0.6.2 阶段化状态文本
const STAGE_TEXT = {
  ready: '正在建立连接…',
  searching: '正在检索使用手册…',
  search_done: '已检索到匹配章节，正在准备问题…',
  llm_start: '客服正在思考…',
  streaming: '客服正在生成…'
}
const stageText = computed(() => STAGE_TEXT[store.stage] || '')
const stageIcon = computed(() => store.stage === 'search_done' && store.stageMeta === 0 ? '🤔' : '💭')
</script>

<template>
  <div class="chat-widget">
    <!-- 悬浮按钮 -->
    <button
      v-if="!panelOpen"
      class="chat-fab"
      type="button"
      aria-label="打开 AI 客服"
      @click="togglePanel"
    >
      <span class="fab-icon">💬</span>
      <span class="fab-label">AI 客服</span>
    </button>

    <!-- 面板 -->
    <div v-else class="chat-panel" role="dialog" aria-label="智能客服">
      <!-- 顶栏 -->
      <header class="chat-header">
        <div class="chat-header__title">
          <span class="avatar-mini">🤖</span>
          <div>
            <div class="title">智能客服 · 汇汇</div>
            <div class="subtitle">基于使用手册 &amp; 大模型</div>
          </div>
        </div>
        <div class="chat-header__actions">
          <button class="icon-btn" type="button" title="新建会话" @click="onNewSession">↻</button>
          <button class="icon-btn" type="button" title="清空历史" @click="onClear">🗑</button>
          <button class="icon-btn" type="button" title="关闭" @click="togglePanel">×</button>
        </div>
      </header>

      <!-- 消息列表 -->
      <div ref="messageListRef" class="chat-messages">
        <div v-if="store.historyLoading && store.isEmpty" class="status-row">加载中…</div>
        <div v-else-if="store.isEmpty" class="empty-row">
          <div class="empty-icon">💡</div>
          <p class="empty-title">你好，我是汇汇</p>
          <p class="empty-tip">问我关于「如何投递」「如何修改密码」「如何撤回」等问题吧</p>
          <div class="empty-suggest">
            <button class="suggest-btn" type="button" @click="inputText = '如何投递'; send()">如何投递</button>
            <button class="suggest-btn" type="button" @click="inputText = '如何修改密码'; send()">如何修改密码</button>
            <button class="suggest-btn" type="button" @click="inputText = '如何撤回投递'; send()">如何撤回</button>
          </div>
        </div>

        <template v-else>
          <div
            v-for="m in store.chatHistory"
            :key="m.id"
            class="msg"
            :class="['msg--' + m.role.toLowerCase(), { 'msg--error': m.error, 'msg--streaming': m.streaming }]"
          >
            <div class="msg-bubble">
              <span v-if="m.role === 'USER'" class="msg-text" v-text="m.content"></span>
              <span v-else class="msg-text" v-html="renderMarkdown(m.content)"></span>
              <span v-if="m.streaming && !m.content" class="typing-indicator">…</span>
              <span v-if="m.streaming && m.content" class="cursor-blink">▍</span>
            </div>
          </div>
        </template>
      </div>

      <!-- 输入区 -->
      <!-- v0.6.2：阶段状态指示器（解决"长时间无回应"）-->
      <div v-if="store.stage && store.stage !== 'idle' && store.stage !== 'done' && store.stage !== 'error'" class="stage-bar">
        <span class="stage-icon">{{ stageIcon }}</span>
        <span class="stage-text">{{ stageText }}</span>
      </div>

      <footer class="chat-footer">
        <textarea
          ref="inputRef"
          v-model="inputText"
          class="chat-input"
          rows="2"
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          :disabled="store.isStreaming"
          @keydown="onKeydown"
        ></textarea>
        <div class="chat-footer__actions">
          <button
            v-if="store.isStreaming"
            class="abort-btn"
            type="button"
            @click="onAbort"
          >
            停止
          </button>
          <button
            v-else
            class="send-btn"
            type="button"
            :disabled="!inputText.trim()"
            @click="send"
          >
            发送
          </button>
        </div>
      </footer>
    </div>
  </div>
</template>

/* v0.6.2 阶段状态指示器 */
.stage-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: linear-gradient(90deg, rgba(29, 111, 216, 0.06) 0%, rgba(6, 182, 212, 0.06) 100%);
  border-top: 1px solid var(--line);
  font-size: 12px;
  color: var(--text-soft);
  animation: stage-fade 0.3s ease;
}
.stage-icon {
  font-size: 14px;
  animation: spin-stage 1.2s linear infinite;
  display: inline-block;
}
.stage-text {
  flex: 1;
}
@keyframes stage-fade {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes spin-stage {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.15); }
}

<style scoped>
.chat-widget {
  position: fixed;
  right: 24px;
  bottom: 96px;
  z-index: 998;
  font-family: inherit;
}

/* 悬浮按钮 */
.chat-fab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #1d6fd8 0%, #06b6d4 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(29, 111, 216, 0.32);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.chat-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 30px rgba(29, 111, 216, 0.42);
}
.fab-icon {
  font-size: 18px;
}

/* 面板 */
.chat-panel {
  width: 380px;
  height: 540px;
  max-height: calc(100vh - 160px);
  background: var(--panel-strong);
  border: 1px solid var(--line);
  border-radius: 18px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 顶栏 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(135deg, rgba(29, 111, 216, 0.06) 0%, rgba(6, 182, 212, 0.06) 100%);
}
.chat-header__title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.avatar-mini {
  font-size: 22px;
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1d6fd8 0%, #06b6d4 100%);
  border-radius: 50%;
}
.chat-header__title .title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}
.chat-header__title .subtitle {
  font-size: 11px;
  color: var(--text-soft);
}
.chat-header__actions {
  display: flex;
  gap: 4px;
}
.icon-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: var(--text-soft);
  font-size: 14px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease;
}
.icon-btn:hover {
  background: var(--tab-hover);
  color: var(--primary);
}

/* 消息列表 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: var(--bg);
}
.status-row,
.empty-row {
  margin: auto;
  text-align: center;
  color: var(--text-soft);
  font-size: 13px;
}
.empty-row {
  padding: 24px 12px;
}
.empty-icon {
  font-size: 40px;
  margin-bottom: 8px;
}
.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
  margin: 0 0 4px;
}
.empty-tip {
  font-size: 12px;
  color: var(--text-soft);
  margin: 0 0 14px;
}
.empty-suggest {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
}
.suggest-btn {
  padding: 6px 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--panel-strong);
  color: var(--text);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.18s ease;
}
.suggest-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.msg {
  display: flex;
  width: 100%;
}
.msg--user {
  justify-content: flex-end;
}
.msg--assistant {
  justify-content: flex-start;
}
.msg-bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 13.5px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}
.msg--user .msg-bubble {
  background: linear-gradient(135deg, #1d6fd8 0%, #06b6d4 100%);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.msg--assistant .msg-bubble {
  background: var(--panel-strong);
  color: var(--text);
  border: 1px solid var(--line);
  border-bottom-left-radius: 4px;
}
.msg--error .msg-bubble {
  border-color: var(--danger);
  color: var(--danger);
}
.typing-indicator {
  display: inline-block;
  animation: blink 1s steps(2, start) infinite;
}
.cursor-blink {
  display: inline-block;
  margin-left: 2px;
  animation: blink 1s steps(2, start) infinite;
}
@keyframes blink {
  to {
    visibility: hidden;
  }
}

/* 输入区 */
.chat-footer {
  border-top: 1px solid var(--line);
  padding: 10px;
  background: var(--panel-strong);
}
.chat-input {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 8px 10px;
  font-family: inherit;
  font-size: 13px;
  resize: none;
  background: var(--bg);
  color: var(--text);
  outline: none;
  box-sizing: border-box;
}
.chat-input:focus {
  border-color: var(--primary);
}
.chat-input:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.chat-footer__actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.send-btn,
.abort-btn {
  padding: 6px 16px;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}
.send-btn {
  background: var(--primary);
  color: #fff;
}
.send-btn:disabled {
  background: var(--line);
  color: var(--text-muted);
  cursor: not-allowed;
}
.abort-btn {
  background: var(--panel-strong);
  color: var(--text);
  border: 1px solid var(--line);
}
.abort-btn:hover {
  border-color: var(--danger);
  color: var(--danger);
}

@media (max-width: 720px) {
  .chat-widget {
    right: 12px;
    bottom: 80px;
  }
  .chat-panel {
    width: calc(100vw - 24px);
    height: 70vh;
  }
}
</style>