<template>
  <div class="chat-window">
    <div class="chat-header">
      <div class="title-info">
        <h3>{{ currentTitle }}</h3>
        <span v-if="currentConversationId" class="id-badge">#{{ currentConversationId }}</span>
      </div>
      <div class="actions">
        <el-button v-if="currentConversationId" link type="danger" plain @click="clearMessages">
          <el-icon class="el-icon--left"><Delete /></el-icon>
          Clear History
        </el-button>
      </div>
    </div>
    
    <div class="message-list" ref="messageListRef">
      <div v-if="!currentConversationId" class="empty-state">
        <el-empty description="Please select or create a conversation to start chatting" />
      </div>
      
      <template v-else>
        <div v-for="(msg, index) in messages" :key="index" :id="'msg-' + index" :class="['message-item', msg.type === 'assistant' ? 'assistant' : 'user']">
          <div class="message-inner">
            <div class="avatar">
              <el-avatar :size="36" :src="msg.type === 'assistant' ? '/favicon.svg' : ''">
                {{ msg.type === 'assistant' ? 'AI' : 'U' }}
              </el-avatar>
            </div>
            <div class="content-wrapper">
              <div class="role-name">
                {{ msg.type === 'assistant' ? 'SeekAI' : 'You' }}
                <span v-if="msg.type === 'assistant' && msg.agentName" class="agent-tag">{{ msg.agentName }}</span>
              </div>
              <div class="content">
                <template v-if="msg.loading">
                  <div class="loading-dots">
                    <span></span><span></span><span></span>
                  </div>
                </template>
                <template v-else>
                  <div class="react-blocks" v-if="msg.reactBlocks && msg.reactBlocks.length > 0">
                    <div 
                      v-for="(block, bIndex) in msg.reactBlocks" 
                      :key="bIndex" 
                      :class="['react-block', `react-${block.type}`]"
                    >
                      <div class="react-block-header">
                        <el-icon v-if="block.type === 'thought'"><Cpu /></el-icon>
                        <el-icon v-else-if="block.type === 'action'"><Operation /></el-icon>
                        <el-icon v-else-if="block.type === 'observation'"><View /></el-icon>
                        <el-icon v-else-if="block.type === 'critique'"><Warning /></el-icon>
                        <span>{{ formatReActType(block.type) }}</span>
                      </div>
                      <div class="react-block-content markdown-body" v-html="formatMessage(block.content, msg.loading === false && index === messages.length - 1 && streaming && bIndex === msg.reactBlocks.length - 1)"></div>
                    </div>
                  </div>
                  
                  <div v-if="msg.content" class="markdown-body final-answer" :class="{ 'streaming': msg.loading === false && index === messages.length - 1 && streaming }" v-html="formatMessage(msg.content, msg.loading === false && index === messages.length - 1 && streaming)"></div>
                </template>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
    
    <div class="input-area" ref="inputAreaRef">
      <div class="input-inner">
        <div class="input-container" style="position: relative;">
          <el-input
            ref="textareaRef"
            v-model="inputMsg"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 8 }"
            placeholder="Send a message to SeekAI... (输入 @ 提及 Agent)"
            resize="none"
            @keydown.enter.prevent="handleSend"
            @input="handleInputChange"
            @keydown="handleKeydown"
            :disabled="!currentConversationId || streaming"
          />

          <!-- @ 提及自动补全列表 -->
          <div v-if="showMentionList" class="mention-list" :style="mentionListStyle">
            <div
              v-for="(agent, index) in filteredAgents"
              :key="agent.name"
              :class="['mention-item', { active: mentionIndex === index }]"
              @click="selectMentionAgent(agent)"
              @mouseenter="mentionIndex = index"
            >
              <span class="mention-icon">{{ getAgentEmoji(agent.name) }}</span>
              <span class="mention-name">{{ getAgentAlias(agent.name) }}</span>
              <span class="mention-desc">{{ agent.description }}</span>
            </div>
            <div v-if="filteredAgents.length === 0" class="mention-empty">
              没有匹配的 Agent
            </div>
          </div>

          <el-button
            type="primary"
            class="send-btn"
            :disabled="!inputMsg.trim() || streaming || !currentConversationId"
            @click="handleSend"
          >
            <el-icon><Promotion /></el-icon>
          </el-button>
        </div>
        <div class="input-tips">
          Press Enter to send, Shift + Enter for new line. 输入 <span class="tip-highlight">@</span> 提及 Agent
        </div>
      </div>
    </div>
    
    <div class="chat-minimap" v-if="messages.length > 0">
      <el-tooltip
        v-for="(msg, index) in messages"
        :key="'minimap-' + index"
        :content="getMinimapPreview(msg.content)"
        placement="left"
        :show-after="100"
        effect="dark"
      >
        <div 
          class="minimap-item"
          :class="[msg.type === 'assistant' ? 'assistant' : 'user', { active: activeMessageIndex === index }]"
          @click="scrollToMessage(index)"
        ></div>
      </el-tooltip>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, watch, onMounted, onBeforeUnmount, markRaw, shallowRef } from 'vue'
import { useChatStore } from '../stores/chat'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Cpu, Operation, View, Warning, Promotion, Delete } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import * as echarts from 'echarts'

// 先初始化 store（必须在 watch 之前）
const chatStore = useChatStore()

// 使用 shallowRef 避免深度响应式追踪
const messagesShallow = shallowRef([])
const streamingShallow = shallowRef(false)

// watch 使用 chatStore（现在已定义）
watch(() => chatStore.messages, (newVal) => {
  messagesShallow.value = newVal
}, { immediate: true })

watch(() => chatStore.streaming, (newVal) => {
  streamingShallow.value = newVal
}, { immediate: true })

// Markdown 渲染缓存 (使用 shallowRef 避免深度响应式)
const messageRenderCache = shallowRef(new Map())
let isRenderingECharts = false

// 节流辅助函数
let scrollTimer = null
let chartTimer = null
const throttle = (fn, delay) => {
  return (...args) => {
    if (!scrollTimer) {
      fn(...args)
      scrollTimer = setTimeout(() => { scrollTimer = null }, delay)
    }
  }
}

// 图表实例管理
const chartInstances = ref(new Map())

// ============ Markdown-it 配置与自定义渲染 ============
const md = new MarkdownIt({
  html: true,
  breaks: true,
  linkify: true,
  highlight: function (str, lang) {
    // 拦截 ECharts 渲染块
    if (lang === 'echarts') {
      const chartId = `chart-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
      return `<div class="echarts-container chart-container" id="${chartId}" data-chart-config="${encodeURIComponent(str)}"></div>`
    }
    
    // 普通代码块处理
    const escapeHtml = (unsafe) => {
      return unsafe
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
    }
    
    const safeStr = escapeHtml(str)
    const languageClass = lang ? `language-${lang}` : 'language-plaintext'
    const displayLang = lang ? lang.toUpperCase() : 'CODE'

    const codeBlockId = `code-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
    return `
      <div class="custom-code-block">
        <div class="code-header">
          <div class="mac-dots">
            <span class="dot red"></span>
            <span class="dot yellow"></span>
            <span class="dot green"></span>
          </div>
          <div class="code-lang">${displayLang}</div>
          <button class="copy-btn" data-code-id="${codeBlockId}" data-code="${encodeURIComponent(str)}">
            <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
            <span>复制</span>
          </button>
        </div>
        <div class="code-body-wrapper">
          <pre><code id="${codeBlockId}" class="${languageClass}">${safeStr}</code></pre>
        </div>
      </div>
    `
  }
})

const inputMsg = ref('')
const messageListRef = ref(null)
const inputAreaRef = ref(null)
const isUserScrolling = ref(false)
const activeMessageIndex = ref(-1)
let observer = null
let resizeObserver = null
let intersectionObserver = null

const currentConversationId = computed(() => chatStore.currentConversationId)
const messages = computed(() => messagesShallow.value)
const streaming = computed(() => streamingShallow.value)
const currentAgent = computed(() => chatStore.selectedAgent || 'auto')
const agents = computed(() => chatStore.agents)

// @ 提及相关状态
const textareaRef = ref(null)
const showMentionList = ref(false)
const mentionIndex = ref(0)
const mentionQuery = ref('')
const mentionListStyle = ref({})

// Agent 别名映射（中文别名 -> 英文名）
const agentAliases = {
  '爬虫': 'CRAWLER_AGENT',
  'crawler': 'CRAWLER_AGENT',
  '搜索': 'SEARCH_AGENT',
  'search': 'SEARCH_AGENT',
  '图表': 'CHART_AGENT',
  'chart': 'CHART_AGENT',
  '画图': 'CHART_AGENT',
  '数据': 'DATA_AGENT',
  'data': 'DATA_AGENT',
  '分析': 'DATA_AGENT',
  '数据库': 'DATA_AGENT',
  '代码': 'CODE_EXPERT',
  'code': 'CODE_EXPERT',
  '编程': 'AUTO_CODER_AGENT',
  '写代码': 'AUTO_CODER_AGENT',
  '文档': 'DOCUMENT_AGENT',
  'document': 'DOCUMENT_AGENT',
  '助手': 'GENERAL_HELPER',
  'helper': 'GENERAL_HELPER',
  '通用': 'GENERAL_HELPER',
  '多步骤': 'MULTI_STEP_AGENT',
  '执行': 'MULTI_STEP_AGENT',
  '帮我': 'MULTI_STEP_AGENT',
  'steps': 'MULTI_STEP_AGENT',
  'auto': 'MULTI_STEP_AGENT'
}

// Agent 中文名/别名显示
const getAgentAlias = (name) => {
  const aliasMap = {
    'CRAWLER_AGENT': '爬虫',
    'SEARCH_AGENT': '搜索',
    'CHART_AGENT': '图表',
    'DATA_AGENT': '数据',
    'CODE_EXPERT': '代码',
    'AUTO_CODER_AGENT': '编程',
    'DOCUMENT_AGENT': '文档',
    'GENERAL_HELPER': '助手',
    'MULTI_STEP_AGENT': '多步骤'
  }
  return aliasMap[name] || name
}

// Agent 表情映射
const getAgentEmoji = (name) => {
  const emojiMap = {
    'CRAWLER_AGENT': '🕷️',
    'SEARCH_AGENT': '🔍',
    'CHART_AGENT': '📊',
    'DATA_AGENT': '💾',
    'CODE_EXPERT': '💻',
    'AUTO_CODER_AGENT': '⌨️',
    'DOCUMENT_AGENT': '📄',
    'GENERAL_HELPER': '🤖',
    'MULTI_STEP_AGENT': '📋'
  }
  return emojiMap[name] || '🤖'
}

// 过滤后的 Agent 列表
const filteredAgents = computed(() => {
  if (!mentionQuery.value) return agents.value || []
  const query = mentionQuery.value.toLowerCase()
  return (agents.value || []).filter(agent => {
    const alias = getAgentAlias(agent.name).toLowerCase()
    const name = agent.name.toLowerCase()
    const desc = (agent.description || '').toLowerCase()
    return alias.includes(query) || name.includes(query) || desc.includes(query)
  })
})

// 处理输入变化
const handleInputChange = () => {
  const text = inputMsg.value
  const cursorPos = getCursorPosition()

  // 查找 @ 的位置
  const lastAtIndex = text.lastIndexOf('@', cursorPos - 1)

  if (lastAtIndex !== -1) {
    // 检查 @ 后面是否有空格或其他分隔符
    const textAfterAt = text.slice(lastAtIndex + 1, cursorPos)
    const hasSpace = textAfterAt.includes(' ')

    if (!hasSpace) {
      // 在 @ 后面，显示补全列表
      mentionQuery.value = textAfterAt
      showMentionList.value = true
      mentionIndex.value = 0

      // 计算补全列表位置
      updateMentionListPosition(lastAtIndex)
      return
    }
  }

  // 不在 @ 后面，隐藏列表
  showMentionList.value = false
  mentionQuery.value = ''
}

// 获取光标位置
const getCursorPosition = () => {
  const textarea = textareaRef.value?.textarea
  if (!textarea) return 0
  return textarea.selectionStart
}

// 更新补全列表位置
const updateMentionListPosition = (atIndex) => {
  // 简单处理：显示在输入框下方
  mentionListStyle.value = {
    bottom: '100%',
    left: '0',
    right: '0',
    maxHeight: '200px',
    overflowY: 'auto'
  }
}

// 处理键盘事件
const handleKeydown = (e) => {
  if (!showMentionList.value) return

  if (e.key === 'ArrowDown') {
    e.preventDefault()
    mentionIndex.value = Math.min(mentionIndex.value + 1, filteredAgents.value.length - 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    mentionIndex.value = Math.max(mentionIndex.value - 1, 0)
  } else if (e.key === 'Enter' || e.key === 'Tab') {
    e.preventDefault()
    if (filteredAgents.value.length > 0) {
      selectMentionAgent(filteredAgents.value[mentionIndex.value])
    }
  } else if (e.key === 'Escape') {
    showMentionList.value = false
  } else if (e.key === 'Backspace') {
    // 检查是否删除了 @，如果是则关闭列表
    const text = inputMsg.value
    const cursorPos = getCursorPosition()
    const lastAtIndex = text.lastIndexOf('@', cursorPos - 1)
    if (lastAtIndex === -1 || cursorPos <= lastAtIndex + 1) {
      showMentionList.value = false
    }
  }
}

// 选择 Agent
const selectMentionAgent = (agent) => {
  const text = inputMsg.value
  const cursorPos = getCursorPosition()
  const lastAtIndex = text.lastIndexOf('@', cursorPos - 1)

  if (lastAtIndex !== -1) {
    // 替换 @xxx 为 @AgentName
    const before = text.slice(0, lastAtIndex)
    const after = text.slice(cursorPos)
    const alias = getAgentAlias(agent.name)

    inputMsg.value = before + '@' + alias + ' ' + after

    // 关闭补全列表
    showMentionList.value = false

    // 让输入框重新获取焦点
    nextTick(() => {
      const newPos = lastAtIndex + alias.length + 2
      textareaRef.value?.textarea?.setSelectionRange(newPos, newPos)
    })
  }
}

const currentTitle = computed(() => {
  if (!currentConversationId.value) return 'Welcome to SeekAI'
  const conv = chatStore.conversations.find(c => c.id === currentConversationId.value)
  return conv?.title || 'Current Chat'
})

// ============ ECharts 动态渲染逻辑 ============
const renderECharts = async () => {
  // 防抖：避免重复渲染
  if (isRenderingECharts) return
  isRenderingECharts = true

  try {
    await nextTick()

    const containers = document.querySelectorAll('.echarts-container:not([data-initialized])')
    if (containers.length === 0) {
      isRenderingECharts = false
      return
    }

    await new Promise(resolve => requestAnimationFrame(resolve))

    containers.forEach(container => {
      const configStr = container.getAttribute('data-chart-config')
      if (!configStr) return

      const chartId = container.id
      const rect = container.getBoundingClientRect()

      if (rect.width === 0 || rect.height === 0) {
        console.warn(`Chart container ${chartId} has no size, skipping init`)
        setTimeout(() => renderECharts(), 100)
        return
      }

      try {
        const option = JSON.parse(decodeURIComponent(configStr))

        if (chartInstances.value.has(chartId)) {
          const oldChart = chartInstances.value.get(chartId)
          if (oldChart._resizeObserver) oldChart._resizeObserver.disconnect()
          oldChart.dispose()
        }

        const chart = markRaw(echarts.init(container, null, {
          width: rect.width,
          height: rect.height
        }))

        chart.setOption(option)
        container.setAttribute('data-initialized', 'true')
        chartInstances.value.set(chartId, chart)

        const resizeObserver = new ResizeObserver((entries) => {
          for (const entry of entries) {
            if (entry.contentRect.width > 0 && entry.contentRect.height > 0) {
              chart.resize()
            }
          }
        })
        resizeObserver.observe(container)
        chart._resizeObserver = resizeObserver

      } catch (e) {
        console.error('ECharts 渲染失败:', e)
        container.innerHTML = `<div style="color: #ef4444; padding: 20px; text-align: center; background: #fef2f2; border-radius: 8px; border: 1px solid #fee2e2;">📊 图表配置解析失败<br><small>${e.message}</small></div>`
      }
    })
  } catch (e) {
    console.error('ECharts 渲染错误:', e)
  } finally {
    isRenderingECharts = false
  }
}

// ============ 滚动与观察者逻辑 ============
const checkIfAtBottom = () => {
  if (!messageListRef.value) return true
  const { scrollTop, scrollHeight, clientHeight } = messageListRef.value
  return scrollHeight - scrollTop - clientHeight < 50
}

const handleScroll = () => {
  isUserScrolling.value = !checkIfAtBottom()
}

const getMinimapPreview = (content) => {
  if (!content) return '...'
  const text = content.replace(/<[^>]+>/g, '').replace(/[*_#`~>]/g, '').trim()
  return text.length > 30 ? text.slice(0, 30) + '...' : text || '...'
}

const scrollToMessage = (index) => {
  const el = document.getElementById('msg-' + index)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    setTimeout(() => {
      isUserScrolling.value = !checkIfAtBottom()
    }, 500)
  }
}

const initIntersectionObserver = () => {
  if (intersectionObserver) {
    intersectionObserver.disconnect()
  }
  
  intersectionObserver = new IntersectionObserver((entries) => {
    let maxRatio = 0
    let targetIndex = -1
    
    entries.forEach(entry => {
      if (entry.isIntersecting && entry.intersectionRatio > maxRatio) {
        maxRatio = entry.intersectionRatio
        const idStr = entry.target.getAttribute('id')
        if (idStr && idStr.startsWith('msg-')) {
          targetIndex = parseInt(idStr.replace('msg-', ''), 10)
        }
      }
    })
    
    if (targetIndex !== -1) {
      activeMessageIndex.value = targetIndex
    }
  }, {
    root: messageListRef.value,
    threshold: [0.1, 0.5, 0.9]
  })
  
  nextTick(() => {
    const messageElements = document.querySelectorAll('.message-item')
    messageElements.forEach(el => intersectionObserver.observe(el))
  })
}

// ============ 生命周期与监听 ============
watch(() => messages.value.length, () => {
  initIntersectionObserver()
})

// 复制代码功能（事件委托）
const handleCopyCode = (e) => {
  const btn = e.target.closest('.copy-btn')
  if (!btn) return

  const codeStr = decodeURIComponent(btn.dataset.code || '')
  if (!codeStr) return

  navigator.clipboard.writeText(codeStr).then(() => {
    const originalHTML = btn.innerHTML
    btn.innerHTML = '<span class="copied-text">✓ 已复制</span>'
    btn.classList.add('copied')
    setTimeout(() => {
      btn.innerHTML = originalHTML
      btn.classList.remove('copied')
    }, 2000)
  }).catch(err => {
    console.error('复制失败', err)
  })
}

onMounted(() => {
  // 添加复制代码事件监听
  document.addEventListener('click', handleCopyCode)

  if (messageListRef.value) {
    messageListRef.value.addEventListener('scroll', handleScroll)

    observer = new MutationObserver(() => {
      if (!isUserScrolling.value && messageListRef.value) {
        messageListRef.value.scrollTop = messageListRef.value.scrollHeight
      }
    })

    observer.observe(messageListRef.value, {
      childList: true,
      subtree: true
      // 移除 characterData: true，这会导致严重的性能问题
    })
  }

  if (inputAreaRef.value) {
    resizeObserver = new ResizeObserver(() => {
      if (!isUserScrolling.value && messageListRef.value) {
        messageListRef.value.scrollTop = messageListRef.value.scrollHeight
      }
    })
    resizeObserver.observe(inputAreaRef.value)
  }
})

onBeforeUnmount(() => {
  // 移除复制代码事件监听
  document.removeEventListener('click', handleCopyCode)

  if (messageListRef.value) {
    messageListRef.value.removeEventListener('scroll', handleScroll)
  }
  if (observer) observer.disconnect()
  if (resizeObserver) resizeObserver.disconnect()
  if (intersectionObserver) intersectionObserver.disconnect()

  chartInstances.value.forEach((chart) => {
    if (chart._resizeObserver) chart._resizeObserver.disconnect()
    chart.dispose()
  })
  chartInstances.value.clear()
})

// 优化：移除 deep watch，改用 shallow 响应式 + 节流
const handleMessagesChange = throttle(async () => {
  await nextTick()
  if (!isUserScrolling.value && messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
  // 延迟渲染 ECharts，避免频繁初始化
  if (chartTimer) clearTimeout(chartTimer)
  chartTimer = setTimeout(() => renderECharts(), 150)
}, 100)

watch(() => messages.value.length, handleMessagesChange)

watch(() => streaming.value, (isStreaming) => {
  if (!isStreaming) {
    setTimeout(() => renderECharts(), 200)
  }
})

watch(() => currentConversationId.value, async () => {
  isUserScrolling.value = false
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
  chartInstances.value.forEach((chart) => {
    if (chart._resizeObserver) chart._resizeObserver.disconnect()
    chart.dispose()
  })
  chartInstances.value.clear()
})

// ============ 发送与处理消息 ============
const handleSend = () => {
  if (!inputMsg.value.trim() || streaming.value) return
  const msg = inputMsg.value
  inputMsg.value = ''
  chatStore.sendStreamMessage(msg)
}

const clearMessages = () => {
  ElMessageBox.confirm(
    'Are you sure you want to clear all messages in this conversation?',
    'Warning',
    {
      confirmButtonText: 'Clear',
      cancelButtonText: 'Cancel',
      type: 'warning',
    }
  ).then(async () => {
    try {
      await chatStore.clearConversationMessages(currentConversationId.value)
      chartInstances.value.forEach((chart) => {
        if (chart._resizeObserver) chart._resizeObserver.disconnect()
        chart.dispose()
      })
      chartInstances.value.clear()
    } catch (e) {}
  })
}

// 保护 Echarts 不受流式渲染标签截断的影响
const patchMarkdown = (text) => {
  if (!text) return ''
  const echartsBlocks = []
  let processed = text.replace(/```echarts[\s\S]*?```/g, (match) => {
    echartsBlocks.push(match)
    return `__ECHARTS_BLOCK_${echartsBlocks.length - 1}__`
  })
  
  const codeBlockRegex = /```/g
  const matches = processed.match(codeBlockRegex)
  if (matches && matches.length % 2 !== 0) {
    processed += '\n```'
  }
  
  echartsBlocks.forEach((block, i) => {
    processed = processed.replace(`__ECHARTS_BLOCK_${i}__`, block)
  })
  return processed
}

const formatMessage = (content, isStreaming = false) => {
  if (!content) return ''
  const safeContent = isStreaming ? patchMarkdown(content) : content
  return md.render(safeContent)
}

const formatReActType = (type) => {
  const map = {
    'thought': '思考过程',
    'action': '调用工具',
    'observation': '观察结果',
    'critique': '自我反思与纠错'
  }
  return map[type] || type
}
</script>

<style scoped>
/* ==================== 基础布局 ==================== */
.chat-window {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: transparent;
  position: relative;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

/* ==================== 头部 ==================== */
.chat-header {
  height: 64px;
  padding: 0 24px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  position: sticky;
  top: 0;
  z-index: 20;
  border-radius: 0;
}

.title-info {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
  margin-right: 16px;
}

.title-info h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.87);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  letter-spacing: -0.01em;
}

.id-badge {
  font-family: 'Fira Code', monospace;
  font-size: 11px;
  color: #7c3aed;
  background: rgba(124, 58, 237, 0.08);
  border: 1px solid rgba(124, 58, 237, 0.15);
  padding: 2px 8px;
  border-radius: 6px;
  font-weight: 500;
  flex-shrink: 0;
}

.actions {
  flex-shrink: 0;
}

/* ==================== 消息列表与条目 ==================== */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 32px 0;
  scroll-behavior: smooth;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(0, 0, 0, 0.4);
}

/* 空状态玻璃卡片 */
.empty-state :deep(.el-empty__description) {
  color: rgba(0, 0, 0, 0.5);
}

.message-item {
  display: flex;
  justify-content: center;
  padding: 32px 24px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.message-item.assistant {
  background: rgba(0, 0, 0, 0.02);
  border-top: 1px solid rgba(0, 0, 0, 0.04);
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

/* 消息项入场动画 */
.message-item {
  animation: messageSlideIn 0.4s cubic-bezier(0.4, 0, 0.2, 1) forwards;
}

@keyframes messageSlideIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-inner {
  display: flex;
  gap: 24px;
  width: 100%;
  max-width: 800px;
}

.avatar {
  flex-shrink: 0;
}

.avatar .el-avatar {
  background-color: #10a37f;
  color: white;
  font-weight: bold;
  border-radius: 8px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
  border: 2px solid #e5e7eb;
}

.message-item.user .avatar .el-avatar {
  background-color: #4b5563;
}

.content-wrapper {
  flex: 1;
  min-width: 0;
  max-width: 800px;
}

.role-name {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.78);
  margin-bottom: 8px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 10px;
}

.agent-tag {
  font-size: 11px;
  font-weight: 500;
  color: #059669;
  background: rgba(5, 150, 105, 0.08);
  padding: 2px 8px;
  border-radius: 12px;
  border: 1px solid rgba(5, 150, 105, 0.12);
}

/* ==================== 思考链路 (ReAct Blocks) ==================== */
.react-blocks {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 20px;
}

.react-block {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  border: 1px solid transparent;
  font-size: 14px;
}

.react-block-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  padding: 8px 16px;
  background-color: rgba(0, 0, 0, 0.03);
  border-bottom: 1px solid rgba(0, 0, 0, 0.03);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.react-block-content {
  padding: 16px;
  background-color: #fff;
  border: none;
  font-size: 14.5px;
  color: #4b5563;
}

/* 思考块颜色定制 */
.react-thought { border-color: #e5e7eb; }
.react-thought .react-block-header { color: #6b7280; }

.react-action { border-color: #bfdbfe; }
.react-action .react-block-header { color: #3b82f6; background-color: #eff6ff; border-bottom-color: #dbeafe; }
.react-action .react-block-content { font-family: 'Fira Code', monospace; }

.react-observation { border-color: #374151; }
.react-observation .react-block-header { color: #6d28d9; background-color: #f3f4f6; border-bottom-color: #e5e7eb;}
.react-observation .react-block-content { background-color: #f9fafb; color: #374151; font-family: 'Fira Code', monospace; }

.react-critique { border-color: #fde68a; }
.react-critique .react-block-header { color: #d97706; background-color: #fffbeb; border-bottom-color: #fef3c7;}
.react-critique .react-block-content { background-color: #fff; color: #92400e; }

/* ==================== Markdown 通用排版 ==================== */
.markdown-body {
  font-size: 15.5px;
  line-height: 1.75;
  color: rgba(0, 0, 0, 0.78);
  word-wrap: break-word;
}

.markdown-body :deep(p) {
  margin-top: 0;
  margin-bottom: 16px;
}
.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  color: rgba(0, 0, 0, 0.87);
  margin-top: 1.5em;
  margin-bottom: 0.8em;
  font-weight: 600;
  line-height: 1.3;
}
.markdown-body :deep(h1) { font-size: 1.5em; }
.markdown-body :deep(h2) { font-size: 1.3em; border-bottom: 1px solid rgba(0, 0, 0, 0.08); padding-bottom: 8px; }
.markdown-body :deep(h3) { font-size: 1.15em; }

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 24px;
  margin-top: 0;
  margin-bottom: 16px;
}
.markdown-body :deep(li) {
  margin-bottom: 4px;
}

.markdown-body :deep(blockquote) {
  margin: 16px 0;
  padding: 12px 16px;
  border-left: 4px solid rgba(139, 92, 246, 0.3);
  background: rgba(139, 92, 246, 0.04);
  color: rgba(0, 0, 0, 0.6);
  border-radius: 0 8px 8px 0;
}

/* ==================== 表格渲染样式 ==================== */
.markdown-body :deep(table) {
  width: 100%;
  margin: 20px 0;
  border-collapse: collapse;
  text-align: left;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.1);
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}
.markdown-body :deep(th) {
  background: rgba(0, 0, 0, 0.03);
  font-weight: 600;
  color: rgba(0, 0, 0, 0.87);
}
.markdown-body :deep(tr:last-child td) {
  border-bottom: none;
}
.markdown-body :deep(tr:nth-child(even) td) {
  background: rgba(0, 0, 0, 0.02);
}

/* ==================== 完美衔接的代码块样式 ==================== */
.markdown-body :deep(.custom-code-block) {
  /* 容器统一控制圆角和边框，解决脱节问题 */
  background-color: #f6f8fa;
  border-radius: 8px;
  overflow: hidden;
  margin: 20px 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  border: 1px solid #d0d7de;
  display: flex;
  flex-direction: column;
}

.markdown-body :deep(.code-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #f0f2f5;
  padding: 10px 16px;
  border-bottom: 1px solid #d0d7de; /* 明确的分割线 */
}

.markdown-body :deep(.mac-dots) {
  display: flex;
  gap: 6px;
  width: 60px; /* 固定宽度以居中语言标签 */
}

.markdown-body :deep(.dot) {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}
.markdown-body :deep(.dot.red) { background-color: #ff5f56; }
.markdown-body :deep(.dot.yellow) { background-color: #ffbd2e; }
.markdown-body :deep(.dot.green) { background-color: #27c93f; }

.markdown-body :deep(.code-lang) {
  flex: 1;
  text-align: center;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  font-size: 12px;
  font-weight: 600;
  color: #656d76;
  letter-spacing: 0.5px;
}

.markdown-body :deep(.copy-btn) {
  width: 60px;
  justify-content: flex-end;
  background: transparent;
  border: none;
  color: #656d76;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 12px;
  padding: 4px 0;
  transition: all 0.2s ease;
}
.markdown-body :deep(.copy-btn:hover) { color: #24292f; }
.markdown-body :deep(.copy-btn.copied) { color: #27c93f; }

.markdown-body :deep(.code-body-wrapper) {
  /* 包装层确保背景色和滚动表现一致 */
  background-color: #f6f8fa;
  width: 100%;
  overflow-x: auto;
}

.markdown-body :deep(.custom-code-block pre) {
  margin: 0 !important;
  padding: 16px;
  background-color: transparent !important;
  border: none !important;
}

.markdown-body :deep(pre code) {
  font-family: 'Fira Code', 'Cascadia Code', Consolas, Monaco, monospace;
  font-size: 14px;
  color: #24292f;
  line-height: 1.6;
  tab-size: 4;
  background: transparent;
}

/* ==================== 内联代码 (与代码块严格区分) ==================== */
.markdown-body :deep(:not(pre) > code) {
  font-family: 'Fira Code', 'Cascadia Code', Consolas, Monaco, monospace;
  background: rgba(139, 92, 246, 0.1);
  color: #7c3aed;
  padding: 0.2em 0.4em;
  border-radius: 4px;
  font-size: 0.9em;
  font-weight: 500;
  word-break: break-word;
}

/* ==================== ECharts 容器样式 ==================== */
.markdown-body :deep(.echarts-container) {
  transition: all 0.3s ease;
  min-width: 300px !important;
  min-height: 300px !important;
  width: 100% !important;
  height: 350px !important; /* 适当增加高度显得更大气 */
  box-sizing: border-box;
  margin: 20px 0;
  border-radius: 12px;
  border: 1px solid #e2e8f0 !important;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
  background: linear-gradient(135deg, #fafafa 0%, #ffffff 100%);
  padding: 10px;
}

/* ==================== 动画与杂项 ==================== */
.markdown-body.streaming :deep(*:last-child)::after {
  content: '▋';
  display: inline-block;
  margin-left: 4px;
  vertical-align: baseline;
  animation: blink 1s step-end infinite;
  color: #10a37f;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.loading-dots {
  display: flex;
  gap: 6px;
  align-items: center;
  height: 24px;
}
.loading-dots span {
  width: 6px;
  height: 6px;
  background-color: #9ca3af;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}
.loading-dots span:nth-child(1) { animation-delay: -0.32s; }
.loading-dots span:nth-child(2) { animation-delay: -0.16s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

/* ==================== 输入区域 ==================== */
.input-area {
  padding: 20px 24px 36px;
  background: linear-gradient(0deg, rgba(255, 255, 255, 0.9) 0%, transparent 100%);
  position: relative;
  display: flex;
  justify-content: center;
}

.input-inner {
  width: 100%;
  max-width: 800px;
}

.input-container {
  position: relative;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.03);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 24px;
  box-shadow:
    0 10px 40px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
  padding: 12px 20px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.input-container:focus-within {
  background: rgba(0, 0, 0, 0.02);
  border-color: rgba(16, 163, 127, 0.4);
  box-shadow:
    0 0 0 3px rgba(16, 163, 127, 0.15),
    0 20px 40px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
}

.input-container :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 8px 48px 8px 8px;
  font-size: 15px;
  line-height: 1.6;
  background: transparent;
  color: rgba(0, 0, 0, 0.87);
}
.input-container :deep(.el-textarea__inner:focus) { box-shadow: none; }
.input-container :deep(.el-textarea__inner::placeholder) {
  color: rgba(0, 0, 0, 0.35);
}

.send-btn {
  position: absolute;
  right: 14px;
  bottom: 14px;
  width: 40px;
  height: 40px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: linear-gradient(135deg, #10a37f, #059669);
  border-color: transparent;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 15px rgba(16, 163, 127, 0.3);
}

.send-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #0d9668, #047857);
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 8px 25px rgba(16, 163, 127, 0.4);
}
.send-btn:active {
  transform: translateY(0) scale(0.98);
}
.send-btn:disabled {
  background: rgba(0, 0, 0, 0.06);
  border-color: transparent;
  color: rgba(0, 0, 0, 0.25);
}

.input-tips {
  margin-top: 12px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.4);
  text-align: center;
}

.tip-highlight {
  background: rgba(16, 163, 127, 0.1);
  color: #059669;
  padding: 1px 4px;
  border-radius: 4px;
  font-weight: 500;
}

/* ==================== @ 提及自动补全列表 ==================== */
.mention-list {
  position: absolute;
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.15);
  z-index: 1000;
  margin-bottom: 8px;
  max-height: 200px;
  overflow-y: auto;
}

.mention-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background-color 0.15s;
  border-bottom: 1px solid #f3f4f6;
}

.mention-item:last-child {
  border-bottom: none;
}

.mention-item:hover,
.mention-item.active {
  background-color: #f0fdf4;
}

.mention-icon {
  font-size: 18px;
  width: 28px;
  text-align: center;
}

.mention-name {
  font-weight: 600;
  color: #374151;
  min-width: 60px;
}

.mention-desc {
  font-size: 12px;
  color: #9ca3af;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mention-empty {
  padding: 16px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}

/* ==================== 侧边小地图 ==================== */
.chat-minimap {
  position: absolute;
  right: 20px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  gap: 8px;
  z-index: 50;
  padding: 16px 8px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
  transition: all 0.3s ease;
}

.minimap-item {
  width: 18px;
  height: 3px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  opacity: 0.5;
}

.minimap-item:hover,
.minimap-item.active {
  opacity: 1;
  transform: scaleX(1.5);
}
.minimap-item.user { background: linear-gradient(90deg, #6366f1, #8b5cf6); align-self: flex-end; }
.minimap-item.assistant { background: linear-gradient(90deg, #10a37f, #34d399); align-self: flex-start; }

/* 响应式调整 */
@media (max-width: 1024px) {
  .message-item, .input-area {
    padding-left: 16px;
    padding-right: 16px;
  }
  .chat-minimap { display: none; }
}
</style>