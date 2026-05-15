<template>
  <div class="skills-view">
    <div class="page-header">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        返回
      </button>
      <h1>技能市场</h1>
      <p>选择并使用各种技能来扩展 AI 能力</p>
    </div>

    <div class="skills-grid">
      <div
        v-for="skill in skills"
        :key="skill.id"
        class="skill-card"
        @click="useSkill(skill)"
      >
        <div class="skill-icon" :style="{ background: getSkillColor(skill.category) }">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
          </svg>
        </div>
        <div class="skill-info">
          <h3>{{ skill.name }}</h3>
          <p>{{ skill.description }}</p>
          <div class="skill-tags">
            <span v-for="tag in skill.tags" :key="tag" class="tag">{{ tag }}</span>
          </div>
        </div>
        <div class="skill-meta">
          <span class="usage-count">{{ skill.usageCount || 0 }} 次使用</span>
        </div>
      </div>
    </div>

    <!-- 技能执行弹窗 -->
    <div v-if="showExecuteModal" class="modal-overlay" @click="showExecuteModal = false">
      <div class="modal" @click.stop>
        <div class="modal-header">
          <h2>{{ selectedSkill?.name }}</h2>
          <button class="close-btn" @click="showExecuteModal = false">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="input-group">
            <label>{{ selectedSkill?.category === 'translator' ? '翻译文本' : '输入内容' }}</label>
            <textarea v-model="executeParams" class="input" rows="6" :placeholder="getPlaceholder()"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="showExecuteModal = false">取消</button>
          <button class="btn btn-primary" @click="executeSkill" :disabled="executing">
            {{ executing ? '执行中...' : '执行' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 执行结果 -->
    <div v-if="result" class="result-panel">
      <div class="result-header">
        <h3>执行结果</h3>
        <button class="btn btn-ghost" @click="result = ''">关闭</button>
      </div>
      <div class="result-content" v-html="result"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { usePageState } from '../composables/usePageState'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()

const goBack = () => {
  router.push('/')
}

const skills = ref([])
const showExecuteModal = ref(false)
const selectedSkill = ref(null)
const executeParams = ref('')
const executing = ref(false)
const result = ref('')

const fetchSkills = async () => {
  try {
    const response = await axios.get('http://localhost:8080/api/skill/list')
    skills.value = response.data.skills || []
  } catch (e) {
    skills.value = [
      { id: 'ai_chat', name: 'AI 对话', description: '与 AI 进行对话', category: 'assistant', tags: ['ai', 'chat'] },
      { id: 'code_generator', name: '代码生成', description: '根据需求生成代码', category: 'development', tags: ['code', 'generator'] },
      { id: 'data_analysis', name: '数据分析', description: '分析数据并生成报告', category: 'analysis', tags: ['data', 'analysis'] },
      { id: 'translator', name: '翻译', description: '多语言翻译', category: 'tool', tags: ['translate'] },
      { id: 'summarizer', name: '摘要', description: '文本摘要生成', category: 'tool', tags: ['summary'] }
    ]
  }
}

const getSkillColor = (category) => {
  const colors = {
    assistant: 'linear-gradient(135deg, #0a84ff, #5e5ce6)',
    development: 'linear-gradient(135deg, #30d158, #34c759)',
    analysis: 'linear-gradient(135deg, #ff9f0a, #ff6b00)',
    tool: 'linear-gradient(135deg, #bf5af2, #ac8e68)'
  }
  return colors[category] || colors.tool
}

const useSkill = (skill) => {
  selectedSkill.value = skill
  showExecuteModal.value = true
  result.value = ''
}

const getPlaceholder = () => {
  const placeholders = {
    translator: '要翻译的文本...',
    code_generator: '描述你想要生成的代码功能...',
    data_analysis: '输入要分析的数据...',
    summarizer: '要总结的文本内容...'
  }
  return placeholders[selectedSkill.value?.id] || '输入内容...'
}

const executeSkill = async () => {
  if (!executeParams.value.trim()) return

  executing.value = true
  try {
    const response = await axios.post(
      `http://localhost:8080/api/skill/execute/${selectedSkill.value.id}`,
      { text: executeParams.value }
    )
    result.value = response.data.output || response.data.error
    showExecuteModal.value = false
  } catch (e) {
    result.value = '执行失败: ' + e.message
  }
  executing.value = false
}

onMounted(() => {
  fetchSkills()
})
</script>

<style scoped>
.skills-view {
  padding: var(--space-xl);
  height: 100%;
  overflow-y: auto;
}

.page-header {
  margin-bottom: var(--space-xl);
}

.back-btn {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  padding: var(--space-sm) var(--space-md);
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  cursor: pointer;
  margin-bottom: var(--space-md);
  transition: all var(--transition-fast);
}

.back-btn:hover {
  background: var(--color-bg-tertiary);
  color: var(--color-text-primary);
}

.back-btn svg {
  width: 16px;
  height: 16px;
}

.page-header h1 {
  font-size: var(--font-size-2xl);
  margin-bottom: var(--space-xs);
}

.page-header p {
  color: var(--color-text-secondary);
}

.skills-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-lg);
}

.skill-card {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  cursor: pointer;
  transition: all var(--transition-fast);
  display: flex;
  gap: var(--space-md);
}

.skill-card:hover {
  border-color: var(--color-accent);
  transform: translateY(-2px);
}

.skill-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.skill-icon svg {
  width: 24px;
  height: 24px;
  color: white;
}

.skill-info {
  flex: 1;
  min-width: 0;
}

.skill-info h3 {
  font-size: var(--font-size-base);
  margin-bottom: var(--space-xs);
}

.skill-info p {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-bottom: var(--space-sm);
}

.skill-tags {
  display: flex;
  gap: var(--space-xs);
  flex-wrap: wrap;
}

.tag {
  padding: 2px 8px;
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.skill-meta {
  display: flex;
  align-items: flex-end;
}

.usage-count {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
}

.modal {
  background: var(--color-bg-secondary);
  border-radius: var(--radius-xl);
  width: 90%;
  max-width: 500px;
  animation: slideUp 0.3s ease;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-lg);
  border-bottom: 1px solid var(--color-border);
}

.modal-header h2 {
  font-size: var(--font-size-lg);
}

.close-btn {
  width: 32px;
  height: 32px;
  background: transparent;
  border: none;
  color: var(--color-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn svg {
  width: 20px;
  height: 20px;
}

.modal-body {
  padding: var(--space-lg);
}

.input-group label {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-bottom: var(--space-sm);
}

.input {
  width: 100%;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  padding: var(--space-lg);
  border-top: 1px solid var(--color-border);
}

/* 结果面板 */
.result-panel {
  position: fixed;
  bottom: var(--space-xl);
  right: var(--space-xl);
  width: 400px;
  max-height: 400px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  animation: slideUp 0.3s ease;
  overflow: hidden;
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

.result-content {
  padding: var(--space-md);
  max-height: 300px;
  overflow-y: auto;
  font-size: var(--font-size-sm);
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>