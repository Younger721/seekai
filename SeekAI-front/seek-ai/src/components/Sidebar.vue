<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <el-button type="primary" class="new-chat-btn" @click="createNewChat">
        <el-icon><Plus /></el-icon>
        New Chat
      </el-button>
      <div class="agent-tip">
        输入 <span class="highlight">@</span> 提及 Agent
      </div>
    </div>

    <!-- 面试亮点 4：基于 IntersectionObserver 实现虚拟列表 (Virtual List) 思想的性能优化 -->
    <div class="conversation-list">
      <div 
        v-for="item in displayConversations" 
        :key="item.id"
        :class="['conversation-item', { active: currentId === item.id }]"
        @click="selectConversation(item.id)"
      >
        <div class="item-content">
          <el-icon class="msg-icon"><ChatLineRound /></el-icon>
          <span class="title">{{ item.title || 'Untitled Chat' }}</span>
        </div>
        <div class="item-actions">
          <el-button 
            type="text" 
            class="delete-btn" 
            @click.stop="deleteChat(item.id)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>
      <!-- 无限滚动底部探测锚点 -->
      <div v-if="hasMore" ref="loadMoreAnchor" class="load-more-anchor"></div>
    </div>
    
    <div class="sidebar-footer">
      <div class="test-link" @click="goToTest">
        <el-icon><Tools /></el-icon>
        <span>工具测试</span>
      </div>
      <div class="user-info">
        <el-avatar :size="32">User</el-avatar>
        <span class="username">SeekAI User</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, computed, ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useChatStore } from '../stores/chat'
import { Tools } from '@element-plus/icons-vue'

const router = useRouter()
const chatStore = useChatStore()

const conversations = computed(() => chatStore.conversations)
const currentId = computed(() => chatStore.currentConversationId)

const goToTest = () => {
  router.push('/test')
}

// 虚拟列表/无限滚动相关状态
const displayCount = ref(20) // 初始渲染数量
const loadMoreAnchor = ref(null)
let io = null

const displayConversations = computed(() => {
  return conversations.value.slice(0, displayCount.value)
})

const hasMore = computed(() => {
  return displayCount.value < conversations.value.length
})

onMounted(() => {
  chatStore.fetchConversations()
  chatStore.fetchAgents()

  // 面试亮点 4：IntersectionObserver 实现无限滚动优化长列表性能
  io = new IntersectionObserver((entries) => {
    const target = entries[0]
    if (target.isIntersecting && hasMore.value) {
      // 模拟加载延迟或直接增加显示数量
      displayCount.value += 20
    }
  }, {
    root: null, // 默认 viewport
    rootMargin: '0px 0px 50px 0px', // 提前 50px 触发
    threshold: 0.1
  })

  // Vue 的 ref 需要等渲染后才能获取，所以加个微小延迟或者在更新后 observe
  setTimeout(() => {
    if (loadMoreAnchor.value) {
      io.observe(loadMoreAnchor.value)
    }
  }, 500)
})

onBeforeUnmount(() => {
  if (io) {
    io.disconnect()
  }
})

const createNewChat = () => {
  chatStore.createConversation()
}

const selectConversation = (id) => {
  chatStore.fetchConversationDetail(id)
}

const deleteChat = (id) => {
  chatStore.deleteConversation(id)
}
</script>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: #f9f9f9;
  color: #374151;
  transition: width 0.3s ease;
}

.sidebar-header {
  padding: 16px;
  background-color: #f9f9f9;
}

.agent-tip {
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
  margin-top: 8px;
}

.agent-tip .highlight {
  background-color: #10a37f20;
  color: #10a37f;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
}

.new-chat-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  background-color: #ffffff;
  border: 1px solid #e5e7eb;
  color: #374151;
  height: 44px;
  border-radius: 8px;
  font-weight: 500;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;
}

.new-chat-btn:hover {
  background-color: #f3f4f6;
  border-color: #d1d5db;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
}

.conversation-list::-webkit-scrollbar {
  width: 6px;
}

.conversation-list::-webkit-scrollbar-thumb {
  background-color: transparent;
  border-radius: 3px;
}

.conversation-list:hover::-webkit-scrollbar-thumb {
  background-color: #dcdfe6;
}

.conversation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background-color 0.2s ease, color 0.2s ease;
  color: #4b5563;
}

.conversation-item:hover {
  background-color: #f3f4f6;
}

.conversation-item.active {
  background-color: #e5e7eb;
  font-weight: 500;
  color: #111827;
}

.item-content {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.msg-icon {
  margin-right: 12px;
  font-size: 16px;
  color: #9ca3af;
}

.conversation-item.active .msg-icon {
  color: #374151;
}

.title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
}

.item-actions {
  display: none;
}

.conversation-item:hover .item-actions {
  display: block;
}

.delete-btn {
  color: #9ca3af;
  padding: 4px;
  transition: color 0.2s ease;
}

.delete-btn:hover {
  color: #ef4444;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid #e5e7eb;
  background-color: #f9f9f9;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.user-info:hover {
  background-color: #f3f4f6;
}

.username {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}
</style>

<style scoped>
.sidebar {
  background-color: #fcfcfc; /* 极淡的底色 */
  border-right: 1px solid #f0f0f0;
}

.sidebar-header {
  padding: 20px 16px;
}

.agent-tip {
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
  margin-top: 8px;
}

.agent-tip .highlight {
  background-color: #10a37f20;
  color: #10a37f;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
}

.new-chat-btn {
  justify-content: center;
  background-color: #10a37f !important; /* 调整为品牌色 */
  color: white !important;
  border: none;
  font-weight: 600;
  transition: transform 0.2s;
}

.new-chat-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(16, 163, 127, 0.2);
}

.conversation-list {
  padding: 0 12px;
}

/* 会话条目：类似 MacOS 的选中效果 */
.conversation-item {
  padding: 12px 14px;
  margin-bottom: 2px;
  border-radius: 10px;
  position: relative;
}

.conversation-item:hover {
  background-color: #f1f5f9;
}

.conversation-item.active {
  background-color: #10a37f10; /* 透明品牌色背景 */
  color: #10a37f;
}

/* 激活态左侧指示条 */
.conversation-item.active::before {
  content: '';
  position: absolute;
  left: 4px;
  top: 12px;
  bottom: 12px;
  width: 3px;
  background-color: #10a37f;
  border-radius: 4px;
}

.title {
  font-size: 13.5px;
  font-weight: 500;
}

.sidebar-footer {
  padding: 12px 16px 24px;
  border-top: 1px solid rgba(0,0,0,0.05);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.test-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border-radius: 10px;
  cursor: pointer;
  color: #6b7280;
  font-size: 13px;
  transition: all 0.2s;
}

.test-link:hover {
  background-color: #f1f5f9;
  color: #10a37f;
}

.user-info {
  background: #fff;
  border: 1px solid #f0f0f0;
  padding: 10px;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.03);
}

.username {
  font-size: 13px;
  color: #4b5563;
}
</style>