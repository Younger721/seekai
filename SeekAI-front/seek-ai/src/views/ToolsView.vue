<template>
  <div class="tools-view">
    <div class="page-header">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        返回
      </button>
      <h1>工具中心</h1>
      <p>探索和使用各种 AI 工具</p>
    </div>

    <!-- 工具分类 -->
    <div class="tool-categories">
      <button
        v-for="cat in categories"
        :key="cat.id"
        :class="['category-btn', { active: activeCategory === cat.id }]"
        @click="activeCategory = cat.id"
      >
        <span class="cat-icon" v-html="cat.icon"></span>
        <span>{{ cat.name }}</span>
      </button>
    </div>

    <!-- 工具网格 -->
    <div class="tools-grid">
      <div
        v-for="tool in filteredTools"
        :key="tool.name"
        class="tool-card"
        @click="openTool(tool)"
      >
        <div class="tool-icon" :style="{ background: tool.color }">
          <span v-html="tool.icon"></span>
        </div>
        <div class="tool-info">
          <h3>{{ tool.name }}</h3>
          <p>{{ tool.description }}</p>
        </div>
        <div class="tool-arrow">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="9 18 15 12 9 6"/>
          </svg>
        </div>
      </div>
    </div>

    <!-- 工具弹窗 -->
    <div v-if="showToolModal" class="modal-overlay" @click="showToolModal = false">
      <div class="modal tool-modal" @click.stop>
        <div class="modal-header">
          <div class="modal-title">
            <div class="tool-icon" :style="{ background: selectedTool?.color, width: '40px', height: '40px' }">
              <span v-html="selectedTool?.icon"></span>
            </div>
            <h2>{{ selectedTool?.name }}</h2>
          </div>
          <button class="close-btn" @click="showToolModal = false">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <p class="tool-desc">{{ selectedTool?.description }}</p>

          <div class="tool-input">
            <label>输入</label>
            <textarea
              v-model="toolInput"
              class="input"
              rows="4"
              :placeholder="selectedTool?.placeholder"
            ></textarea>
          </div>

          <button class="btn btn-primary" @click="executeTool" :disabled="executing">
            {{ executing ? '执行中...' : '执行' }}
          </button>

          <div v-if="toolOutput" class="tool-output">
            <label>输出</label>
            <pre>{{ toolOutput }}</pre>
          </div>
        </div>
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

const activeCategory = ref('all')
const showToolModal = ref(false)
const selectedTool = ref(null)
const toolInput = ref('')
const toolOutput = ref('')
const executing = ref(false)

const categories = [
  { id: 'all', name: '全部', icon: '⚡' },
  { id: 'ai', name: 'AI 能力', icon: '🤖' },
  { id: 'web', name: '网页', icon: '🌐' },
  { id: 'dev', name: '开发', icon: '💻' },
  { id: 'data', name: '数据', icon: '📊' }
]

const tools = [
  {
    name: 'AI 对话',
    description: '与 AI 进行智能对话',
    category: 'ai',
    color: 'linear-gradient(135deg, #0a84ff, #5e5ce6)',
    icon: '💬',
    placeholder: '输入你的问题...',
    endpoint: '/api/skill/execute/ai_chat',
    paramKey: 'message'
  },
  {
    name: '代码生成',
    description: '根据描述生成代码',
    category: 'dev',
    color: 'linear-gradient(135deg, #30d158, #34c759)',
    icon: '⌨️',
    placeholder: '描述你想要的功能...',
    endpoint: '/api/skill/execute/code_generator',
    paramKey: 'requirement'
  },
  {
    name: '翻译',
    description: '多语言翻译',
    category: 'ai',
    color: 'linear-gradient(135deg, #bf5af2, #ac8e68)',
    icon: '🌍',
    placeholder: '输入要翻译的文本...',
    endpoint: '/api/skill/execute/translator',
    paramKey: 'text'
  },
  {
    name: '网页爬虫',
    description: '抓取网页内容',
    category: 'web',
    color: 'linear-gradient(135deg, #ff9f0a, #ff6b00)',
    icon: '🕷️',
    placeholder: '输入网页 URL...',
    endpoint: '/api/tools/crawl',
    paramKey: 'url'
  },
  {
    name: '数据分析',
    description: '分析数据并生成报告',
    category: 'data',
    color: 'linear-gradient(135deg, #64d2ff, #0a84ff)',
    icon: '📈',
    placeholder: '输入要分析的数据...',
    endpoint: '/api/skill/execute/data_analysis',
    paramKey: 'data'
  },
  {
    name: '文本摘要',
    description: '将长文本压缩成摘要',
    category: 'ai',
    color: 'linear-gradient(135deg, #ff375f, #bf5af2)',
    icon: '📝',
    placeholder: '输入要总结的文本...',
    endpoint: '/api/skill/execute/summarizer',
    paramKey: 'text'
  },
  {
    name: '搜索',
    description: '网络搜索',
    category: 'web',
    color: 'linear-gradient(135deg, #30d158, #0a84ff)',
    icon: '🔍',
    placeholder: '输入搜索关键词...',
    endpoint: '/api/tools/search',
    paramKey: 'keyword'
  },
  {
    name: '天气查询',
    description: '查询天气信息',
    category: 'data',
    color: 'linear-gradient(135deg, #5e5ce6, #bf5af2)',
    icon: '🌤️',
    placeholder: '输入城市名称...',
    endpoint: '/api/tools/weather',
    paramKey: 'city'
  }
]

const filteredTools = computed(() => {
  if (activeCategory.value === 'all') return tools
  return tools.filter(t => t.category === activeCategory.value)
})

const openTool = (tool) => {
  selectedTool.value = tool
  showToolModal.value = true
  toolInput.value = ''
  toolOutput.value = ''
}

const executeTool = async () => {
  if (!toolInput.value.trim()) return

  executing.value = true
  toolOutput.value = ''

  try {
    const response = await axios.post(
      selectedTool.value.endpoint,
      { [selectedTool.value.paramKey]: toolInput.value }
    )
    toolOutput.value = response.data.output || response.data.message || JSON.stringify(response.data, null, 2)
  } catch (e) {
    toolOutput.value = '执行失败: ' + e.message
  }

  executing.value = false
}
</script>

<style scoped>
.tools-view {
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

/* 分类 */
.tool-categories {
  display: flex;
  gap: var(--space-sm);
  margin-bottom: var(--space-xl);
  overflow-x: auto;
  padding-bottom: var(--space-sm);
}

.category-btn {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  padding: var(--space-sm) var(--space-lg);
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-full);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  cursor: pointer;
  white-space: nowrap;
  transition: all var(--transition-fast);
}

.category-btn:hover {
  border-color: var(--color-accent);
}

.category-btn.active {
  background: var(--color-accent);
  border-color: var(--color-accent);
  color: white;
}

.cat-icon {
  font-size: 16px;
}

/* 工具卡片 */
.tools-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-lg);
}

.tool-card {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-lg);
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.tool-card:hover {
  border-color: var(--color-accent);
  transform: translateY(-2px);
}

.tool-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.tool-info {
  flex: 1;
  min-width: 0;
}

.tool-info h3 {
  font-size: var(--font-size-base);
  margin-bottom: 2px;
}

.tool-info p {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tool-arrow {
  color: var(--color-text-tertiary);
}

.tool-arrow svg {
  width: 20px;
  height: 20px;
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);

.tool-modal {
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-lg);
  border-bottom: 1px solid var(--color-border);
}

.modal-title {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.modal-title h2 {
  font-size: var(--font-size-lg);
}

.close-btn {
  width: 32px;
  height: 32px;
  background: transparent;
  border: none;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.close-btn svg {
  width: 20px;
  height: 20px;
}

.modal-body {
  padding: var(--space-lg);
}

.tool-desc {
  color: var(--color-text-secondary);
  margin-bottom: var(--space-lg);
}

.tool-input {
  margin-bottom: var(--space-lg);
}

.tool-input label,
.tool-output label {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-bottom: var(--space-sm);
}

.tool-output {
  margin-top: var(--space-lg);
}

.tool-output pre {
  background: var(--color-bg-tertiary);
  padding: var(--space-md);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  white-space: pre-wrap;
  max-height: 200px;
  overflow-y: auto;
}
</style>