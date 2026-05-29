<template>
  <div class="chat-window">
    <div class="chat-header">
      <div class="title-info">
        <h3>{{ currentTitle }}</h3>
        <span v-if="currentConversationId" class="id-badge">#{{ currentConversationId }}</span>
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
        <div class="composer-toolbar">
          <div class="toolbar-group">
            <button type="button" class="toolbar-chip">Deep Think</button>
            <button type="button" class="toolbar-chip">Tools</button>
            <span class="agent-chip">{{ currentAgentLabel }}</span>
          </div>
          <el-button v-if="currentConversationId" link type="danger" plain @click="clearMessages">
            <el-icon class="el-icon--left"><Delete /></el-icon>
            Clear History
          </el-button>
        </div>

        <div class="input-container" style="position: relative;">
          <el-input
            ref="textareaRef"
            v-model="inputMsg"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 8 }"
            placeholder="有问题，尽管问，Shift + Enter 换行"
            resize="none"
            @keydown.enter.prevent="handleSend"
            @input="handleInputChange"
            @keydown="handleKeydown"
            :disabled="!currentConversationId || streaming"
          />

          <!-- @ 鎻愬強鑷姩琛ュ叏鍒楄〃 -->
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
              娌℃湁鍖归厤鐨?Agent
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
          Enter 发送，Shift + Enter 换行，输入 <span class="tip-highlight">@</span> 提及 Agent
        </div>
      </div>
    </div>
    
    <div class="chat-minimap" v-if="userMinimapItems.length > 0">
      <el-tooltip
        v-for="item in userMinimapItems"
        :key="'minimap-' + item.index"
        :content="getMinimapPreview(item.message.content)"
        placement="left"
        :show-after="100"
        effect="dark"
      >
        <div 
          class="minimap-item"
          :class="{ active: activeUserMessageIndex === item.index }"
          @click="scrollToMessage(item.index)"
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

const chatStore = useChatStore()
const messagesShallow = shallowRef([])
const streamingShallow = shallowRef(false)

watch(() => chatStore.messages, (newVal) => {
  messagesShallow.value = newVal
}, { immediate: true })

watch(() => chatStore.streaming, (newVal) => {
  streamingShallow.value = newVal
}, { immediate: true })

// Markdown 娓叉煋缂撳瓨 (浣跨敤 shallowRef 閬垮厤娣卞害鍝嶅簲寮?
const messageRenderCache = shallowRef(new Map())
let isRenderingECharts = false

// 鑺傛祦杈呭姪鍑芥暟
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

// 鍥捐〃瀹炰緥绠＄悊
const chartInstances = ref(new Map())

// ============ Markdown-it 閰嶇疆涓庤嚜瀹氫箟娓叉煋 ============
const md = new MarkdownIt({
  html: true,
  breaks: true,
  linkify: true,
  highlight: function (str, lang) {
    if (lang === 'echarts') {
      const chartId = `chart-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
      return `<div class="echarts-container chart-container" id="${chartId}" data-chart-config="${encodeURIComponent(str)}"></div>`
    }
    
    // 鏅€氫唬鐮佸潡澶勭悊
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
            <span>澶嶅埗</span>
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
const userMinimapItems = computed(() => {
  return messages.value
    .map((message, index) => ({ message, index }))
    .filter(item => item.message.type === 'user')
})
const activeUserMessageIndex = computed(() => {
  if (activeMessageIndex.value < 0) {
    return userMinimapItems.value[0]?.index ?? -1
  }

  let nearestUserIndex = -1
  for (const item of userMinimapItems.value) {
    if (item.index <= activeMessageIndex.value) {
      nearestUserIndex = item.index
    } else {
      break
    }
  }

  return nearestUserIndex !== -1
    ? nearestUserIndex
    : userMinimapItems.value[0]?.index ?? -1
})
const currentAgent = computed(() => chatStore.selectedAgent || 'auto')
const agents = computed(() => chatStore.agents)
const currentAgentLabel = computed(() => {
  if (!currentAgent.value || currentAgent.value === 'auto') return 'Auto'
  const agent = agents.value.find(item => item.name === currentAgent.value)
  return agent ? getAgentAlias(agent.name) : currentAgent.value
})

const textareaRef = ref(null)
const showMentionList = ref(false)
const mentionIndex = ref(0)
const mentionQuery = ref('')
const mentionListStyle = ref({})

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

const getAgentEmoji = (name) => {
  const emojiMap = {
    'CRAWLER_AGENT': '🕷️',
    'SEARCH_AGENT': '🔎',
    'CHART_AGENT': '📊',
    'DATA_AGENT': '📈',
    'CODE_EXPERT': '💻',
    'AUTO_CODER_AGENT': '⚙️',
    'DOCUMENT_AGENT': '📄',
    'GENERAL_HELPER': '🤖',
    'MULTI_STEP_AGENT': '🧭'
  }
  return emojiMap[name] || '🤖'
}

// 杩囨护鍚庣殑 Agent 鍒楄〃
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

// 澶勭悊杈撳叆鍙樺寲
const handleInputChange = () => {
  const text = inputMsg.value
  const cursorPos = getCursorPosition()

  const lastAtIndex = text.lastIndexOf('@', cursorPos - 1)

  if (lastAtIndex !== -1) {
    const textAfterAt = text.slice(lastAtIndex + 1, cursorPos)
    const hasSpace = textAfterAt.includes(' ')

    if (!hasSpace) {
      mentionQuery.value = textAfterAt
      showMentionList.value = true
      mentionIndex.value = 0

      updateMentionListPosition(lastAtIndex)
      return
    }
  }

  showMentionList.value = false
  mentionQuery.value = ''
}

// 鑾峰彇鍏夋爣浣嶇疆
const getCursorPosition = () => {
  const textarea = textareaRef.value?.textarea
  if (!textarea) return 0
  return textarea.selectionStart
}

// 鏇存柊琛ュ叏鍒楄〃浣嶇疆
const updateMentionListPosition = (atIndex) => {
  // 绠€鍗曞鐞嗭細鏄剧ず鍦ㄨ緭鍏ユ涓嬫柟
  mentionListStyle.value = {
    bottom: '100%',
    left: '0',
    right: '0',
    maxHeight: '200px',
    overflowY: 'auto'
  }
}

// 澶勭悊閿洏浜嬩欢
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
    const text = inputMsg.value
    const cursorPos = getCursorPosition()
    const lastAtIndex = text.lastIndexOf('@', cursorPos - 1)
    if (lastAtIndex === -1 || cursorPos <= lastAtIndex + 1) {
      showMentionList.value = false
    }
  }
}

// 閫夋嫨 Agent
const selectMentionAgent = (agent) => {
  const text = inputMsg.value
  const cursorPos = getCursorPosition()
  const lastAtIndex = text.lastIndexOf('@', cursorPos - 1)

  if (lastAtIndex !== -1) {
    // 鏇挎崲 @xxx 涓?@AgentName
    const before = text.slice(0, lastAtIndex)
    const after = text.slice(cursorPos)
    const alias = getAgentAlias(agent.name)

    inputMsg.value = before + '@' + alias + ' ' + after

    // 鍏抽棴琛ュ叏鍒楄〃
    showMentionList.value = false

    // 璁╄緭鍏ユ閲嶆柊鑾峰彇鐒︾偣
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

// ============ ECharts 鍔ㄦ€佹覆鏌撻€昏緫 ============
const renderECharts = async () => {
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
        console.error('ECharts 娓叉煋澶辫触:', e)
        container.innerHTML = `<div style="color: #ef4444; padding: 20px; text-align: center; background: #fef2f2; border-radius: 8px; border: 1px solid #fee2e2;">馃搳 鍥捐〃閰嶇疆瑙ｆ瀽澶辫触<br><small>${e.message}</small></div>`
      }
    })
  } catch (e) {
    console.error('ECharts 娓叉煋閿欒:', e)
  } finally {
    isRenderingECharts = false
  }
}

// ============ 婊氬姩涓庤瀵熻€呴€昏緫 ============
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

// ============ 鐢熷懡鍛ㄦ湡涓庣洃鍚?============
watch(() => messages.value.length, () => {
  initIntersectionObserver()
})

// 澶嶅埗浠ｇ爜鍔熻兘锛堜簨浠跺鎵橈級
const handleCopyCode = (e) => {
  const btn = e.target.closest('.copy-btn')
  if (!btn) return

  const codeStr = decodeURIComponent(btn.dataset.code || '')
  if (!codeStr) return

  navigator.clipboard.writeText(codeStr).then(() => {
    const originalHTML = btn.innerHTML
    btn.innerHTML = '<span class="copied-text">鉁?宸插鍒?/span>'
    btn.classList.add('copied')
    setTimeout(() => {
      btn.innerHTML = originalHTML
      btn.classList.remove('copied')
    }, 2000)
  }).catch(err => {
    console.error('澶嶅埗澶辫触', err)
  })
}

onMounted(() => {
  // 娣诲姞澶嶅埗浠ｇ爜浜嬩欢鐩戝惉
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
      // 绉婚櫎 characterData: true锛岃繖浼氬鑷翠弗閲嶇殑鎬ц兘闂
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
  // 绉婚櫎澶嶅埗浠ｇ爜浜嬩欢鐩戝惉
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

// 浼樺寲锛氱Щ闄?deep watch锛屾敼鐢?shallow 鍝嶅簲寮?+ 鑺傛祦
const handleMessagesChange = throttle(async () => {
  await nextTick()
  if (!isUserScrolling.value && messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
  // 寤惰繜娓叉煋 ECharts锛岄伩鍏嶉绻佸垵濮嬪寲
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

// ============ 鍙戦€佷笌澶勭悊娑堟伅 ============
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
    'critique': '自我反思'
  }
  return map[type] || type
}
</script>

<style scoped>
/* ==================== 鍩虹甯冨眬 ==================== */
.chat-window {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #ffffff;
  position: relative;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

/* ==================== 澶撮儴 ==================== */
.chat-header {
  height: 56px;
  padding: 0 24px;
  border-bottom: 1px solid #ece8e2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  background: #faf8f5;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
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
  color: #57534e;
  background: #f5f2ec;
  border: 1px solid #e7e1d8;
  padding: 2px 8px;
  border-radius: 6px;
  font-weight: 500;
  flex-shrink: 0;
}

/* ==================== 娑堟伅鍒楄〃涓庢潯鐩?==================== */
.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: scroll;
  padding: 20px 0;
  scroll-behavior: smooth;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: #bcb5aa #f4f1eb;
}

.message-list::-webkit-scrollbar {
  width: 10px;
}

.message-list::-webkit-scrollbar-track {
  background: #f4f1eb;
}

.message-list::-webkit-scrollbar-thumb {
  background: #bcb5aa;
  border-radius: 999px;
  border: 2px solid #f4f1eb;
}

.message-list::-webkit-scrollbar-thumb:hover {
  background: #9f9688;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(0, 0, 0, 0.4);
}

/* 绌虹姸鎬佺幓鐠冨崱鐗?*/
.empty-state :deep(.el-empty__description) {
  color: rgba(0, 0, 0, 0.5);
}

.message-item {
  display: flex;
  justify-content: flex-start;
  padding: 32px 24px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.message-item.assistant {
  background: #f8f6f2;
  border-top: 1px solid #efebe5;
  border-bottom: 1px solid #efebe5;
}

/* 娑堟伅椤瑰叆鍦哄姩鐢?*/
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

/* ==================== 鎬濊€冮摼璺?(ReAct Blocks) ==================== */
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

/* 鎬濊€冨潡棰滆壊瀹氬埗 */
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

/* ==================== Markdown 閫氱敤鎺掔増 ==================== */
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

/* ==================== 琛ㄦ牸娓叉煋鏍峰紡 ==================== */
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

/* ==================== 瀹岀編琛旀帴鐨勪唬鐮佸潡鏍峰紡 ==================== */
.markdown-body :deep(.custom-code-block) {
  /* 瀹瑰櫒缁熶竴鎺у埗鍦嗚鍜岃竟妗嗭紝瑙ｅ喅鑴辫妭闂 */
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
  border-bottom: 1px solid #d0d7de; /* 鏄庣‘鐨勫垎鍓茬嚎 */
}

.markdown-body :deep(.mac-dots) {
  display: flex;
  gap: 6px;
  width: 60px; /* 鍥哄畾瀹藉害浠ュ眳涓瑷€鏍囩 */
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
  /* 鍖呰灞傜‘淇濊儗鏅壊鍜屾粴鍔ㄨ〃鐜颁竴鑷?*/
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

/* ==================== 鍐呰仈浠ｇ爜 (涓庝唬鐮佸潡涓ユ牸鍖哄垎) ==================== */
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

/* ==================== ECharts 瀹瑰櫒鏍峰紡 ==================== */
.markdown-body :deep(.echarts-container) {
  transition: all 0.3s ease;
  min-width: 300px !important;
  min-height: 300px !important;
  width: 100% !important;
  height: 350px !important; /* 閫傚綋澧炲姞楂樺害鏄惧緱鏇村ぇ姘?*/
  box-sizing: border-box;
  margin: 20px 0;
  border-radius: 12px;
  border: 1px solid #e2e8f0 !important;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
  background: linear-gradient(135deg, #fafafa 0%, #ffffff 100%);
  padding: 10px;
}

/* ==================== 鍔ㄧ敾涓庢潅椤?==================== */
.markdown-body.streaming :deep(*:last-child)::after {
  content: '|';
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

/* ==================== 杈撳叆鍖哄煙 ==================== */
.input-area {
  padding: 12px 24px 20px;
  background: #f7f4ee;
  position: relative;
  display: flex;
  justify-content: center;
  border-top: 1px solid #f0ece6;
}

.input-inner {
  width: 100%;
  max-width: 800px;
}

.composer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-chip {
  border: 1px solid #ddd8cf;
  background: #f8f6f2;
  color: #44403c;
  border-radius: 999px;
  font-size: 13px;
  height: 30px;
  padding: 0 12px;
  cursor: pointer;
}

.toolbar-chip:hover {
  background: #f1ede7;
}

.agent-chip {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid #d9d4ca;
  background: #faf7f2;
  color: #57534e;
  font-size: 13px;
}

.input-container {
  position: relative;
  display: flex;
  align-items: flex-end;
  background: #f9f6f1;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  border: 1px solid #ddd8cf;
  border-radius: 24px;
  box-shadow: none;
  padding: 12px 20px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.input-container:focus-within {
  background: #fcfaf6;
  border-color: #bfb8ad;
  box-shadow: 0 0 0 3px rgba(87, 83, 78, 0.08);
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
  background: #171717;
  border-color: transparent;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: none;
}

.send-btn:hover:not(:disabled) {
  background: #262626;
  transform: translateY(-1px) scale(1.01);
  box-shadow: none;
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
  background: #ede7de;
  color: #44403c;
  padding: 1px 4px;
  border-radius: 4px;
  font-weight: 500;
}

/* ==================== @ 鎻愬強鑷姩琛ュ叏鍒楄〃 ==================== */
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
  background-color: #f5f2ec;
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

/* ==================== 渚ц竟灏忓湴鍥?==================== */
.chat-minimap {
  position: absolute;
  right: 18px;
  top: 88px;
  bottom: 150px;
  z-index: 18;
  width: 82px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 9px;
  padding: 10px 8px;
  overflow-y: auto;
  overflow-x: hidden;
  pointer-events: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(234, 88, 12, 0.45) transparent;
}

.chat-minimap::-webkit-scrollbar {
  width: 4px;
}

.chat-minimap::-webkit-scrollbar-track {
  background: transparent;
}

.chat-minimap::-webkit-scrollbar-thumb {
  background: rgba(234, 88, 12, 0.45);
  border-radius: 999px;
}

.minimap-item {
  width: 54px;
  height: 11px;
  flex: 0 0 11px;
  border: 1px solid rgba(251, 146, 60, 0.62);
  border-radius: 3px;
  background: linear-gradient(90deg, #fb923c, #f97316);
  box-shadow: 0 1px 3px rgba(194, 65, 12, 0.18);
  cursor: pointer;
  opacity: 0.72;
  transition: width 0.18s ease, opacity 0.18s ease, background-color 0.18s ease, box-shadow 0.18s ease;
}

.minimap-item:hover,
.minimap-item.active {
  opacity: 1;
  width: 64px;
  background: linear-gradient(90deg, #f97316, #ea580c);
  box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.2), 0 4px 10px rgba(194, 65, 12, 0.24);
}

/* 鍝嶅簲寮忚皟鏁?*/
@media (max-width: 1024px) {
  .message-item, .input-area {
    padding-left: 16px;
    padding-right: 16px;
  }
  .chat-minimap { display: none; }
}

/* ==================== Luxe glass chat overrides ==================== */
.chat-window {
  --luxe-ink: #23221f;
  --luxe-text: #4a4844;
  --luxe-muted: #8e8a80;
  --luxe-gold: #c5a880;
  --luxe-line: rgba(35, 34, 31, 0.08);
  --luxe-glass: rgba(255, 254, 252, 0.42);
  --luxe-white: rgba(255, 255, 255, 0.82);

  overflow: hidden;
  color: var(--luxe-ink);
  background: transparent;
}

.chat-header {
  height: 64px;
  padding: 0 92px 0 44px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.34), rgba(255, 255, 255, 0));
  border-bottom: 1px solid rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(26px) saturate(130%);
  -webkit-backdrop-filter: blur(26px) saturate(130%);
}

.title-info h3 {
  color: var(--luxe-ink);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.id-badge {
  color: var(--luxe-muted);
  background: rgba(255, 255, 255, 0.52);
  border: 1px solid rgba(142, 135, 121, 0.14);
  border-radius: 999px;
}

.message-list {
  padding: 34px 100px 178px;
  overflow-y: auto;
  scrollbar-width: none;
  scrollbar-gutter: auto;
  scrollbar-color: transparent transparent;
}

.message-list::-webkit-scrollbar {
  width: 0;
}

.message-list::-webkit-scrollbar-track,
.message-list::-webkit-scrollbar-thumb {
  background: transparent;
  border: none;
}

.empty-state {
  min-height: calc(100% - 80px);
  color: var(--luxe-muted);
}

.empty-state :deep(.el-empty) {
  padding: 42px 48px;
  background: rgba(255, 255, 255, 0.42);
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 24px;
  box-shadow: 0 24px 60px rgba(142, 135, 121, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(30px) saturate(135%);
  -webkit-backdrop-filter: blur(30px) saturate(135%);
}

.empty-state :deep(.el-empty__description) {
  color: var(--luxe-muted);
}

.message-item {
  justify-content: center;
  padding: 0 0 54px;
  background: transparent;
  border: none;
  animation: elegantSlideUp 0.7s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

.message-item.assistant {
  background: transparent;
  border: none;
}

@keyframes elegantSlideUp {
  from {
    opacity: 0;
    transform: translateY(24px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-inner {
  width: min(100%, 820px);
  max-width: 820px;
  gap: 28px;
}

.avatar {
  flex: 0 0 auto;
}

.avatar .el-avatar {
  width: 40px !important;
  height: 40px !important;
  color: #ffffff;
  font-size: 12px;
  font-weight: 700;
  background: var(--luxe-ink);
  border: 1px solid rgba(255, 255, 255, 0.84);
  border-radius: 14px;
  box-shadow: 0 10px 24px rgba(35, 34, 31, 0.14);
}

.message-item.user .avatar .el-avatar {
  color: var(--luxe-ink);
  background: #ffffff;
  border-color: rgba(142, 135, 121, 0.16);
  box-shadow: 0 8px 22px rgba(142, 135, 121, 0.08);
}

.content-wrapper {
  max-width: none;
}

.role-name {
  margin-bottom: 10px;
  color: var(--luxe-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.agent-tag {
  color: #7b6748;
  background: rgba(197, 168, 128, 0.13);
  border: 1px solid rgba(197, 168, 128, 0.22);
  border-radius: 999px;
}

.markdown-body {
  color: var(--luxe-ink);
  font-size: 16px;
  line-height: 1.82;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  color: var(--luxe-ink);
}

.markdown-body :deep(h2) {
  border-bottom-color: var(--luxe-line);
}

.markdown-body :deep(blockquote) {
  color: var(--luxe-text);
  background: rgba(255, 255, 255, 0.42);
  border-left-color: var(--luxe-gold);
  border-radius: 0 12px 12px 0;
}

.react-block {
  overflow: hidden;
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 16px;
  box-shadow: 0 16px 34px rgba(142, 135, 121, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(22px) saturate(130%);
  -webkit-backdrop-filter: blur(22px) saturate(130%);
}

.react-block-header {
  color: var(--luxe-muted);
  background: rgba(255, 255, 255, 0.34);
  border-bottom: 1px solid rgba(142, 135, 121, 0.08);
}

.react-block-content {
  color: var(--luxe-text);
  background: transparent;
}

.react-action .react-block-header,
.react-observation .react-block-header,
.react-critique .react-block-header {
  color: #7b6748;
  background: rgba(197, 168, 128, 0.11);
  border-bottom-color: rgba(197, 168, 128, 0.14);
}

.markdown-body :deep(table) {
  background: rgba(255, 255, 255, 0.6);
  box-shadow: 0 0 0 1px rgba(142, 135, 121, 0.14);
}

.markdown-body :deep(th) {
  background: rgba(197, 168, 128, 0.11);
}

.markdown-body :deep(.custom-code-block) {
  background: rgba(35, 34, 31, 0.94);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 16px;
  box-shadow: 0 18px 42px rgba(35, 34, 31, 0.16);
}

.markdown-body :deep(.code-header) {
  background: rgba(255, 255, 255, 0.06);
  border-bottom-color: rgba(255, 255, 255, 0.12);
}

.markdown-body :deep(.code-lang),
.markdown-body :deep(.copy-btn) {
  color: rgba(255, 255, 255, 0.68);
}

.markdown-body :deep(.copy-btn:hover) {
  color: #ffffff;
}

.markdown-body :deep(.code-body-wrapper),
.markdown-body :deep(.custom-code-block pre) {
  background: transparent !important;
}

.markdown-body :deep(pre code) {
  color: rgba(255, 255, 255, 0.88);
}

.markdown-body :deep(:not(pre) > code) {
  color: #7b6748;
  background: rgba(197, 168, 128, 0.14);
  border-radius: 6px;
}

.markdown-body :deep(.echarts-container) {
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(255, 255, 255, 0.78) !important;
  border-radius: 18px;
  box-shadow: 0 18px 42px rgba(142, 135, 121, 0.1);
}

.markdown-body.streaming :deep(*:last-child)::after {
  color: var(--luxe-gold);
}

.loading-dots {
  height: 28px;
  gap: 6px;
}

.loading-dots span {
  width: 3px;
  height: 14px;
  background: var(--luxe-muted);
  border-radius: 99px;
  animation: pulseHeight 1.2s infinite ease-in-out alternate;
}

.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes pulseHeight {
  0% { transform: scaleY(0.45); opacity: 0.35; }
  100% { transform: scaleY(1.28); opacity: 1; background: var(--luxe-gold); }
}

.input-area {
  position: absolute;
  right: 0;
  bottom: 34px;
  left: 0;
  z-index: 30;
  padding: 0 112px;
  background: transparent;
  border-top: none;
  pointer-events: none;
}

.input-inner {
  width: min(100%, 780px);
  max-width: 780px;
  padding: 12px 16px 14px;
  pointer-events: auto;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.54) 0%, rgba(253, 252, 248, 0.3) 100%);
  border: 1px solid rgba(255, 255, 255, 0.86);
  border-radius: 26px;
  box-shadow:
    0 35px 70px rgba(130, 122, 105, 0.15),
    0 10px 20px rgba(130, 122, 105, 0.05),
    inset 0 2px 5px rgba(255, 255, 255, 0.9),
    inset 0 -2px 6px rgba(142, 135, 121, 0.04);
  backdrop-filter: blur(35px) saturate(160%);
  -webkit-backdrop-filter: blur(35px) saturate(160%);
  transition: all 0.46s cubic-bezier(0.16, 1, 0.3, 1);
}

.input-inner:focus-within {
  transform: scale(1.01);
  box-shadow:
    0 45px 90px rgba(114, 106, 90, 0.2),
    0 12px 30px rgba(114, 106, 90, 0.08),
    inset 0 2px 4px rgba(255, 255, 255, 1);
}

.composer-toolbar {
  margin: 0 0 8px;
  padding: 0 4px;
}

.toolbar-chip,
.agent-chip {
  height: 28px;
  color: var(--luxe-text);
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(142, 135, 121, 0.12);
  border-radius: 999px;
}

.toolbar-chip:hover {
  background: #ffffff;
}

.composer-toolbar :deep(.el-button) {
  color: var(--luxe-muted);
}

.input-container {
  min-height: 54px;
  padding: 4px 58px 4px 10px;
  background: transparent;
  border: none;
  border-radius: 0;
  box-shadow: none;
}

.input-container:focus-within {
  background: transparent;
  border-color: transparent;
  box-shadow: none;
}

.input-container :deep(.el-textarea__inner) {
  min-height: 44px !important;
  padding: 10px 0;
  color: var(--luxe-ink);
  font-size: 15.5px;
  line-height: 1.6;
}

.input-container :deep(.el-textarea__inner::placeholder) {
  color: #b2aca0;
}

.send-btn {
  right: 4px;
  bottom: 8px;
  width: 42px;
  height: 42px;
  color: #ffffff;
  background: var(--luxe-ink);
  border: none;
  border-radius: 16px;
  box-shadow: 0 8px 20px rgba(35, 34, 31, 0.15);
}

.send-btn:hover:not(:disabled) {
  background: var(--luxe-gold);
  transform: scale(1.05) translateY(-1px);
  box-shadow: 0 8px 22px rgba(197, 168, 128, 0.3);
}

.send-btn:disabled {
  color: rgba(35, 34, 31, 0.24);
  background: rgba(35, 34, 31, 0.07);
}

.input-tips {
  margin-top: 8px;
  color: rgba(35, 34, 31, 0.38);
}

.tip-highlight {
  color: var(--luxe-ink);
  background: rgba(197, 168, 128, 0.16);
}

.mention-list {
  bottom: calc(100% + 12px) !important;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(255, 255, 255, 0.86);
  border-radius: 18px;
  box-shadow: 0 20px 45px rgba(142, 135, 121, 0.16);
  backdrop-filter: blur(24px) saturate(145%);
  -webkit-backdrop-filter: blur(24px) saturate(145%);
}

.mention-item {
  border-bottom-color: rgba(142, 135, 121, 0.08);
}

.mention-item:hover,
.mention-item.active {
  background: rgba(197, 168, 128, 0.13);
}

.mention-name {
  color: var(--luxe-ink);
}

.mention-desc,
.mention-empty {
  color: var(--luxe-muted);
}

.chat-minimap {
  top: 50%;
  right: 28px;
  bottom: auto;
  width: 34px;
  max-height: min(58vh, 420px);
  padding: 14px 8px;
  align-items: center;
  gap: 14px;
  overflow: visible;
  transform: translateY(-50%);
  scrollbar-width: none;
}

.chat-minimap::before {
  position: absolute;
  top: 22px;
  bottom: 22px;
  left: 50%;
  z-index: -1;
  width: 1px;
  content: '';
  background: rgba(35, 34, 31, 0.07);
  transform: translateX(-50%);
}

.chat-minimap::-webkit-scrollbar {
  width: 0;
}

.minimap-item {
  width: 8px;
  height: 8px;
  flex: 0 0 8px;
  background: rgba(35, 34, 31, 0.16);
  border: none;
  border-radius: 50%;
  box-shadow: none;
  opacity: 1;
  transition: all 0.45s cubic-bezier(0.16, 1, 0.3, 1);
}

.minimap-item:hover {
  width: 11px;
  height: 11px;
  background: var(--luxe-ink);
  transform: scale(1.2);
}

.minimap-item.active {
  width: 8px;
  height: 28px;
  background: var(--luxe-ink);
  border-radius: 6px;
  box-shadow: 0 4px 10px rgba(35, 34, 31, 0.15);
}

@media (max-width: 1080px) {
  .chat-header {
    padding: 0 28px 0 68px;
  }

  .message-list {
    padding: 34px 28px 178px;
  }

  .input-area {
    bottom: 22px;
    padding: 0 24px;
  }
}

@media (max-width: 720px) {
  .chat-header {
    height: 58px;
    padding: 0 18px 0 66px;
  }

  .title-info h3 {
    font-size: 13px;
  }

  .id-badge {
    display: none;
  }

  .message-list {
    padding: 24px 16px 186px;
  }

  .message-item {
    padding-bottom: 38px;
  }

  .message-inner {
    gap: 14px;
  }

  .avatar .el-avatar {
    width: 34px !important;
    height: 34px !important;
    border-radius: 12px;
  }

  .markdown-body {
    font-size: 15px;
  }

  .input-area {
    bottom: 12px;
    padding: 0 12px;
  }

  .input-inner {
    padding: 10px 12px 12px;
    border-radius: 22px;
  }

  .composer-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .toolbar-group {
    width: 100%;
  }

  .input-tips {
    display: none;
  }

  .chat-minimap {
    display: none;
  }
}
</style>

