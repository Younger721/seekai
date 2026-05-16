import { defineStore } from 'pinia'
import request from '../api/request'
import { ElMessage } from 'element-plus'

const REACT_PREFIX_REGEX = /(?:^|\n)(Thought|Action|Observation|Critique|Final Answer):\s*/g

function normalizeConversation(item) {
  return {
    ...item,
    id: item.id || item.conversationId,
    title: item.title || item.preview || 'New Chat'
  }
}

function normalizeRole(msg) {
  return (msg.type || msg.role || msg.sender || '').toLowerCase()
}

function parseReActFromFullText(text) {
  const source = text || ''
  if (!source) return { content: '', reactBlocks: [] }

  const typeMap = {
    Thought: 'thought',
    Action: 'action',
    Observation: 'observation',
    Critique: 'critique'
  }

  const blocks = []
  let finalContent = ''
  let cursor = 0
  let currentType = 'Final Answer'
  let match

  REACT_PREFIX_REGEX.lastIndex = 0
  while ((match = REACT_PREFIX_REGEX.exec(source)) !== null) {
    const chunk = source.slice(cursor, match.index)
    if (chunk) {
      if (currentType === 'Final Answer') {
        finalContent += chunk
      } else {
        blocks.push({ type: typeMap[currentType], content: chunk.trim() })
      }
    }

    currentType = match[1]
    cursor = REACT_PREFIX_REGEX.lastIndex
  }

  const tail = source.slice(cursor)
  if (tail) {
    if (currentType === 'Final Answer') {
      finalContent += tail
    } else {
      blocks.push({ type: typeMap[currentType], content: tail.trim() })
    }
  }

  finalContent = finalContent.replace(/^\s+/, '')
  if (!finalContent.trim() && blocks.length === 0) {
    finalContent = source
  }

  return { content: finalContent, reactBlocks: blocks.filter(b => b.content) }
}

export const useChatStore = defineStore('chat', {
  state: () => ({
    conversations: [],
    currentConversationId: null,
    messages: [],
    loading: false,
    streaming: false,
    agents: [],
    selectedAgent: null
  }),

  actions: {
    async fetchAgents() {
      try {
        const data = await request.get('/agent/smart/agents')
        this.agents = data || []
        if (!this.selectedAgent) {
          this.selectedAgent = 'auto'
        }
      } catch (error) {
        console.error('Failed to fetch agents:', error)
        ElMessage.error('获取 Agent 列表失败')
      }
    },

    selectAgent(agentName) {
      this.selectedAgent = agentName
    },

    async fetchConversations() {
      try {
        const data = await request.get('/conversations')
        this.conversations = (data || []).map(normalizeConversation)
      } catch (error) {
        console.error('Failed to fetch conversations:', error)
        ElMessage.error('获取会话列表失败')
      }
    },

    async createConversation() {
      try {
        const data = await request.post('/conversations')
        const newConversation = {
          id: data.id || data.conversationId,
          title: data.title || 'New Chat',
          createdAt: data.createdAt
        }
        this.conversations.unshift(newConversation)
        this.currentConversationId = newConversation.id
        this.messages = []
        ElMessage.success('创建成功')
        return newConversation.id
      } catch (error) {
        console.error('Failed to create conversation:', error)
        ElMessage.error('创建会话失败')
        return null
      }
    },

    async fetchConversationDetail(id) {
      try {
        this.loading = true
        this.currentConversationId = id
        const data = await request.get(`/conversations/${id}`)

        const rawMessages = data.messages || []
        this.messages = rawMessages
          .filter(msg => normalizeRole(msg) !== 'system')
          .map(msg => {
            const role = normalizeRole(msg)
            const isAssistant = role === 'assistant'
            const originalContent = msg.content || ''
            const parsed = isAssistant
              ? parseReActFromFullText(originalContent)
              : { content: originalContent, reactBlocks: [] }

            return {
              ...msg,
              type: isAssistant ? 'assistant' : 'user',
              content: parsed.content && parsed.content.trim() ? parsed.content : originalContent,
              reactBlocks: parsed.reactBlocks,
              agentName: msg.agentName || null,
              loading: false
            }
          })
      } catch (error) {
        console.error('Failed to fetch conversation detail:', error)
        ElMessage.error('获取会话详情失败')
      } finally {
        this.loading = false
      }
    },

    async deleteConversation(id) {
      try {
        await request.delete(`/conversations/${id}`)
        this.conversations = this.conversations.filter(c => c.id !== id)
        if (this.currentConversationId === id) {
          this.currentConversationId = null
          this.messages = []
        }
        ElMessage.success('删除成功')
      } catch (error) {
        console.error('Failed to delete conversation:', error)
        ElMessage.error('删除会话失败')
      }
    },

    async clearConversationMessages(id) {
      try {
        await request.delete(`/conversations/${id}/messages`)
        this.messages = []
        ElMessage.success('消息已清空')
      } catch (error) {
        console.error('Failed to clear messages:', error)
        ElMessage.error('清空消息失败')
      }
    },

    async sendStreamMessage(message) {
      if (!message.trim() || this.streaming) return

      const conversationId = this.currentConversationId

      const userMsg = {
        type: 'user',
        content: message,
        timestamp: new Date(),
        loading: false,
        reactBlocks: []
      }
      this.messages.push(userMsg)

      const aiMsg = {
        type: 'assistant',
        content: '',
        loading: true,
        timestamp: new Date(),
        reactBlocks: []
      }
      this.messages.push(aiMsg)
      const aiMsgIndex = this.messages.length - 1

      this.streaming = true
      let fullResponse = ''

      const applyChunk = (chunk) => {
        fullResponse += chunk
        const parsed = parseReActFromFullText(fullResponse)
        const msg = this.messages[aiMsgIndex]
        if (!msg) return
        msg.reactBlocks = parsed.reactBlocks
        msg.content = parsed.content && parsed.content.trim() ? parsed.content : fullResponse
      }

      try {
        let url
        if (this.selectedAgent && this.selectedAgent !== 'auto') {
          url = `/api/agent/${this.selectedAgent}/stream?message=${encodeURIComponent(message)}`
        } else {
          url = `/api/agent/smart/stream?message=${encodeURIComponent(message)}`
        }
        if (conversationId) {
          url += `&conversationId=${encodeURIComponent(conversationId)}`
        }

        const response = await fetch(url, {
          method: 'GET',
          credentials: 'include'
        })

        if (!response.ok || !response.body) {
          throw new Error(`Network response was not ok: ${response.status}`)
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder('utf-8')
        let buffer = ''

        this.messages[aiMsgIndex].loading = false

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })

          if (!buffer.includes('data:')) {
            applyChunk(buffer.replace(/\\n/g, '\n').replace(/\\"/g, '"'))
            buffer = ''
            continue
          }

          let newlineIndex
          while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
            let line = buffer.slice(0, newlineIndex)
            buffer = buffer.slice(newlineIndex + 1)
            line = line.replace(/\r$/, '')

            if (line.startsWith('data:')) {
              const content = line.replace(/^data:\s?/, '')
              if (content && content !== '[DONE]') {
                applyChunk(content.replace(/\\n/g, '\n').replace(/\\"/g, '"'))
              }
            }
          }
        }

        if (buffer.trim()) {
          applyChunk(buffer.replace(/^data:\s?/, '').replace(/\\n/g, '\n').replace(/\\"/g, '"'))
        }
      } catch (error) {
        console.error('Streaming error:', error)
        if (this.messages[aiMsgIndex]) {
          this.messages[aiMsgIndex].content = 'Error: ' + error.message
          this.messages[aiMsgIndex].loading = false
        }
        ElMessage.error('消息发送失败')
      } finally {
        this.streaming = false
        this.fetchConversations()

        // 防止“回复闪现后消失”：如果回读返回旧数据，保留本地结果
        if (this.currentConversationId) {
          const localSnapshot = JSON.parse(JSON.stringify(this.messages))
          setTimeout(async () => {
            const previousLoading = this.loading
            try {
              await this.fetchConversationDetail(this.currentConversationId)
              if (this.messages.length < localSnapshot.length) {
                this.messages = localSnapshot
              }
            } catch (e) {
              console.error('Failed to sync final message format:', e)
            } finally {
              this.loading = previousLoading
            }
          }, 1200)
        }
      }
    }
  }
})
