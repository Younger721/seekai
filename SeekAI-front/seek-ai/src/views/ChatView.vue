<template>
  <div class="neo-page">
    <div class="liquid-bg">
      <div class="liquid-blob blob-one"></div>
      <div class="liquid-blob blob-two"></div>
      <div class="liquid-blob blob-three"></div>
    </div>
    <div class="noise-layer"></div>

    <div class="app-shell">
      <aside class="sidebar" :class="{ 'mobile-open': mobileSidebarOpen }">
        <div class="brand-block">
          <div class="brand-mark">S</div>
          <div>
            <h2>SeekAI</h2>
            <p>多智能体协同平台</p>
          </div>
        </div>

        <button class="new-chat-btn" @click="createNewChat">
          <span>新建会话</span>
          <span class="plus">+</span>
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
              <span class="conversation-index">#</span>
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
        <button class="mobile-sidebar-toggle" @click="mobileSidebarOpen = true">
          <span></span>
          <span></span>
        </button>
        <section class="chat-shell">
          <ChatWindow />
        </section>
      </div>
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
  --luxe-bg: #f3f2ec;
  --luxe-panel: rgba(255, 254, 252, 0.48);
  --luxe-border: rgba(255, 255, 255, 0.86);
  --luxe-ink: #23221f;
  --luxe-text: #4a4844;
  --luxe-muted: #8e8a80;
  --luxe-gold: #c5a880;
  --luxe-line: rgba(35, 34, 31, 0.08);
  --luxe-shadow: 0 40px 90px rgba(142, 135, 121, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.9);

  position: relative;
  width: 100%;
  height: 100vh;
  padding: 24px;
  overflow: hidden;
  color: var(--luxe-ink);
  background: var(--luxe-bg);
  font-family: 'HarmonyOS Sans SC', 'MiSans', 'Source Han Sans SC', 'PingFang SC', sans-serif;
}

.liquid-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  background: radial-gradient(circle at 50% 50%, #fbfbfa 0%, #edeae0 100%);
}

.liquid-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(18px);
  mix-blend-mode: multiply;
  opacity: 0.64;
  animation: organicFloat 25s infinite alternate ease-in-out;
}

.blob-one {
  width: 700px;
  height: 700px;
  top: -14%;
  left: -8%;
  background: radial-gradient(circle, #ecdcb6 0%, rgba(236, 220, 182, 0) 70%);
  animation-duration: 26s;
}

.blob-two {
  width: 820px;
  height: 820px;
  right: -10%;
  bottom: -18%;
  background: radial-gradient(circle, #e5d4b9 0%, rgba(229, 212, 185, 0) 70%);
  animation-duration: 32s;
}

.blob-three {
  width: 600px;
  height: 600px;
  top: 27%;
  left: 36%;
  background: radial-gradient(circle, #e8d5bc 0%, rgba(232, 213, 188, 0) 70%);
  animation-duration: 20s;
}

.noise-layer {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  opacity: 0.03;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E");
}

@keyframes organicFloat {
  0% { transform: translate(0, 0) scale(1) rotate(0deg); }
  50% { transform: translate(80px, 60px) scale(1.08) rotate(120deg); }
  100% { transform: translate(-30px, 120px) scale(0.96) rotate(240deg); }
}

.app-shell {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 24px;
  width: 100%;
  height: 100%;
}

.sidebar {
  position: relative;
  z-index: 3;
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 0;
  padding: 34px 20px 24px;
  overflow: hidden;
  background: var(--luxe-panel);
  border: 1px solid var(--luxe-border);
  border-radius: 24px;
  box-shadow: var(--luxe-shadow);
  backdrop-filter: blur(40px) saturate(140%);
  -webkit-backdrop-filter: blur(40px) saturate(140%);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 10px;
}

.brand-mark,
.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5c4e3c;
  font-weight: 700;
  background: linear-gradient(135deg, #eadecc 0%, #d8c6b0 100%);
  border: 2px solid #ffffff;
  box-shadow: 0 8px 20px rgba(142, 135, 121, 0.16);
}

.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  font-size: 18px;
}

.brand-block h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  background: linear-gradient(90deg, var(--luxe-ink), var(--luxe-muted));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.brand-block p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--luxe-muted);
}

.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  padding: 0 14px 0 18px;
  color: var(--luxe-ink);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  background: #ffffff;
  border: 1px solid rgba(142, 135, 121, 0.18);
  border-radius: 16px;
  box-shadow: 0 8px 25px rgba(142, 135, 121, 0.06);
  transition: all 0.36s cubic-bezier(0.16, 1, 0.3, 1);
}

.new-chat-btn:hover {
  border-color: var(--luxe-ink);
  transform: translateY(-2px);
  box-shadow: 0 14px 30px rgba(142, 135, 121, 0.12);
}

.plus {
  display: grid;
  place-items: center;
  width: 25px;
  height: 25px;
  color: #ffffff;
  font-size: 18px;
  line-height: 1;
  background: var(--luxe-ink);
  border-radius: 50%;
  transition: all 0.36s ease;
}

.new-chat-btn:hover .plus {
  background: var(--luxe-gold);
  transform: rotate(90deg);
}

.agent-tip {
  margin: -6px 0 0;
  color: var(--luxe-muted);
  font-size: 12px;
  text-align: center;
}

.agent-tip span {
  color: var(--luxe-ink);
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
  height: 42px;
  padding: 0 14px;
  color: var(--luxe-text);
  font-size: 14px;
  text-decoration: none;
  border-radius: 14px;
  transition: all 0.28s ease;
}

.nav-item:hover {
  color: var(--luxe-ink);
  background: rgba(255, 255, 255, 0.62);
}

.nav-item.active {
  color: var(--luxe-ink);
  font-weight: 600;
  background: #ffffff;
  box-shadow: 0 8px 20px rgba(142, 135, 121, 0.06);
}

.dot {
  width: 7px;
  height: 7px;
  background: rgba(35, 34, 31, 0.2);
  border-radius: 50%;
}

.nav-item.active .dot {
  background: var(--luxe-gold);
}

.history-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 160px;
  overflow: hidden;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px 10px;
  color: var(--luxe-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  border-bottom: 1px solid var(--luxe-line);
}

.history-header em {
  color: var(--luxe-gold);
  font-style: normal;
  font-weight: 700;
}

.conversation-list {
  flex: 1;
  padding: 12px 4px 0;
  overflow-y: auto;
}

.conversation-list::-webkit-scrollbar {
  width: 0;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 10px;
  margin-bottom: 4px;
  color: var(--luxe-text);
  cursor: pointer;
  border-radius: 14px;
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.conversation-item:hover {
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 8px 22px rgba(142, 135, 121, 0.08);
}

.conversation-item.active {
  color: var(--luxe-ink);
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(142, 135, 121, 0.09), inset 0 1px 0 #fff;
}

.conversation-index {
  flex: 0 0 auto;
  color: var(--luxe-muted);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  opacity: 0.62;
}

.conversation-item.active .conversation-index {
  color: var(--luxe-gold);
  opacity: 1;
}

.conversation-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delete-btn {
  flex: 0 0 auto;
  color: var(--luxe-muted);
  font-size: 12px;
  cursor: pointer;
  background: transparent;
  border: none;
  opacity: 0;
  transition: all 0.2s ease;
}

.conversation-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  color: #d83939;
}

.empty-conversations {
  padding: 18px 12px;
  color: var(--luxe-muted);
  font-size: 12px;
  text-align: center;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 10px 0;
  border-top: 1px solid var(--luxe-line);
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
}

.user-status {
  color: var(--luxe-muted);
  font-size: 11px;
}

.workspace {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--luxe-panel);
  border: 1px solid var(--luxe-border);
  border-radius: 24px;
  box-shadow: var(--luxe-shadow);
  backdrop-filter: blur(40px) saturate(140%);
  -webkit-backdrop-filter: blur(40px) saturate(140%);
}

.chat-shell {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: transparent;
}

.chat-shell :deep(.chat-window) {
  background: transparent;
}

.mobile-sidebar-toggle,
.mobile-mask {
  display: none;
}

@media (max-width: 1080px) {
  .neo-page {
    padding: 14px;
  }

  .app-shell {
    grid-template-columns: 1fr;
  }

  .sidebar {
    position: fixed;
    top: 14px;
    bottom: 14px;
    left: 14px;
    z-index: 5;
    width: 300px;
    transform: translateX(-108%);
    transition: transform 0.22s ease;
  }

  .sidebar.mobile-open {
    transform: translateX(0);
  }

  .mobile-mask {
    position: fixed;
    inset: 0;
    z-index: 4;
    display: block;
    background: rgba(35, 34, 31, 0.22);
    backdrop-filter: blur(6px);
    -webkit-backdrop-filter: blur(6px);
  }

  .workspace {
    border-radius: 22px;
  }

  .mobile-sidebar-toggle {
    position: absolute;
    top: 14px;
    left: 14px;
    z-index: 40;
    display: grid;
    place-items: center;
    gap: 4px;
    width: 40px;
    height: 40px;
    cursor: pointer;
    background: rgba(255, 255, 255, 0.68);
    border: 1px solid rgba(142, 135, 121, 0.18);
    border-radius: 14px;
    box-shadow: 0 8px 20px rgba(142, 135, 121, 0.08);
  }

  .mobile-sidebar-toggle span {
    width: 16px;
    height: 2px;
    background: var(--luxe-ink);
    border-radius: 99px;
  }
}

@media (max-width: 640px) {
  .neo-page {
    padding: 8px;
  }

  .app-shell {
    gap: 0;
  }

  .sidebar {
    top: 8px;
    bottom: 8px;
    left: 8px;
    width: min(300px, calc(100vw - 16px));
    border-radius: 20px;
  }

  .workspace {
    border-radius: 18px;
  }
}
</style>
