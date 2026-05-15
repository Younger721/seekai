<template>
  <div class="main-layout">
    <!-- Apple 风格侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <div class="logo" @click="toggleSidebar">
          <div class="logo-icon">
            <svg viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/>
              <path d="M8 12h8M12 8v8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
          <span class="logo-text">SeekAI</span>
        </div>
      </div>

      <!-- 导航菜单 -->
      <nav class="sidebar-nav">
        <router-link to="/" class="nav-item" :class="{ active: $route.path === '/' }">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <span>对话</span>
        </router-link>

        <router-link to="/skills" class="nav-item" :class="{ active: $route.path === '/skills' }">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
          </svg>
          <span>技能</span>
        </router-link>

        <router-link to="/memory" class="nav-item" :class="{ active: $route.path === '/memory' }">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2a10 10 0 0 1 10 10c0 5.52-4.48 10-10 10S2 17.52 2 12"/>
            <path d="M12 6v6l4 2"/>
          </svg>
          <span>记忆</span>
        </router-link>

        <router-link to="/browser" class="nav-item" :class="{ active: $route.path === '/browser' }">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="2" y1="12" x2="22" y2="12"/>
            <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
          </svg>
          <span>浏览器</span>
        </router-link>

        <router-link to="/tools" class="nav-item" :class="{ active: $route.path === '/tools' }">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
          </svg>
          <span>工具</span>
        </router-link>

        <router-link to="/security" class="nav-item" :class="{ active: $route.path === '/security' }">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          </svg>
          <span>安全</span>
        </router-link>
      </nav>

      <!-- 对话列表 -->
      <div class="conversation-section">
        <div class="conversation-header">
          <button class="new-chat-btn" @click="createNewChat">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 5v14M5 12h14"/>
            </svg>
            新建对话
          </button>
        </div>
        <div class="conversation-list">
          <div
            v-for="item in conversations"
            :key="item.id"
            :class="['conversation-item', { active: currentConversationId === item.id }]"
            @click="selectConversation(item.id)"
          >
            <div class="item-content">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
              <span class="title">{{ item.title || '新对话' }}</span>
            </div>
            <button class="delete-btn" @click="deleteChat(item.id, $event)">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 6L6 18M6 6l12 12"/>
              </svg>
            </button>
          </div>
          <div v-if="conversations.length === 0" class="empty-conversations">
            暂无对话，点击上方按钮新建
          </div>
        </div>
      </div>

      <!-- 底部用户区 -->
      <div class="sidebar-footer">
        <div class="user-info">
          <div class="user-avatar">
            {{ userName.charAt(0).toUpperCase() }}
          </div>
          <div class="user-details">
            <span class="user-name">{{ userName }}</span>
            <span class="user-status">
              <span class="status-dot"></span>
              在线
            </span>
          </div>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main-content">
      <ChatWindow />
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useChatStore } from '../stores/chat'
import ChatWindow from '../components/ChatWindow.vue'

const router = useRouter()
const chatStore = useChatStore()

const sidebarCollapsed = ref(false)
const userName = ref('用户')

const conversations = computed(() => chatStore.conversations)
const currentConversationId = computed(() => chatStore.currentConversationId)

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

// 新建对话
const createNewChat = () => {
  chatStore.createConversation()
}

// 选择对话
const selectConversation = (id) => {
  chatStore.fetchConversationDetail(id)
}

// 删除对话
const deleteChat = (id, event) => {
  event.stopPropagation()
  chatStore.deleteConversation(id)
}

onMounted(() => {
  chatStore.fetchConversations()
  chatStore.fetchAgents()
})
</script>

<style scoped>
.main-layout {
  display: flex;
  height: 100vh;
  width: 100vw;
  position: relative;
}

/* 玻璃拟态侧边栏 */
.sidebar {
  width: var(--sidebar-width);
  height: 100%;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-right: 1px solid rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
  position: relative;
  z-index: 10;
}

.sidebar::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(180deg, rgba(255,255,255,0.5) 0%, transparent 100%);
  pointer-events: none;
}

.sidebar.collapsed {
  width: 72px;
}

.sidebar-header {
  padding: var(--space-lg);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  position: relative;
}

.logo {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  cursor: pointer;
}

.logo-icon {
  width: 28px;
  height: 28px;
  color: var(--color-accent);
}

.logo-icon svg {
  width: 100%;
  height: 100%;
}

.logo-text {
  font-size: var(--font-size-lg);
  font-weight: 600;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-purple));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.sidebar.collapsed .logo-text {
  display: none;
}

/* 导航 */
.sidebar-nav {
  flex: 1;
  padding: var(--space-md);
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-lg);
  color: rgba(0, 0, 0, 0.6);
  text-decoration: none;
  font-size: var(--font-size-base);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.nav-item::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(0, 0, 0, 0.03), transparent);
  transform: translateX(-100%);
  transition: transform 0.5s ease;
}

.nav-item:hover {
  background: rgba(0, 0, 0, 0.05);
  color: rgba(0, 0, 0, 0.9);
}

.nav-item:hover::before {
  transform: translateX(100%);
}

.nav-item.active {
  background: linear-gradient(135deg, rgba(10, 132, 255, 0.1), rgba(139, 92, 246, 0.1));
  color: #1d1d1f;
  box-shadow: 0 4px 20px rgba(10, 132, 255, 0.1);
}

.nav-item.active::after {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: linear-gradient(180deg, var(--color-accent), var(--color-accent-purple));
  border-radius: 0 4px 4px 0;
}

.nav-item svg {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  transition: transform 0.3s ease;
}

.nav-item:hover svg {
  transform: scale(1.1);
}

.sidebar.collapsed .nav-item span {
  display: none;
}

.sidebar.collapsed .nav-item {
  justify-content: center;
  padding: var(--space-sm);
}

/* 用户区 */
.sidebar-footer {
  padding: var(--space-lg);
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  position: relative;
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-purple));
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: var(--font-size-sm);
  color: white;
  flex-shrink: 0;
  box-shadow: 0 4px 15px rgba(10, 132, 255, 0.3);
  transition: all 0.3s ease;
}

.user-avatar:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 20px rgba(10, 132, 255, 0.4);
}

.user-details {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: var(--font-size-sm);
  font-weight: 500;
}

.user-status {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-accent-green);
}

.sidebar.collapsed .user-details {
  display: none;
}

/* 主内容区 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  position: relative;
}

/* 响应式 */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    z-index: 100;
    transform: translateX(-100%);
  }

  .sidebar:not(.collapsed) {
    transform: translateX(0);
  }
}
/* 对话列表区域 */
.conversation-section {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  margin-top: var(--space-md);
}

.conversation-header {
  padding: var(--space-md);
}

.new-chat-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  background: linear-gradient(135deg, var(--color-accent), #059669);
  border: none;
  border-radius: var(--radius-lg);
  color: white;
  font-size: var(--font-size-sm);
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 15px rgba(16, 163, 127, 0.3);
}

.new-chat-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(16, 163, 127, 0.4);
}

.new-chat-btn:active {
  transform: scale(0.98);
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 var(--space-sm);
}

.conversation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-md);
  cursor: pointer;
  margin-bottom: 4px;
  transition: all 0.2s ease;
  color: rgba(0, 0, 0, 0.6);
}

.conversation-item:hover {
  background: rgba(0, 0, 0, 0.05);
}

.conversation-item.active {
  background: linear-gradient(135deg, rgba(16, 163, 127, 0.08), rgba(16, 163, 127, 0.04));
  color: #1d1d1f;
}

.conversation-item.active::before {
  content: "";
  position: absolute;
  left: 4px;
  width: 3px;
  height: 16px;
  background: var(--color-accent);
  border-radius: 0 4px 4px 0;
}

.item-content {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex: 1;
  min-width: 0;
  position: relative;
}

.item-content svg {
  flex-shrink: 0;
  opacity: 0.7;
}

.conversation-item.active .item-content svg {
  opacity: 1;
  color: var(--color-accent);
}

.title {
  font-size: var(--font-size-sm);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.delete-btn {
  opacity: 0;
  background: transparent;
  border: none;
  color: rgba(0, 0, 0, 0.4);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.conversation-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
}

.empty-conversations {
  text-align: center;
  padding: var(--space-xl);
  color: rgba(0, 0, 0, 0.4);
  font-size: var(--font-size-sm);
}
</style>