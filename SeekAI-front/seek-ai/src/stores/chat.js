import { defineStore } from 'pinia'
import request from '../api/request'
import { ElMessage } from 'element-plus'

export const useChatStore = defineStore('chat', {
  state: () => ({
    conversations: [],
    currentConversationId: null,
    messages: [],
    loading: false,
    streaming: false,
    agents: [],              // 可用 Agent 列表
    selectedAgent: null      // 当前选择的 Agent
  }),
  
  actions: {
    // 获取可用 Agent 列表
    async fetchAgents() {
      try {
        const data = await request.get('/agent/smart/agents')
        this.agents = data || []
        // 默认选择第一个 Agent
        if (this.agents.length > 0 && !this.selectedAgent) {
          this.selectedAgent = this.agents[0].name
        }
      } catch (error) {
        console.error('Failed to fetch agents:', error)
        ElMessage.error('获取 Agent 列表失败')
      }
    },

    // 选择 Agent
    selectAgent(agentName) {
      this.selectedAgent = agentName
    },

    // 获取所有会话
    async fetchConversations() {
      try {
        const data = await request.get('/conversations')
        // request.js 拦截器已经提取了 res.data，所以这里直接用 data
        this.conversations = (data || []).map(item => ({
          ...item,
          id: item.id || item.conversationId,
          title: item.title || item.preview || 'New Chat'
        }))
      } catch (error) {
        console.error('Failed to fetch conversations:', error)
        ElMessage.error('获取会话列表失败')
      }
    },
    
    // 创建新会话
    async createConversation() {
      try {
        // 如果后端依然保留了空 POST /api/conversations 也可以用
        const data = await request.post('/conversations')
        const newConversation = {
          id: data.id || data.conversationId, 
          title: data.title || 'New Chat',
          createdAt: data.createdAt,
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
    
    // 获取会话详情（包含消息）
    async fetchConversationDetail(id) {
      try {
        this.loading = true
        this.currentConversationId = id
        const data = await request.get(`/conversations/${id}`)
        
        // 过滤掉 SYSTEM 类型的消息，并将所有的角色统一转换为小写，方便前端组件统一判断
        const rawMessages = data.messages || []
        
        // 解析完整的消息文本中的 ReAct 区块（针对历史记录加载）
        const parseReActFromFullText = (text) => {
          if (!text) return { content: '', reactBlocks: [] }
          
          const blocks = []
          let finalContent = ''
          
          // 使用正则精准匹配：匹配出现在行首（或文本开头）的 ReAct 标识符
          // \n? 匹配可能存在的前导换行符，(Thought|Action|Observation|Critique|Final Answer): 匹配关键字
          // 注意：这确保了只会匹配作为新段落开始的关键字，防止匹配到文本正文中偶尔出现的 "Action:" 单词
          const reactRegex = /(?:^|\n)(Thought|Action|Observation|Critique|Final Answer):\s*/g
          
          let lastIndex = 0
          let currentType = null
          let match

          while ((match = reactRegex.exec(text)) !== null) {
            // 提取上一个标识符到当前标识符之间的文本
            const chunk = text.substring(lastIndex, match.index)
            
            if (currentType) {
              // 属于上一个标识符的内容
              if (currentType === 'Final Answer') {
                // 如果是 Final Answer，不能 trim，必须保留原始换行
                finalContent += chunk
              } else {
                blocks.push({ type: currentType.toLowerCase(), content: chunk.trim() })
              }
            } else if (chunk.trim()) {
              // 开头没有标识符的普通文本，如果是普通文本也要保留原始换行
              finalContent += chunk
            }
            
            // 更新当前标识符类型，并将游标移到内容开始处
            currentType = match[1]
            lastIndex = reactRegex.lastIndex
          }
          
          // 处理最后一段文本
          const lastChunk = text.substring(lastIndex)
          if (currentType) {
            if (currentType === 'Final Answer') {
              // 不能 trim
              finalContent += lastChunk
            } else {
              blocks.push({ type: currentType.toLowerCase(), content: lastChunk.trim() })
            }
          } else {
            // 不能 trim
            finalContent += lastChunk
          }
          
          // 注意：不要使用 .trim() 强行清除 finalContent 的前后空格/换行
          // 因为这会导致以代码块或列表开头的 Markdown 解析异常，这里直接返回原文本
          // 但是！为了防止 "Final Answer: " 提取出来后前面多了一个孤立的回车，我们可以只去掉开头的连续空白
          return { content: finalContent.replace(/^\s+/, ''), reactBlocks: blocks }
        }

        this.messages = rawMessages
          .filter(msg => {
            const role = (msg.type || msg.role || msg.sender || '').toUpperCase()
            return role !== 'SYSTEM'
          })
          .map(msg => {
            const isAssistant = (msg.type || msg.role || msg.sender || '').toLowerCase() === 'assistant'
            const parsed = isAssistant ? parseReActFromFullText(msg.content) : { content: msg.content, reactBlocks: [] }
            
            // 为了防止在没有 Final Answer 关键字时丢掉内容，如果解析出来的 content 是空的，就使用原文
            let finalContent = parsed.content
            if (!finalContent || finalContent.trim() === '') {
                finalContent = msg.content
            }

            return {
              ...msg,
              type: isAssistant ? 'assistant' : 'user',
              agentName: msg.agentName,
              content: finalContent, 
              reactBlocks: parsed.reactBlocks
            }
          })
      } catch (error) {
        console.error('Failed to fetch conversation detail:', error)
        ElMessage.error('获取会话详情失败')
      } finally {
        this.loading = false
      }
    },
    
    // 删除会话
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
    
    // 清空会话消息
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
    
    // 发送消息 (流式)
    async sendStreamMessage(message) {
      if (!message.trim() || this.streaming) return

      let conversationId = this.currentConversationId

      // 1. 添加用户消息到本地 (使用 type 字段对齐后端)
      const userMsg = { type: 'user', content: message, timestamp: new Date() }
      this.messages.push(userMsg)

      // 2. 添加一个空的 AI 消息占位
      const aiMsg = { 
        type: 'assistant', 
        content: '', 
        loading: true, 
        timestamp: new Date(),
        // 新增：用于存储 ReAct 过程中的各个阶段信息
        reactBlocks: [] 
      }
      this.messages.push(aiMsg)
      const aiMsgIndex = this.messages.length - 1

      this.streaming = true

      // 用于解析 ReAct 格式的内部状态机
      let currentReactType = 'content' // 默认作为普通内容
      let currentReactBuffer = ''

      const processReActContent = (contentChunk) => {
        currentReactBuffer += contentChunk
        const msgRef = this.messages[aiMsgIndex]

        const reactPrefixes = ['Thought:', 'Action:', 'Observation:', 'Critique:', 'Final Answer:']
        
        while (currentReactBuffer.length > 0) {
          // 找所有标识符中，出现位置最靠前的一个
          let firstPrefix = null
          let firstIndex = -1
          
          for (const prefix of reactPrefixes) {
            const idx = currentReactBuffer.indexOf(prefix)
            if (idx !== -1 && (firstIndex === -1 || idx < firstIndex)) {
              firstIndex = idx
              firstPrefix = prefix
            }
          }

          if (firstIndex !== -1) {
            // 找到了一个新的阶段标识符
            // 把标识符前面的文本（如果有），归入上一个区块
            const preText = currentReactBuffer.substring(0, firstIndex)
            if (preText) {
              if (currentReactType === 'content' || currentReactType === 'final') {
                msgRef.content += preText
              } else if (msgRef.reactBlocks.length > 0) {
                msgRef.reactBlocks[msgRef.reactBlocks.length - 1].content += preText
              }
            }

            // 更新状态
            if (firstPrefix === 'Thought:') currentReactType = 'thought'
            else if (firstPrefix === 'Action:') currentReactType = 'action'
            else if (firstPrefix === 'Observation:') currentReactType = 'observation'
            else if (firstPrefix === 'Critique:') currentReactType = 'critique'
            else if (firstPrefix === 'Final Answer:') currentReactType = 'final'

            // 剥离掉前缀本身，剩下的文本留到下一次循环或者下一次网络接收处理
            currentReactBuffer = currentReactBuffer.substring(firstIndex + firstPrefix.length).replace(/^\s+/, '')
            
            // 既然进入了新阶段，如果是区块类型，我们要提前创建一个空的 block 对象占位
            if (currentReactType !== 'content' && currentReactType !== 'final') {
              msgRef.reactBlocks.push({ type: currentReactType, content: '' })
            }
          } else {
            // 如果 buffer 里没有发现任何新的标识符，说明这些文本都属于当前的状态
            // 考虑到标识符可能被从中间切断（比如 buffer 现在是 "Final An"）
            // 我们需要检查 buffer 尾部是否包含可能的不完整前缀
            let possiblePrefixCut = false
            let safeLength = currentReactBuffer.length
            
            for (const prefix of reactPrefixes) {
              // 检查 buffer 的后缀是否是某个前缀的前缀
              for (let i = 1; i <= prefix.length && i <= currentReactBuffer.length; i++) {
                if (currentReactBuffer.endsWith(prefix.substring(0, i))) {
                  possiblePrefixCut = true
                  // 保留这个不完整的后缀，等下一次网络 chunk 来了再拼起来看是不是完整的
                  safeLength = Math.min(safeLength, currentReactBuffer.length - i)
                }
              }
            }

            // 把安全的部分（绝对不是前缀的一部分）追加到当前的 block 里
            const safeText = currentReactBuffer.substring(0, safeLength)
            if (safeText) {
              if (currentReactType === 'content' || currentReactType === 'final') {
                msgRef.content += safeText
              } else if (msgRef.reactBlocks.length > 0) {
                msgRef.reactBlocks[msgRef.reactBlocks.length - 1].content += safeText
              }
            }
            
            // 把未确定的部分留在 buffer 里，等下一个网络包
            currentReactBuffer = currentReactBuffer.substring(safeLength)
            break // 跳出 while 循环，等待下一个网络 chunk
          }
        }
      }

      try {
        // 根据后端实际 Controller 代码：@GetMapping(value = "/stream", ...) 且使用 @RequestParam
        // 恢复为 GET 请求，并通过 URL 参数传递 message
        // 关键修复：如果在有 conversationId 的情况下需要传递给后端（如果后端需要显式传入的话）
        // 但目前后端代码是 `String conversationId = session.getId();`
        // 这意味着后端是强依赖 JSESSIONID Cookie 的！
        // 如果想要新建会话，必须让后端生成一个新的 JSESSIONID 或者后端提供一个显式的会话切换机制。
        
        // 考虑到目前的架构，我们可以在请求参数中带上 conversationId，
        // 即便后端当前只读 session.getId()，传过去也没有坏处。
        // 根据选择的 Agent 决定使用哪个 API 端点
        // 如果 selectedAgent 为空或为 "auto"，则使用智能路由
        let url
        if (this.selectedAgent && this.selectedAgent !== 'auto') {
          // 使用指定 Agent
          url = `/api/agent/${this.selectedAgent}/stream?message=${encodeURIComponent(message)}`
        } else {
          // 使用智能路由
          url = `/api/agent/smart/stream?message=${encodeURIComponent(message)}`
        }

        if (conversationId) {
          url += `&conversationId=${encodeURIComponent(conversationId)}`
        }

        console.log('发送请求到:', url, 'Agent:', this.selectedAgent)

        const response = await fetch(url, {
          method: 'GET'
        })

        if (!response.ok) throw new Error('Network response was not ok')

        const reader = response.body.getReader()
        const decoder = new TextDecoder('utf-8', { fatal: false })
        let buffer = '' // 使用 buffer 拼接，防止数据被任意切断
        
        this.messages[aiMsgIndex].loading = false

        while (true) {
          const { done, value } = await reader.read()
          if (done) break
          
          buffer += decoder.decode(value, { stream: true })
          
          // 如果后端完全没有用 SSE 格式（没有 'data:' 关键字），那就当成纯文本流直接处理
          if (!buffer.includes('data:')) {
            let content = buffer
            // 兼容性处理：如果后端发送的是 JSON 字符串包裹的，需要解析 \n，否则直接保留
            content = content.replace(/\\n/g, '\n').replace(/\\"/g, '"')
            processReActContent(content)
            buffer = ''
            continue
          }

          // 标准 SSE 格式解析：按 \n 分割，保留最后一段可能不完整的拼接到下一次
          let newlineIndex
          while ((newlineIndex = buffer.indexOf('\n')) !== -1) {
            let line = buffer.slice(0, newlineIndex)
            buffer = buffer.slice(newlineIndex + 1)
            
            // 移除可能存在的 \r
            line = line.replace('\r', '')

            if (line.startsWith('data: ')) {
              let content = line.substring(6)
              if (content === '[DONE]') continue
              content = content.replace(/\\n/g, '\n').replace(/\\"/g, '"')
              processReActContent(content)
            } else if (line.startsWith('data:')) {
              let content = line.substring(5)
              if (content === '[DONE]') continue
              content = content.replace(/\\n/g, '\n').replace(/\\"/g, '"')
              processReActContent(content)
            } else if (line === '') {
              // 关键点：SSE 协议中的空行是 Event 的分隔符。
              // 但是如果此时我们在一个代码块（```）的中间，后端的空行很有可能是真正的换行符！
              // 我们可以通过判断当前内容里 ``` 的数量，来决定是否保留这个换行符
              const currentContent = this.messages[aiMsgIndex].content
              const codeBlockMatches = currentContent.match(/```/g)
              if (codeBlockMatches && codeBlockMatches.length % 2 !== 0) {
                // 如果是奇数，说明代码块尚未闭合，此时出现的空行极有可能是代码里的真实空行！
                processReActContent('\n')
              }
              continue
            } else {
              // 既不是 data: 也不是空行，说明是被暴力切断的纯文本（或者后端就是直接把带有 \n 的代码块当作纯文本传过来了）
              // 此时，之前被 split 切掉的 \n 实际上是真正的数据内容，我们必须把它补回来！
              let content = line + '\n'
              content = content.replace(/\\n/g, '\n').replace(/\\"/g, '"')
              processReActContent(content)
            }
          }
        }
      } catch (error) {
        console.error('Streaming error:', error)
        this.messages[aiMsgIndex].content = 'Error: ' + error.message
        this.messages[aiMsgIndex].loading = false
        ElMessage.error('消息发送失败')
      } finally {
        this.streaming = false
        // 消息发送完成后，刷新左侧会话列表以更新最新的对话标题和最后回复时间
        this.fetchConversations()
        
        // 完美还原方案：流式输出结束后，静默调用一次接口从数据库拉取完整的、格式完美的消息记录
        // 这样既保留了流式输出的“打字机”实时快感，又能在最后瞬间纠正任何因为流式截断导致的 Markdown 格式错误！
        if (this.currentConversationId) {
          // 不要再手写一堆相同的过滤和解析逻辑了，这很容易导致和 fetchConversationDetail 出现差异
          // 直接调用 fetchConversationDetail 模拟一次“用户重新点击左侧会话”的操作
          // 为了避免屏幕出现 loading 动画导致闪烁，我们可以在调用前备份 loading 状态
          // 重要修复：给后端一点点时间完成事务落库。因为后端在返回 SSE 流完成指令后，
          // 可能还需要几毫秒的时间去执行 `conversationRepository.saveMessage` 写入数据库。
          // 如果前端立刻马上发起 GET 请求，可能查到的还是上一次的历史记录！
          setTimeout(async () => {
            const previousLoading = this.loading
            try {
              await this.fetchConversationDetail(this.currentConversationId)
            } catch (e) {
              console.error('Failed to sync final message format:', e)
            } finally {
              this.loading = previousLoading
            }
          }, 300) // 延迟 300 毫秒，确保后端数据库事务已提交
        }
      }
    }
  }
})
