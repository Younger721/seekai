<template>
  <div class="security-view">
    <div class="page-header">
      <button class="back-btn" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        返回
      </button>
      <h1>权限沙盒</h1>
      <p>系统权限随心配 - 安全可控</p>
    </div>

    <!-- 当前安全级别 -->
    <div class="security-level-card">
      <div class="level-selector">
        <h2>当前安全级别</h2>
        <div class="level-buttons">
          <button
            v-for="level in securityLevels"
            :key="level.name"
            :class="['level-btn', { active: currentLevel === level.name }]"
            @click="setLevel(level.name)"
          >
            <span class="level-name">{{ level.display }}</span>
            <span class="level-desc">{{ level.description }}</span>
          </button>
        </div>
      </div>

      <div class="current-level">
        <div class="level-indicator" :class="currentLevel.toLowerCase()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          </svg>
        </div>
        <div class="level-info">
          <h3>{{ currentLevelConfig?.display }}</h3>
          <p>{{ currentLevelConfig?.description }}</p>
        </div>
      </div>
    </div>

    <!-- 权限列表 -->
    <div class="permissions-section">
      <h2>权限详情</h2>
      <div class="permissions-grid">
        <div
          v-for="perm in permissions"
          :key="perm.name"
          :class="['permission-card', { granted: perm.granted }]"
        >
          <div class="perm-icon" :class="perm.granted ? 'granted' : 'denied'">
            <svg v-if="perm.granted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </div>
          <div class="perm-info">
            <span class="perm-name">{{ perm.display }}</span>
            <span class="perm-desc">{{ perm.description }}</span>
          </div>
          <button
            v-if="!perm.granted"
            class="btn btn-secondary btn-sm"
            @click="grantPermission(perm.name)"
          >
            授权
          </button>
        </div>
      </div>
    </div>

    <!-- 白名单/黑名单 -->
    <div class="lists-section">
      <div class="list-card">
        <h3>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 12l2 2 4-4"/>
            <circle cx="12" cy="12" r="10"/>
          </svg>
          白名单
        </h3>
        <div class="list-input">
          <input v-model="newWhitelist" type="text" class="input" placeholder="输入路径，如 /tmp/*" />
          <button class="btn btn-secondary" @click="addWhitelist">添加</button>
        </div>
        <div class="list-items">
          <span v-for="item in whitelist" :key="item" class="list-item green">
            {{ item }}
          </span>
        </div>
      </div>

      <div class="list-card">
        <h3>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/>
          </svg>
          黑名单
        </h3>
        <div class="list-input">
          <input v-model="newBlacklist" type="text" class="input" placeholder="输入路径，如 /etc/*" />
          <button class="btn btn-secondary" @click="addBlacklist">添加</button>
        </div>
        <div class="list-items">
          <span v-for="item in blacklist" :key="item" class="list-item red">
            {{ item }}
          </span>
        </div>
      </div>
    </div>

    <!-- 操作日志 -->
    <div class="log-section">
      <h2>操作日志</h2>
      <div class="log-list">
        <div v-for="log in logs" :key="log.timestamp" class="log-item">
          <span class="log-time">{{ formatTime(log.timestamp) }}</span>
          <span class="log-action">{{ log.operation }}</span>
          <span :class="['log-result', log.success ? 'success' : 'error']">
            {{ log.success ? '成功' : '失败' }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const goBack = () => {
  router.push('/')
}
import axios from 'axios'

const currentLevel = ref('SANDBOX')
const whitelist = ref(['/tmp/*', '/home/user/documents/*'])
const blacklist = ref(['/etc/*', '/root/.ssh/*'])
const newWhitelist = ref('')
const newBlacklist = ref('')
const logs = ref([])

const securityLevels = [
  { name: 'SANDBOX', display: '沙盒模式', description: '仅允许安全的基本操作' },
  { name: 'RESTRICTED', display: '受限模式', description: '允许部分写入和受限命令' },
  { name: 'STANDARD', display: '标准模式', description: '日常开发所需权限' },
  { name: 'TRUSTED', display: '信任模式', description: '完全信任，可执行所有操作' }
]

const permissions = ref([
  { name: 'FILE_READ', display: '读取文件', description: '查看文件内容', granted: true },
  { name: 'FILE_WRITE', display: '写入文件', description: '创建或修改文件', granted: false },
  { name: 'COMMAND_EXECUTE', display: '执行命令', description: '运行系统命令', granted: false },
  { name: 'NETWORK_REQUEST', display: '网络请求', description: '发送 HTTP 请求', granted: true },
  { name: 'BROWSER_CONTROL', display: '浏览器控制', description: '操作浏览器', granted: false },
  { name: 'DATABASE_READ', display: '数据库读取', description: '查询数据库', granted: false }
])

const currentLevelConfig = computed(() => {
  return securityLevels.find(l => l.name === currentLevel.value)
})

const setLevel = async (level) => {
  currentLevel.value = level
  // 更新权限列表
  const levelIndex = securityLevels.findIndex(l => l.name === level)
  permissions.value.forEach((p, i) => {
    p.granted = i <= levelIndex
  })
  addLog('设置安全级别', `切换到 ${level}`, true)
}

const grantPermission = async (permName) => {
  const perm = permissions.value.find(p => p.name === permName)
  if (perm) {
    perm.granted = true
    addLog('授权权限', permName, true)
  }
}

const addWhitelist = () => {
  if (newWhitelist.value) {
    whitelist.value.push(newWhitelist.value)
    addLog('添加白名单', newWhitelist.value, true)
    newWhitelist.value = ''
  }
}

const addBlacklist = () => {
  if (newBlacklist.value) {
    blacklist.value.push(newBlacklist.value)
    addLog('添加黑名单', newBlacklist.value, true)
    newBlacklist.value = ''
  }
}

const addLog = (operation, detail, success) => {
  logs.value.unshift({
    timestamp: new Date(),
    operation: `${operation}: ${detail}`,
    success
  })
  if (logs.value.length > 20) logs.value.pop()
}

const formatTime = (date) => {
  return new Date(date).toLocaleTimeString('zh-CN')
}

onMounted(() => {
  addLog('系统初始化', '权限系统就绪', true)
})
</script>

<style scoped>
.security-view {
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

/* 安全级别卡片 */
.security-level-card {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  margin-bottom: var(--space-lg);
}

.security-level-card h2 {
  font-size: var(--font-size-lg);
  margin-bottom: var(--space-md);
}

.level-buttons {
  display: flex;
  gap: var(--space-sm);
  margin-bottom: var(--space-lg);
}

.level-btn {
  flex: 1;
  padding: var(--space-md);
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  text-align: left;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.level-btn:hover {
  border-color: var(--color-accent);
}

.level-btn.active {
  background: var(--color-accent);
  border-color: var(--color-accent);
}

.level-name {
  display: block;
  font-weight: 500;
  margin-bottom: 2px;
}

.level-desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.level-btn.active .level-desc {
  color: rgba(255, 255, 255, 0.8);
}

.current-level {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-md);
}

.level-indicator {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
}

.level-indicator svg {
  width: 24px;
  height: 24px;
  color: white;
}

.level-indicator.sandbox { background: var(--color-accent); }
.level-indicator.restricted { background: var(--color-accent-orange); }
.level-indicator.standard { background: var(--color-accent-green); }
.level-indicator.trusted { background: var(--color-accent-purple); }

.level-info h3 {
  margin-bottom: 2px;
}

.level-info p {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

/* 权限列表 */
.permissions-section {
  margin-bottom: var(--space-lg);
}

.permissions-section h2 {
  font-size: var(--font-size-lg);
  margin-bottom: var(--space-md);
}

.permissions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: var(--space-md);
}

.permission-card {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-md);
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.permission-card.granted {
  border-color: var(--color-accent-green);
}

.perm-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
}

.perm-icon.granted {
  background: rgba(48, 209, 88, 0.2);
  color: var(--color-accent-green);
}

.perm-icon.denied {
  background: rgba(255, 69, 58, 0.2);
  color: var(--color-accent-red);
}

.perm-icon svg {
  width: 16px;
  height: 16px;
}

.perm-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.perm-name {
  font-size: var(--font-size-sm);
  font-weight: 500;
}

.perm-desc {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.btn-sm {
  padding: var(--space-xs) var(--space-sm);
  font-size: var(--font-size-xs);
}

/* 列表 */
.lists-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-lg);
  margin-bottom: var(--space-lg);
}

.list-card {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
}

.list-card h3 {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: var(--font-size-base);
  margin-bottom: var(--space-md);
}

.list-card h3 svg {
  width: 18px;
  height: 18px;
}

.list-input {
  display: flex;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.list-input .input {
  flex: 1;
}

.list-items {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
}

.list-item {
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
}

.list-item.green {
  background: rgba(48, 209, 88, 0.2);
  color: var(--color-accent-green);
}

.list-item.red {
  background: rgba(255, 69, 58, 0.2);
  color: var(--color-accent-red);
}

/* 日志 */
.log-section h2 {
  font-size: var(--font-size-lg);
  margin-bottom: var(--space-md);
}

.log-list {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  max-height: 200px;
  overflow-y: auto;
}

.log-item {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--color-border);
  font-size: var(--font-size-sm);
}

.log-item:last-child {
  border-bottom: none;
}

.log-time {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.log-action {
  flex: 1;
}

.log-result {
  font-size: var(--font-size-xs);
}

.log-result.success { color: var(--color-accent-green); }
.log-result.error { color: var(--color-accent-red); }
</style>