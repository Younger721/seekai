<template>
  <div class="memory-view" ref="contentRef" @scroll="onScroll">
    <div class="page-header">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        返回
      </button>
      <h1>记忆系统</h1>
      <p>越用越懂你 - AI 会记住你的偏好和习惯</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon blue">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2a10 10 0 0 1 10 10c0 5.52-4.48 10-10 10S2 17.52 2 12"/>
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.memories }}</span>
          <span class="stat-label">记忆条目</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon green">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="3"/>
            <path d="M12 1v6m0 6v6"/>
            <path d="m4.22 4.22 4.24 4.24m7.08 7.08 4.24 4.24"/>
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.preferences }}</span>
          <span class="stat-label">用户偏好</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon orange">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ stats.habits }}</span>
          <span class="stat-label">使用习惯</span>
        </div>
      </div>
    </div>

    <!-- 用户画像 -->
    <div class="section-card">
      <div class="section-header">
        <h2>用户画像</h2>
        <span class="badge badge-blue">AI 生成</span>
      </div>
      <div class="user-profile">
        <div class="profile-avatar">{{ userName.charAt(0) }}</div>
        <div class="profile-info">
          <h3>{{ userName }}</h3>
          <div class="preferences-list">
            <div v-for="pref in preferences" :key="pref.preferenceKey" class="preference-item">
              <span class="pref-key">{{ pref.preferenceKey }}</span>
              <span class="pref-value">{{ pref.preferenceValue }}</span>
              <span class="pref-confidence">{{ Math.round(pref.confidence * 100) }}% 置信度</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 记忆列表 -->
    <div class="section-card">
      <div class="section-header">
        <h2>记忆库</h2>
        <div class="tabs">
          <button
            v-for="tab in ['全部', '偏好', '习惯', '重要']"
            :key="tab"
            :class="['tab', { active: activeTab === tab }]"
            @click="activeTab = tab"
          >
            {{ tab }}
          </button>
        </div>
      </div>

      <div class="memory-list">
        <div v-for="memory in filteredMemories" :key="memory.id" class="memory-item">
          <div class="memory-type" :class="memory.memoryType?.toLowerCase()">
            {{ getTypeLabel(memory.memoryType) }}
          </div>
          <div class="memory-content">{{ memory.content }}</div>
          <div class="memory-meta">
            <span>创建于: {{ formatDate(memory.createdAt) }}</span>
            <span>访问: {{ memory.accessCount || 0 }} 次</span>
          </div>
        </div>
        <div v-if="filteredMemories.length === 0" class="empty-state">
          暂无记忆记录
        </div>
      </div>
    </div>

    <!-- 习惯统计 -->
    <div class="section-card">
      <div class="section-header">
        <h2>常用功能</h2>
      </div>
      <div class="habits-grid">
        <div v-for="habit in habits" :key="habit.id" class="habit-card">
          <div class="habit-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
            </svg>
          </div>
          <div class="habit-info">
            <span class="habit-type">{{ habit.habitType }}</span>
            <span class="habit-value">{{ habit.habitValue }}</span>
          </div>
          <div class="habit-frequency">
            {{ habit.frequency }} 次
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'

// 滚动位置保持
const contentRef = ref(null)
const SCROLL_KEY = 'seekai_memory_scroll'

const onScroll = () => {
  if (contentRef.value) {
    sessionStorage.setItem(SCROLL_KEY, contentRef.value.scrollTop)
  }
}

const restoreScroll = () => {
  const saved = sessionStorage.getItem(SCROLL_KEY)
  if (saved && contentRef.value) {
    contentRef.value.scrollTop = parseInt(saved, 10)
  }
}

const userId = ref('user1')
const userName = ref('用户')
const stats = ref({ memories: 0, preferences: 0, habits: 0 })
const preferences = ref([])
const habits = ref([])
const memories = ref([])
const activeTab = ref('全部')

const filteredMemories = computed(() => {
  if (activeTab.value === '全部') return memories.value
  const map = { '偏好': 'PREFERENCE', '习惯': 'HABIT', '重要': 'IMPORTANT' }
  return memories.value.filter(m => m.memoryType === map[activeTab.value])
})

const getTypeLabel = (type) => {
  const labels = { 'PERSONA': '人物', 'PREFERENCE': '偏好', 'HABIT': '习惯', 'FACTS': '事实', 'IMPORTANT': '重要' }
  return labels[type] || type
}

const formatDate = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleDateString('zh-CN')
}

const fetchData = async () => {
  try {
    const [prefRes, habitRes] = await Promise.all([
      axios.get(`http://localhost:8080/api/memory/preferences?userId=${userId.value}`),
      axios.get(`http://localhost:8080/api/memory/habits?userId=${userId.value}`)
    ])
    preferences.value = prefRes.data || []
    habits.value = habitRes.data || []
    stats.value = {
      preferences: preferences.value.length,
      habits: habits.value.length,
      memories: 0
    }
  } catch (e) {
    console.log('使用模拟数据')
    preferences.value = [
      { preferenceKey: 'response_length', preferenceValue: 'medium', confidence: 0.8 },
      { preferenceKey: 'language', preferenceValue: 'Chinese', confidence: 0.9 }
    ]
    habits.value = [
      { habitType: 'agent', habitValue: 'CODE_EXPERT', frequency: 15 },
      { habitType: 'agent', habitValue: 'SEARCH_AGENT', frequency: 8 }
    ]
    stats.value = { preferences: 2, habits: 2, memories: 5 }
  }
}

onMounted(() => {
  fetchData()
  restoreScroll()
})
</script>

<style scoped>
.memory-view {
  padding: var(--space-xl);
  height: 100%;
  overflow-y: auto;
}

.page-header {
  margin-bottom: var(--space-xl);
}

.page-header h1 {
  font-size: var(--font-size-2xl);
  margin-bottom: var(--space-xs);
}

.page-header p {
  color: var(--color-text-secondary);
}

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-lg);
  margin-bottom: var(--space-xl);
}

.stat-card {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-icon svg {
  width: 24px;
  height: 24px;
  color: white;
}

.stat-icon.blue { background: var(--color-accent); }
.stat-icon.green { background: var(--color-accent-green); }
.stat-icon.orange { background: var(--color-accent-orange); }

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: var(--font-size-2xl);
  font-weight: 600;
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

/* 区块卡片 */
.section-card {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  margin-bottom: var(--space-lg);
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-lg);
  border-bottom: 1px solid var(--color-border);
}

.section-header h2 {
  font-size: var(--font-size-lg);
}

/* 用户画像 */
.user-profile {
  display: flex;
  gap: var(--space-lg);
  padding: var(--space-lg);
}

.profile-avatar {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-purple));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-2xl);
  font-weight: 600;
  flex-shrink: 0;
}

.profile-info h3 {
  margin-bottom: var(--space-md);
}

.preferences-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.preference-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-size-sm);
}

.pref-key {
  color: var(--color-text-secondary);
}

.pref-value {
  background: var(--color-bg-tertiary);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
}

.pref-confidence {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

/* 记忆列表 */
.memory-list {
  max-height: 300px;
  overflow-y: auto;
}

.memory-item {
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border);
}

.memory-item:last-child {
  border-bottom: none;
}

.memory-type {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  margin-bottom: var(--space-xs);
}

.memory-type.persona { background: rgba(10, 132, 255, 0.2); color: var(--color-accent); }
.memory-type.preference { background: rgba(48, 209, 88, 0.2); color: var(--color-accent-green); }
.memory-type.habit { background: rgba(255, 159, 10, 0.2); color: var(--color-accent-orange); }
.memory-type.important { background: rgba(255, 69, 58, 0.2); color: var(--color-accent-red); }

.memory-content {
  margin-bottom: var(--space-xs);
}

.memory-meta {
  display: flex;
  gap: var(--space-lg);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.empty-state {
  padding: var(--space-xl);
  text-align: center;
  color: var(--color-text-secondary);
}

/* 习惯 */
.habits-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--space-md);
  padding: var(--space-lg);
}

.habit-card {
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.habit-icon {
  width: 32px;
  height: 32px;
  background: var(--color-accent);
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}

.habit-icon svg {
  width: 16px;
  height: 16px;
  color: white;
}

.habit-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.habit-type {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.habit-value {
  font-size: var(--font-size-sm);
  font-weight: 500;
}

.habit-frequency {
  font-size: var(--font-size-sm);
  color: var(--color-accent);
}
</style>