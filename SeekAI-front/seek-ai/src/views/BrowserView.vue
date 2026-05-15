<template>
  <div class="browser-view">
    <div class="page-header">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        返回
      </button>
      <h1>浏览器自动化</h1>
      <p>AI 帮你操作浏览器 - 填表、点击、截图</p>
    </div>

    <!-- 浏览器控制面板 -->
    <div class="control-panel">
      <div class="url-bar">
        <input
          v-model="url"
          type="text"
          class="input"
          placeholder="输入网址..."
          @keydown.enter="navigate"
        />
        <button class="btn btn-primary" @click="navigate" :disabled="loading">
          {{ loading ? '加载中...' : '打开' }}
        </button>
      </div>

      <!-- 快捷操作 -->
      <div class="quick-actions">
        <button class="action-btn" @click="quickAction('screenshot')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
            <circle cx="8.5" cy="8.5" r="1.5"/>
            <polyline points="21 15 16 10 5 21"/>
          </svg>
          截图
        </button>
        <button class="action-btn" @click="quickAction('getContent')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
          </svg>
          获取内容
        </button>
        <button class="action-btn" @click="quickAction('getLinks')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
            <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
          </svg>
          获取链接
        </button>
        <button class="action-btn" @click="quickAction('scrollDown')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <polyline points="19 12 12 19 5 12"/>
          </svg>
          向下滚动
        </button>
      </div>
    </div>

    <!-- 交互操作 -->
    <div class="interaction-panel">
      <div class="panel-section">
        <h3>点击元素</h3>
        <div class="input-row">
          <input v-model="clickSelector" type="text" class="input" placeholder="CSS 选择器，如 #button, .link" />
          <button class="btn btn-secondary" @click="doClick">点击</button>
        </div>
      </div>

      <div class="panel-section">
        <h3>输入文本</h3>
        <div class="input-row">
          <input v-model="typeSelector" type="text" class="input" placeholder="CSS 选择器" />
          <input v-model="typeText" type="text" class="input" placeholder="输入文本" />
          <button class="btn btn-secondary" @click="doType">输入</button>
        </div>
      </div>

      <div class="panel-section">
        <h3>执行 JavaScript</h3>
        <div class="input-row">
          <input v-model="jsCode" type="text" class="input" placeholder="JS 代码，如 window.scrollTo(0, 500)" />
          <button class="btn btn-secondary" @click="doExecute">执行</button>
        </div>
      </div>
    </div>

    <!-- 结果展示 -->
    <div class="result-panel">
      <div class="result-header">
        <h3>结果</h3>
        <span v-if="status" class="status-badge" :class="status.type">{{ status.message }}</span>
      </div>
      <div class="result-content" v-html="formattedResult"></div>
    </div>

    <!-- 截图预览 -->
    <div v-if="screenshotUrl" class="screenshot-panel">
      <div class="panel-header">
        <h3>截图预览</h3>
        <button class="btn btn-ghost" @click="screenshotUrl = ''">关闭</button>
      </div>
      <div class="screenshot-preview">
        <img :src="screenshotUrl" alt="页面截图" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const goBack = () => {
  router.push('/')
}
import axios from 'axios'

const url = ref('https://www.baidu.com')
const loading = ref(false)
const status = ref(null)
const result = ref('')
const screenshotUrl = ref('')

// 交互
const clickSelector = ref('')
const typeSelector = ref('')
const typeText = ref('')
const jsCode = ref('')

const formattedResult = computed(() => {
  if (!result.value) return '暂无结果'
  return result.value.replace(/\n/g, '<br>')
})

const navigate = async () => {
  if (!url.value) return
  loading.value = true
  status.value = { type: 'info', message: '加载中...' }
  result.value = ''

  try {
    const response = await axios.post('http://localhost:8080/api/browser/navigate', null, {
      params: { url: url.value }
    })
    result.value = response.data.result || '导航成功'
    status.value = { type: 'success', message: '完成' }
  } catch (e) {
    result.value = '操作失败: ' + e.message
    status.value = { type: 'error', message: '失败' }
  }
  loading.value = false
}

const quickAction = async (action) => {
  status.value = { type: 'info', message: '执行中...' }
  try {
    let response
    if (action === 'screenshot') {
      response = await axios.post('http://localhost:8080/api/browser/screenshot', null, {
        params: { path: 'screenshots/screenshot.png' }
      })
      result.value = response.data.result
      // 模拟截图显示
      screenshotUrl.value = '/screenshots/screenshot.png?' + Date.now()
    } else if (action === 'getContent') {
      response = await axios.get('http://localhost:8080/api/browser/content')
      result.value = response.data.content
    } else if (action === 'getLinks') {
      response = await axios.get('http://localhost:8080/api/browser/links')
      result.value = response.data.links
    } else if (action === 'scrollDown') {
      response = await axios.post('http://localhost:8080/api/browser/scroll', null, {
        params: { pixels: 500 }
      })
      result.value = response.data.result
    }
    status.value = { type: 'success', message: '完成' }
  } catch (e) {
    result.value = '操作失败: ' + e.message
    status.value = { type: 'error', message: '失败' }
  }
}

const doClick = async () => {
  if (!clickSelector.value) return
  status.value = { type: 'info', message: '点击中...' }
  try {
    const response = await axios.post('http://localhost:8080/api/browser/click', null, {
      params: { selector: clickSelector.value }
    })
    result.value = response.data.result
    status.value = { type: 'success', message: '完成' }
  } catch (e) {
    result.value = '点击失败: ' + e.message
    status.value = { type: 'error', message: '失败' }
  }
}

const doType = async () => {
  if (!typeSelector.value || !typeText.value) return
  status.value = { type: 'info', message: '输入中...' }
  try {
    const response = await axios.post('http://localhost:8080/api/browser/type', null, {
      params: { selector: typeSelector.value, text: typeText.value }
    })
    result.value = response.data.result
    status.value = { type: 'success', message: '完成' }
  } catch (e) {
    result.value = '输入失败: ' + e.message
    status.value = { type: 'error', message: '失败' }
  }
}

const doExecute = async () => {
  if (!jsCode.value) return
  status.value = { type: 'info', message: '执行中...' }
  try {
    const response = await axios.post('http://localhost:8080/api/browser/execute', null, {
      params: { script: jsCode.value }
    })
    result.value = response.data.result
    status.value = { type: 'success', message: '完成' }
  } catch (e) {
    result.value = '执行失败: ' + e.message
    status.value = { type: 'error', message: '失败' }
  }
}
</script>

<style scoped>
.browser-view {
  padding: var(--space-xl);
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.page-header h1 {
  font-size: var(--font-size-2xl);
  margin-bottom: var(--space-xs);
}

.page-header p {
  color: var(--color-text-secondary);
}

/* 控制面板 */
.control-panel {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
}

.url-bar {
  display: flex;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.url-bar .input {
  flex: 1;
}

.quick-actions {
  display: flex;
  gap: var(--space-sm);
}

.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-xs);
  padding: var(--space-sm) var(--space-md);
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.action-btn:hover {
  background: var(--color-accent);
  border-color: var(--color-accent);
}

.action-btn svg {
  width: 16px;
  height: 16px;
}

/* 交互面板 */
.interaction-panel {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
}

.panel-section {
  margin-bottom: var(--space-md);
}

.panel-section:last-child {
  margin-bottom: 0;
}

.panel-section h3 {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-bottom: var(--space-sm);
}

.input-row {
  display: flex;
  gap: var(--space-sm);
}

.input-row .input {
  flex: 1;
}

/* 结果面板 */
.result-panel {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  flex: 1;
  min-height: 200px;
  display: flex;
  flex-direction: column;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md);
  border-bottom: 1px solid var(--color-border);
}

.result-header h3 {
  font-size: var(--font-size-base);
}

.status-badge {
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
}

.status-badge.success { background: rgba(48, 209, 88, 0.2); color: var(--color-accent-green); }
.status-badge.error { background: rgba(255, 69, 58, 0.2); color: var(--color-accent-red); }
.status-badge.info { background: rgba(10, 132, 255, 0.2); color: var(--color-accent); }

.result-content {
  flex: 1;
  padding: var(--space-md);
  overflow-y: auto;
  font-size: var(--font-size-sm);
  font-family: monospace;
  white-space: pre-wrap;
  line-height: 1.6;
}

/* 截图 */
.screenshot-panel {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  max-height: 400px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md);
  border-bottom: 1px solid var(--color-border);
}

.screenshot-preview {
  padding: var(--space-md);
  overflow: auto;
}

.screenshot-preview img {
  max-width: 100%;
  border-radius: var(--radius-sm);
}
</style>