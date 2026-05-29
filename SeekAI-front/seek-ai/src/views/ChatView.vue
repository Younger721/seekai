<template>
  <div class="neo-page">
    <div class="ambient ambient-one"></div>
    <div class="ambient ambient-two"></div>

    <aside class="sidebar" :class="{ 'mobile-open': mobileSidebarOpen }">
      <div class="brand-block">
        <div class="brand-mark">S</div>
        <div>
          <h2>SeekAI</h2>
          <p>多智能体协同平台</p>
        </div>
      </div>

      <button class="new-chat-btn" @click="createNewChat">
        <span class="plus">+</span>
        新建会话
      </button>

      <p class="agent-tip">输入 <span>@</span> 可快速唤起指定 Agent</p>

      <nav class="nav-list">
        <router-link
          to="/"
          class="nav-item"
          :class="{ active: route.path === '/' }"
          @click="closeMobileSidebar"
        >
          <span class="dot"></span>
          对话
        </router-link>
      </nav>

      <section class="history-panel">
        <div class="history-header">
          <span>最近会话</span>
          <em>{{ conversations.length }}</em>
        </div>

        <div class="conversation-list">
          <div
            v-for="item in conversations"
            :key="item.id"
            :class="['conversation-item', { active: currentConversationId === item.id }]"
            @click="selectConversation(item.id)"
          >
            <span class="conversation-title">{{ item.title || '新对话' }}</span>
            <button class="delete-btn" @click="deleteChat(item.id, $event)">删除</button>
          </div>
          <div v-if="conversations.length === 0" class="empty-conversations">
            还没有会话，点击上方按钮创建
          </div>
        </div>
      </section>

      <div class="user-card">
        <div class="avatar">{{ userName.charAt(0).toUpperCase() }}</div>
        <div>
          <div class="user-name">{{ userName }}</div>
          <div class="user-status">在线</div>
        </div>
      </div>
    </aside>

    <div class="workspace">
      <section class="chat-shell">
        <ChatWindow />
      </section>
    </div>

    <div v-if="mobileSidebarOpen" class="mobile-mask" @click="closeMobileSidebar"></div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useChatStore } from '../stores/chat'
import ChatWindow from '../components/ChatWindow.vue'

const route = useRoute()
const chatStore = useChatStore()

const mobileSidebarOpen = ref(false)
const userName = ref('用户')

const conversations = computed(() => chatStore.conversations)
const currentConversationId = computed(() => chatStore.currentConversationId)

const closeMobileSidebar = () => {
  mobileSidebarOpen.value = false
}

const createNewChat = () => {
  chatStore.createConversation()
  closeMobileSidebar()
}

const selectConversation = (id) => {
  chatStore.fetchConversationDetail(id)
  closeMobileSidebar()
}

const deleteChat = (id, event) => {
  event.stopPropagation()
  chatStore.deleteConversation(id)
}

onMounted(() => {
  chatStore.initConversationContext()
  chatStore.fetchAgents()
})
</script>

<style scoped>
.neo-page {
  --brand-blue: #111111;
  --brand-cyan: #3f3f46;
  --brand-ink: #1c1917;
  --brand-text: #44403c;
  --brand-border: #dedad3;
  --brand-soft: #ece8e2;
  --brand-white: #ffffff;

  position: relative;
  display: grid;
  grid-template-columns: 308px 1fr;
  height: 100vh;
  width: 100%;
  overflow: hidden;
  font-family: 'HarmonyOS Sans SC', 'MiSans', 'Source Han Sans SC', 'PingFang SC', sans-serif;
  background: #ffffff;
  color: var(--brand-ink);
}

.ambient {
  display: none;
}

.ambient-one {
  width: 320px;
  height: 320px;
  background: radial-gradient(circle, #9ec5ff 0%, rgba(158, 197, 255, 0) 72%);
  top: -130px;
  right: 8%;
}

.ambient-two {
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, #b0f0ff 0%, rgba(176, 240, 255, 0) 72%);
  bottom: -220px;
  left: 22%;
}

.sidebar {
  position: relative;
  z-index: 3;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px 16px;
  background: #f2f0ec;
  border-right: 1px solid #e3dfd8;
  backdrop-filter: none;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
}

.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 18px;
  color: #fff;
  background: linear-gradient(145deg, #30b05f, #22a153);
  box-shadow: none;
}

.brand-block h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}

.brand-block p {
  margin: 2px 0 0;
  font-size: 12px;
  color: #78716c;
}

.new-chat-btn {
  height: 44px;
  border: none;
  border-radius: 12px;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  background: #171717;
  box-shadow: none;
  transition: background-color 0.2s ease;
}

.new-chat-btn:hover {
  transform: none;
  background: #262626;
}

.plus {
  margin-right: 6px;
  font-size: 16px;
}

.agent-tip {
  margin: 0;
  font-size: 12px;
  color: #78716c;
  text-align: center;
}

.agent-tip span {
  color: #171717;
  font-weight: 700;
}

.nav-list {
  display: grid;
  gap: 6px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 38px;
  padding: 0 12px;
  border-radius: 10px;
  color: #3f3f46;
  text-decoration: none;
  font-size: 14px;
  transition: all 0.2s ease;
}

.nav-item:hover {
  background: #ebe7e1;
  color: #1f2937;
}

.nav-item.active {
  background: #e6e2db;
  color: #1f2937;
  font-weight: 600;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #a8a29e;
}

.nav-item.active .dot {
  background: #16a34a;
}

.history-panel {
  flex: 1;
  min-height: 160px;
  display: flex;
  flex-direction: column;
  background: #f6f4f0;
  border: 1px solid #e2ded7;
  border-radius: 14px;
  overflow: hidden;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  font-size: 12px;
  color: #57534e;
  border-bottom: 1px solid #e8e3db;
}

.history-header em {
  font-style: normal;
  color: #166534;
  font-weight: 700;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 9px;
  margin-bottom: 6px;
  cursor: pointer;
  color: var(--brand-text);
  transition: all 0.18s ease;
}

.conversation-item:hover {
  background: #ece8e2;
}

.conversation-item.active {
  background: #e7e2da;
  color: #1f2937;
  box-shadow: inset 0 0 0 1px #dbd5cb;
}

.conversation-title {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delete-btn {
  opacity: 0;
  border: none;
  background: transparent;
  color: #78716c;
  font-size: 12px;
  cursor: pointer;
}

.conversation-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  color: #d83939;
}

.empty-conversations {
  padding: 18px 12px;
  text-align: center;
  color: #78716c;
  font-size: 12px;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
  background: #efebe5;
  border: 1px solid #e0dbd3;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(145deg, #22c55e, #16a34a);
}

.user-name {
  font-size: 13px;
  font-weight: 600;
}

.user-status {
  font-size: 11px;
  color: #78716c;
}

.workspace {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: 10px;
  gap: 0;
  background: #ffffff;
}


.chat-shell {
  flex: 1;
  min-height: 0;
  border-radius: 12px;
  border: 1px solid #e8e4dd;
  overflow: hidden;
  background: #ffffff;
  box-shadow: none;
}

.chat-shell :deep(.chat-window) {
  background: transparent;
}

.chat-shell :deep(.chat-header) {
  background: #faf8f5;
  border-bottom-color: #ebe7df;
}

.chat-shell :deep(.input-container) {
  border-color: #ddd8cf;
}

.chat-shell :deep(.send-btn) {
  background: #171717;
}

.mobile-mask {
  display: none;
}

@media (max-width: 1080px) {
  .neo-page {
    grid-template-columns: 1fr;
  }

  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    width: 300px;
    transform: translateX(-102%);
    transition: transform 0.22s ease;
  }

  .sidebar.mobile-open {
    transform: translateX(0);
  }

  .mobile-mask {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(15, 23, 42, 0.35);
    z-index: 2;
  }

  .workspace {
    padding: 10px;
  }

}
</style>
