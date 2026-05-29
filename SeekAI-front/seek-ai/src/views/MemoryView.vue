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

    <main class="workspace">
      <section class="memory-shell">
        <header class="memory-header">
          <div class="header-left">
            <button class="back-btn" @click="goBack">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M19 12H5M12 19l-7-7 7-7" />
              </svg>
              返回
            </button>
            <div>
              <h1>记忆系统</h1>
              <p>越用越懂你，自动沉淀偏好与习惯</p>
            </div>
          </div>
          <span class="state-tag">实时更新</span>
        </header>

        <section class="stats-grid">
          <article class="stat-card">
            <div class="stat-icon blue">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2a10 10 0 0 1 10 10c0 5.52-4.48 10-10 10S2 17.52 2 12" />
              </svg>
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ stats.memories }}</span>
              <span class="stat-label">记忆条目</span>
            </div>
          </article>
          <article class="stat-card">
            <div class="stat-icon green">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="3" />
                <path d="M12 1v6m0 6v6" />
                <path d="m4.22 4.22 4.24 4.24m7.08 7.08 4.24 4.24" />
              </svg>
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ stats.preferences }}</span>
              <span class="stat-label">用户偏好</span>
            </div>
          </article>
          <article class="stat-card">
            <div class="stat-icon orange">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
              </svg>
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ stats.habits }}</span>
              <span class="stat-label">使用习惯</span>
            </div>
          </article>
        </section>

        <section class="panel-grid">
          <article class="panel">
            <div class="panel-head">
              <h2>用户画像</h2>
              <span class="badge">AI 生成</span>
            </div>
            <div class="profile-wrap">
              <div class="profile-avatar">{{ userName.charAt(0).toUpperCase() }}</div>
              <div class="profile-detail">
                <h3>{{ userName }}</h3>
                <div v-if="preferences.length > 0" class="preferences-list">
                  <div
                    v-for="pref in preferences"
                    :key="`${pref.preferenceKey}-${pref.preferenceValue}`"
                    class="preference-item"
                  >
                    <span class="pref-key">{{ pref.preferenceKey }}</span>
                    <span class="pref-value">{{ pref.preferenceValue }}</span>
                    <span class="pref-confidence">{{ formatConfidence(pref.confidence) }}</span>
                  </div>
                </div>
                <p v-else class="empty-hint">暂无偏好数据</p>
              </div>
            </div>
          </article>

          <article class="panel">
            <div class="panel-head">
              <h2>记忆库</h2>
              <div class="tabs">
                <button
                  v-for="tab in tabs"
                  :key="tab.value"
                  :class="['tab', { active: activeTab === tab.value }]"
                  @click="activeTab = tab.value"
                >
                  {{ tab.label }}
                </button>
              </div>
            </div>

            <div class="memory-list">
              <article v-for="memory in filteredMemories" :key="memory.id || memory.content" class="memory-item">
                <span class="memory-type" :class="memoryTypeClass(memory.memoryType)">
                  {{ getTypeLabel(memory.memoryType) }}
                </span>
                <p class="memory-content">{{ memory.content || '无内容' }}</p>
                <div class="memory-meta">
                  <span>创建于 {{ formatDate(memory.createdAt) }}</span>
                  <span>访问 {{ memory.accessCount || 0 }} 次</span>
                </div>
              </article>
              <div v-if="!loading && filteredMemories.length === 0" class="empty-state">
                当前筛选下暂无记忆
              </div>
              <div v-if="loading" class="empty-state">记忆加载中...</div>
            </div>
          </article>
        </section>

        <article class="panel">
          <div class="panel-head">
            <h2>常用习惯</h2>
          </div>
          <div class="habits-grid">
            <div v-for="habit in habits" :key="habit.id || `${habit.habitType}-${habit.habitValue}`" class="habit-card">
              <div class="habit-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
                </svg>
              </div>
              <div class="habit-info">
                <span class="habit-type">{{ habit.habitType || 'habit' }}</span>
                <span class="habit-value">{{ habit.habitValue || '-' }}</span>
              </div>
              <div class="habit-frequency">{{ habit.frequency || 0 }} 次</div>
            </div>
            <div v-if="!loading && habits.length === 0" class="empty-state full-width">暂无习惯数据</div>
          </div>
        </article>
      </section>
    </main>

    <div v-if="mobileSidebarOpen" class="mobile-mask" @click="closeMobileSidebar"></div>
  </div>
</template>

<script setup>
import axios from 'axios'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore } from '../stores/chat'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()

const mobileSidebarOpen = ref(false)
const userId = ref(localStorage.getItem('seekai_user_id') || 'user1')
const userName = ref(localStorage.getItem('seekai_user_name') || '用户')
const loading = ref(false)

const preferences = ref([])
const habits = ref([])
const memories = ref([])

const tabs = [
  { value: 'ALL', label: '全部' },
  { value: 'PREFERENCE', label: '偏好' },
  { value: 'HABIT', label: '习惯' },
  { value: 'IMPORTANT', label: '重要' }
]
const activeTab = ref('ALL')

const conversations = computed(() => chatStore.conversations)
const currentConversationId = computed(() => chatStore.currentConversationId)
const stats = computed(() => ({
  memories: memories.value.length,
  preferences: preferences.value.length,
  habits: habits.value.length
}))

const filteredMemories = computed(() => {
  if (activeTab.value === 'ALL') return memories.value
  return memories.value.filter(item => item.memoryType === activeTab.value)
})

const closeMobileSidebar = () => {
  mobileSidebarOpen.value = false
}

const createNewChat = () => {
  chatStore.createConversation()
  closeMobileSidebar()
}

const selectConversation = (id) => {
  chatStore.fetchConversationDetail(id)
  router.push('/')
  closeMobileSidebar()
}

const deleteChat = (id, event) => {
  event.stopPropagation()
  chatStore.deleteConversation(id)
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/')
}

const unwrapArrayPayload = (response) => {
  const payload = response?.data
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.data)) return payload.data
  return []
}

const fetchData = async () => {
  loading.value = true
  try {
    const [prefRes, habitRes, memoryRes] = await Promise.all([
      axios.get('/api/memory/preferences', { params: { userId: userId.value }, withCredentials: true }),
      axios.get('/api/memory/habits', { params: { userId: userId.value }, withCredentials: true }),
      axios.get('/api/memory/memories', { params: { userId: userId.value }, withCredentials: true })
    ])

    preferences.value = unwrapArrayPayload(prefRes)
    habits.value = unwrapArrayPayload(habitRes)
    memories.value = unwrapArrayPayload(memoryRes)
  } catch (error) {
    console.warn('memory api unavailable, fallback to mock data', error)
    preferences.value = [
      { preferenceKey: 'response_length', preferenceValue: 'medium', confidence: 0.82 },
      { preferenceKey: 'language', preferenceValue: 'Chinese', confidence: 0.93 }
    ]
    habits.value = [
      { habitType: 'agent', habitValue: 'CODE_EXPERT', frequency: 15 },
      { habitType: 'agent', habitValue: 'SEARCH_AGENT', frequency: 8 }
    ]
    memories.value = [
      {
        id: 'm1',
        memoryType: 'PREFERENCE',
        content: '用户偏好中文回复，并且希望代码示例尽量可直接运行。',
        createdAt: new Date().toISOString(),
        accessCount: 5
      },
      {
        id: 'm2',
        memoryType: 'IMPORTANT',
        content: '用户希望页面风格统一，优先使用主页视觉方案。',
        createdAt: new Date().toISOString(),
        accessCount: 2
      }
    ]
  } finally {
    loading.value = false
  }
}

const typeLabels = {
  PERSONA: '人物',
  PREFERENCE: '偏好',
  HABIT: '习惯',
  FACTS: '事实',
  IMPORTANT: '重要'
}

const getTypeLabel = (type) => typeLabels[type] || type || '未知'

const memoryTypeClass = (type) => (type || '').toLowerCase()

const formatDate = (date) => {
  if (!date) return '未知时间'
  const parsed = new Date(date)
  if (Number.isNaN(parsed.getTime())) return String(date)
  return parsed.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatConfidence = (confidence) => {
  if (typeof confidence !== 'number') return '置信度 --'
  return `置信度 ${Math.round(confidence * 100)}%`
}

onMounted(() => {
  chatStore.initConversationContext()
  chatStore.fetchAgents()
  fetchData()
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
  min-width: 0;
  padding: 10px;
  background: #ffffff;
}

.memory-shell {
  height: 100%;
  overflow-y: auto;
  border: 1px solid #e8e4dd;
  border-radius: 12px;
  background: #ffffff;
  padding: 18px;
}

.memory-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.memory-header h1 {
  margin: 0;
  font-size: 22px;
  line-height: 1.1;
}

.memory-header p {
  margin: 4px 0 0;
  font-size: 13px;
  color: #78716c;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 12px;
  border: 1px solid #d8d4ce;
  border-radius: 10px;
  background: #f7f5f1;
  color: #3f3f46;
  font-size: 13px;
  cursor: pointer;
}

.back-btn:hover {
  background: #efebe5;
}

.back-btn svg {
  width: 16px;
  height: 16px;
}

.state-tag {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #ecfdf3;
  border: 1px solid #b7e8c7;
  color: #166534;
  font-size: 12px;
  font-weight: 600;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.stat-card {
  background: #faf8f5;
  border: 1px solid #e8e4dd;
  border-radius: 12px;
  padding: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.stat-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-icon svg {
  width: 20px;
  height: 20px;
  color: #ffffff;
}

.stat-icon.blue {
  background: #111111;
}

.stat-icon.green {
  background: #16a34a;
}

.stat-icon.orange {
  background: #ea580c;
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
}

.stat-label {
  font-size: 12px;
  color: #78716c;
}

.panel-grid {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(300px, 1.4fr);
  gap: 12px;
  margin-bottom: 12px;
}

.panel {
  background: #faf8f5;
  border: 1px solid #e8e4dd;
  border-radius: 12px;
  overflow: hidden;
}

.panel-head {
  padding: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebe7df;
  gap: 10px;
}

.panel-head h2 {
  margin: 0;
  font-size: 16px;
}

.badge {
  padding: 3px 8px;
  border-radius: 999px;
  background: #e8eefc;
  color: #1d4ed8;
  font-size: 12px;
}

.profile-wrap {
  padding: 12px;
  display: flex;
  gap: 12px;
}

.profile-avatar {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(145deg, #22c55e, #16a34a);
}

.profile-detail {
  flex: 1;
  min-width: 0;
}

.profile-detail h3 {
  margin: 0 0 10px;
  font-size: 15px;
}

.preferences-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preference-item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
}

.pref-key {
  color: #57534e;
}

.pref-value {
  padding: 2px 8px;
  border-radius: 6px;
  background: #ede9e2;
}

.pref-confidence {
  color: #78716c;
}

.tabs {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tab {
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid #ddd8cf;
  background: #f7f5f1;
  color: #57534e;
  font-size: 12px;
  cursor: pointer;
}

.tab.active {
  background: #171717;
  border-color: #171717;
  color: #ffffff;
}

.memory-list {
  max-height: 360px;
  overflow-y: auto;
}

.memory-item {
  padding: 12px;
  border-bottom: 1px solid #ebe7df;
}

.memory-item:last-child {
  border-bottom: none;
}

.memory-type {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  margin-bottom: 8px;
}

.memory-type.persona {
  color: #075985;
  background: #e0f2fe;
}

.memory-type.preference {
  color: #166534;
  background: #dcfce7;
}

.memory-type.habit {
  color: #9a3412;
  background: #ffedd5;
}

.memory-type.important {
  color: #9f1239;
  background: #ffe4e6;
}

.memory-type.facts {
  color: #5b21b6;
  background: #ede9fe;
}

.memory-content {
  margin: 0 0 8px;
  line-height: 1.6;
  color: #1c1917;
  font-size: 14px;
}

.memory-meta {
  display: flex;
  gap: 12px;
  color: #78716c;
  font-size: 12px;
}

.habits-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
  padding: 12px;
}

.habit-card {
  border: 1px solid #e7e1d8;
  border-radius: 10px;
  background: #f6f3ee;
  padding: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.habit-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  background: #171717;
  flex-shrink: 0;
}

.habit-icon svg {
  width: 15px;
  height: 15px;
}

.habit-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.habit-type {
  font-size: 11px;
  color: #78716c;
}

.habit-value {
  font-size: 13px;
  color: #1c1917;
  font-weight: 600;
}

.habit-frequency {
  font-size: 13px;
  color: #166534;
  font-weight: 600;
}

.empty-state {
  padding: 22px 12px;
  text-align: center;
  color: #78716c;
  font-size: 13px;
}

.empty-hint {
  margin: 0;
  color: #78716c;
  font-size: 13px;
}

.full-width {
  grid-column: 1 / -1;
}

.mobile-mask {
  display: none;
}

@media (max-width: 1280px) {
  .panel-grid {
    grid-template-columns: 1fr;
  }
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
}

@media (max-width: 760px) {
  .memory-shell {
    padding: 12px;
  }

  .memory-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-left {
    width: 100%;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .state-tag {
    align-self: flex-start;
  }
}
</style>
