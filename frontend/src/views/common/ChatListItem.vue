<script setup>
import { computed } from 'vue'

const props = defineProps({
  conversation: { type: Object, required: true },
  active: { type: Boolean, default: false }
})

const peerInitial = computed(() => {
  const name = props.conversation.peerName || '?'
  return name.slice(0, 1).toUpperCase()
})

const timeText = computed(() => {
  const t = props.conversation.lastMessageAt
  if (!t) return ''
  const date = new Date(t)
  const now = new Date()
  const sameDay = date.toDateString() === now.toDateString()
  if (sameDay) {
    return date.toTimeString().slice(0, 5)
  }
  const diff = (now - date) / 1000
  if (diff < 7 * 86400) {
    const weekdays = ['日', '一', '二', '三', '四', '五', '六']
    return `周${weekdays[date.getDay()]}`
  }
  return `${date.getMonth() + 1}/${date.getDate()}`
})

const unread = computed(() => props.conversation.unreadCount || 0)
const preview = computed(() => props.conversation.lastMessagePreview || '暂无消息')
</script>

<template>
  <div class="chat-list-item" :class="{ 'chat-list-item--active': active }">
    <div class="avatar">{{ peerInitial }}</div>
    <div class="body">
      <div class="row1">
        <span class="name">{{ conversation.peerName || '未知用户' }}</span>
        <span class="time">{{ timeText }}</span>
      </div>
      <div class="row2">
        <span class="preview">{{ preview }}</span>
        <span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-list-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--line, #eee);
  cursor: pointer;
  transition: background 0.18s ease;
}
.chat-list-item:hover {
  background: var(--tab-hover, rgba(0, 0, 0, 0.03));
}
.chat-list-item--active {
  background: var(--primary-tint, rgba(64, 158, 255, 0.08));
}
.avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--primary, #409eff), var(--accent, #67c23a));
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.body {
  flex: 1;
  min-width: 0;
}
.row1, .row2 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.row1 {
  margin-bottom: 4px;
}
.name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text, #303133);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.time {
  font-size: 12px;
  color: var(--text-muted, #909399);
  flex-shrink: 0;
}
.preview {
  font-size: 12px;
  color: var(--text-soft, #606266);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.badge {
  flex-shrink: 0;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--danger, #f56c6c);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  font-weight: 500;
}
</style>
