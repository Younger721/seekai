<template>
  <div class="security-page neo-page">
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
        <router-link to="/" class="nav-item" :class="{ active: route.path === '/' }" @click="closeMobileSidebar">
          <span class="dot"></span>
          对话
        </router-link>
        <router-link to="/skills" class="nav-item" :class="{ active: route.path === '/skills' }" @click="closeMobileSidebar">
          <span class="dot"></span>
          技能
        </router-link>
        <router-link to="/memory" class="nav-item" :class="{ active: route.path === '/memory' }" @click="closeMobileSidebar">
          <span class="dot"></span>
          记忆
        </router-link>
        <router-link to="/browser" class="nav-item" :class="{ active: route.path === '/browser' }" @click="closeMobileSidebar">
          <span class="dot"></span>
          浏览器
        </router-link>
        <router-link to="/tools" class="nav-item" :class="{ active: route.path === '/tools' }" @click="closeMobileSidebar">
          <span class="dot"></span>
          工具
        </router-link>
        <router-link to="/security" class="nav-item" :class="{ active: route.path === '/security' }" @click="closeMobileSidebar">
          <span class="dot"></span>
          安全
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
          <div v-if="conversations.length === 0" class="empty-conversations">还没有会话，点击上方按钮创建</div>
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
      <section class="security-shell">
        <header class="page-header">
          <div class="header-left">
            <button class="back-btn" @click="goBack">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M19 12H5M12 19l-7-7 7-7" />
              </svg>
              返回
            </button>
            <div>
              <h1>权限沙盒</h1>
              <p>系统权限随心配，安全可控</p>
            </div>
          </div>
          <span class="state-tag">策略已启用</span>
        </header>

        <section class="panel">
          <div class="panel-head">
            <h2>当前安全级别</h2>
          </div>

          <div class="level-grid">
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

          <div class="current-level">
            <div class="level-indicator" :class="currentLevelClass">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
              </svg>
            </div>
            <div class="level-info">
              <h3>{{ currentLevelConfig?.display }}</h3>
              <p>{{ currentLevelConfig?.description }}</p>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h2>权限详情</h2>
          </div>

          <div class="permission-grid">
            <article v-for="perm in permissions" :key="perm.name" :class="['permission-card', { granted: perm.granted }]">
              <div :class="['perm-icon', perm.granted ? 'granted' : 'denied']">
                <svg v-if="perm.granted" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18" />
                  <line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </div>
              <div class="perm-info">
                <span class="perm-name">{{ perm.display }}</span>
                <span class="perm-desc">{{ perm.description }}</span>
              </div>
              <button v-if="!perm.granted" class="action-btn ghost" @click="grantPermission(perm.name)">授权</button>
            </article>
          </div>
        </section>

        <section class="lists-grid">
          <article class="panel list-panel">
            <div class="panel-head">
              <h2>白名单</h2>
            </div>

            <div class="list-input">
              <input v-model="newWhitelist" type="text" class="text-input" placeholder="输入路径，如 /tmp/*" />
              <button class="action-btn" @click="addWhitelist">添加</button>
            </div>

            <div class="list-items">
              <span v-for="item in whitelist" :key="`w-${item}`" class="chip chip-green">{{ item }}</span>
              <div v-if="whitelist.length === 0" class="empty-inline">暂无白名单规则</div>
            </div>
          </article>

          <article class="panel list-panel">
            <div class="panel-head">
              <h2>黑名单</h2>
            </div>

            <div class="list-input">
              <input v-model="newBlacklist" type="text" class="text-input" placeholder="输入路径，如 /etc/*" />
              <button class="action-btn" @click="addBlacklist">添加</button>
            </div>

            <div class="list-items">
              <span v-for="item in blacklist" :key="`b-${item}`" class="chip chip-red">{{ item }}</span>
              <div v-if="blacklist.length === 0" class="empty-inline">暂无黑名单规则</div>
            </div>
          </article>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h2>操作日志</h2>
          </div>

          <div class="log-list">
            <article v-for="log in logs" :key="log.id" class="log-item">
              <span class="log-time">{{ formatTime(log.timestamp) }}</span>
              <span class="log-action">{{ log.operation }}: {{ log.detail }}</span>
              <span :class="['log-result', log.success ? 'success' : 'error']">{{ log.success ? '成功' : '失败' }}</span>
            </article>
            <div v-if="logs.length === 0" class="empty-state">暂无日志</div>
          </div>
        </section>
      </section>
    </main>

    <div v-if="mobileSidebarOpen" class="mobile-mask" @click="closeMobileSidebar"></div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore } from '../stores/chat'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()

const mobileSidebarOpen = ref(false)
const userName = ref(localStorage.getItem('seekai_user_name') || '用户')

const conversations = computed(() => chatStore.conversations)
const currentConversationId = computed(() => chatStore.currentConversationId)

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

const securityLevels = [
  { name: 'SANDBOX', display: '沙盒模式', description: '仅允许安全基础操作' },
  { name: 'RESTRICTED', display: '受限模式', description: '允许部分写入和受限命令' },
  { name: 'STANDARD', display: '标准模式', description: '日常开发所需权限' },
  { name: 'TRUSTED', display: '信任模式', description: '完全信任，可执行所有操作' }
]

const levelPermissions = {
  SANDBOX: ['FILE_READ', 'NETWORK_REQUEST'],
  RESTRICTED: ['FILE_READ', 'NETWORK_REQUEST', 'FILE_WRITE'],
  STANDARD: ['FILE_READ', 'NETWORK_REQUEST', 'FILE_WRITE', 'COMMAND_EXECUTE', 'BROWSER_CONTROL'],
  TRUSTED: ['FILE_READ', 'NETWORK_REQUEST', 'FILE_WRITE', 'COMMAND_EXECUTE', 'BROWSER_CONTROL', 'DATABASE_READ']
}

const currentLevel = ref('SANDBOX')
const permissions = ref([
  { name: 'FILE_READ', display: '读取文件', description: '查看文件内容', granted: true },
  { name: 'FILE_WRITE', display: '写入文件', description: '创建或修改文件', granted: false },
  { name: 'COMMAND_EXECUTE', display: '执行命令', description: '运行系统命令', granted: false },
  { name: 'NETWORK_REQUEST', display: '网络请求', description: '发送 HTTP 请求', granted: true },
  { name: 'BROWSER_CONTROL', display: '浏览器控制', description: '操作浏览器', granted: false },
  { name: 'DATABASE_READ', display: '数据库读取', description: '查询数据库', granted: false }
])

const whitelist = ref(['/tmp/*', '/home/user/documents/*'])
const blacklist = ref(['/etc/*', '/root/.ssh/*'])
const newWhitelist = ref('')
const newBlacklist = ref('')
const logs = ref([])

const currentLevelConfig = computed(() => securityLevels.find(level => level.name === currentLevel.value))
const currentLevelClass = computed(() => currentLevel.value.toLowerCase())

const addLog = (operation, detail, success) => {
  logs.value.unshift({
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    timestamp: new Date(),
    operation,
    detail,
    success
  })
  if (logs.value.length > 20) logs.value.pop()
}

const setLevel = (levelName) => {
  currentLevel.value = levelName
  const allowed = new Set(levelPermissions[levelName] || [])
  permissions.value = permissions.value.map(item => ({
    ...item,
    granted: allowed.has(item.name)
  }))
  addLog('切换安全级别', securityLevels.find(level => level.name === levelName)?.display || levelName, true)
}

const grantPermission = (permName) => {
  const target = permissions.value.find(item => item.name === permName)
  if (!target) return
  target.granted = true
  addLog('授权权限', target.display, true)
}

const addWhitelist = () => {
  const value = newWhitelist.value.trim()
  if (!value) return
  if (!whitelist.value.includes(value)) {
    whitelist.value.push(value)
    addLog('添加白名单', value, true)
  }
  newWhitelist.value = ''
}

const addBlacklist = () => {
  const value = newBlacklist.value.trim()
  if (!value) return
  if (!blacklist.value.includes(value)) {
    blacklist.value.push(value)
    addLog('添加黑名单', value, true)
  }
  newBlacklist.value = ''
}

const formatTime = (date) =>
  new Date(date).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })

onMounted(() => {
  chatStore.fetchConversations()
  chatStore.fetchAgents()
  addLog('系统初始化', '权限系统就绪', true)
})
</script>

<style>
.security-page {
  --brand-ink: #1c1917;
  --brand-text: #44403c;

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

.security-page .sidebar {
  position: relative;
  z-index: 3;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px 16px;
  background: #f2f0ec;
  border-right: 1px solid #e3dfd8;
}

.security-page .brand-block {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
}

.security-page .brand-mark {
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
}

.security-page .brand-block h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}

.security-page .brand-block p {
  margin: 2px 0 0;
  font-size: 12px;
  color: #78716c;
}

.security-page .new-chat-btn {
  height: 44px;
  border: none;
  border-radius: 12px;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  background: #171717;
  transition: background-color 0.2s ease;
}

.security-page .new-chat-btn:hover {
  background: #262626;
}

.security-page .plus {
  margin-right: 6px;
  font-size: 16px;
}

.security-page .agent-tip {
  margin: 0;
  font-size: 12px;
  color: #78716c;
  text-align: center;
}

.security-page .agent-tip span {
  color: #171717;
  font-weight: 700;
}

.security-page .nav-list {
  display: grid;
  gap: 6px;
}

.security-page .nav-item {
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

.security-page .nav-item:hover {
  background: #ebe7e1;
  color: #1f2937;
}

.security-page .nav-item.active {
  background: #e6e2db;
  color: #1f2937;
  font-weight: 600;
}

.security-page .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #a8a29e;
}

.security-page .nav-item.active .dot {
  background: #16a34a;
}

.security-page .history-panel {
  flex: 1;
  min-height: 160px;
  display: flex;
  flex-direction: column;
  background: #f6f4f0;
  border: 1px solid #e2ded7;
  border-radius: 14px;
  overflow: hidden;
}

.security-page .history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  font-size: 12px;
  color: #57534e;
  border-bottom: 1px solid #e8e3db;
}

.security-page .history-header em {
  font-style: normal;
  color: #166534;
  font-weight: 700;
}

.security-page .conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.security-page .conversation-item {
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

.security-page .conversation-item:hover {
  background: #ece8e2;
}

.security-page .conversation-item.active {
  background: #e7e2da;
  color: #1f2937;
  box-shadow: inset 0 0 0 1px #dbd5cb;
}

.security-page .conversation-title {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.security-page .delete-btn {
  opacity: 0;
  border: none;
  background: transparent;
  color: #78716c;
  font-size: 12px;
  cursor: pointer;
}

.security-page .conversation-item:hover .delete-btn {
  opacity: 1;
}

.security-page .delete-btn:hover {
  color: #d83939;
}

.security-page .empty-conversations {
  padding: 18px 12px;
  text-align: center;
  color: #78716c;
  font-size: 12px;
}

.security-page .user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
  background: #efebe5;
  border: 1px solid #e0dbd3;
}

.security-page .avatar {
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

.security-page .user-name {
  font-size: 13px;
  font-weight: 600;
}

.security-page .user-status {
  font-size: 11px;
  color: #78716c;
}

.security-page .workspace {
  position: relative;
  z-index: 2;
  min-width: 0;
  padding: 10px;
  background: #ffffff;
}

.security-page .security-shell {
  height: 100%;
  overflow-y: auto;
  border: 1px solid #e8e4dd;
  border-radius: 12px;
  background: #ffffff;
  padding: 18px;
}

.security-page .page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.security-page .header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.security-page .back-btn {
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

.security-page .back-btn:hover {
  background: #efebe5;
}

.security-page .back-btn svg {
  width: 16px;
  height: 16px;
}

.security-page .page-header h1 {
  margin: 0;
  font-size: 22px;
  line-height: 1.1;
}

.security-page .page-header p {
  margin: 4px 0 0;
  font-size: 13px;
  color: #78716c;
}

.security-page .state-tag {
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

.security-page .panel {
  background: #faf8f5;
  border: 1px solid #e8e4dd;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 12px;
}

.security-page .panel-head {
  padding: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebe7df;
}

.security-page .panel-head h2 {
  margin: 0;
  font-size: 16px;
}

.security-page .level-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  padding: 12px;
}

.security-page .level-btn {
  border: 1px solid #ddd8cf;
  border-radius: 10px;
  background: #f3f0eb;
  text-align: left;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.security-page .level-btn:hover {
  background: #ece8e2;
}

.security-page .level-btn.active {
  background: #171717;
  border-color: #171717;
}

.security-page .level-name {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: #1c1917;
  margin-bottom: 2px;
}

.security-page .level-desc {
  display: block;
  font-size: 12px;
  color: #78716c;
}

.security-page .level-btn.active .level-name,
.security-page .level-btn.active .level-desc {
  color: #ffffff;
}

.security-page .current-level {
  margin: 0 12px 12px;
  border: 1px solid #e5e1da;
  border-radius: 10px;
  background: #f5f2ed;
  padding: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.security-page .level-indicator {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  flex-shrink: 0;
}

.security-page .level-indicator svg {
  width: 22px;
  height: 22px;
}

.security-page .level-indicator.sandbox {
  background: #2563eb;
}

.security-page .level-indicator.restricted {
  background: #ea580c;
}

.security-page .level-indicator.standard {
  background: #16a34a;
}

.security-page .level-indicator.trusted {
  background: #a21caf;
}

.security-page .level-info h3 {
  margin: 0;
  font-size: 17px;
}

.security-page .level-info p {
  margin: 4px 0 0;
  font-size: 13px;
  color: #78716c;
}

.security-page .permission-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 10px;
  padding: 12px;
}

.security-page .permission-card {
  border: 1px solid #ddd8cf;
  border-radius: 10px;
  background: #f5f2ed;
  padding: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.security-page .permission-card.granted {
  border-color: #86d9a5;
  background: #effbf3;
}

.security-page .perm-icon {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.security-page .perm-icon svg {
  width: 16px;
  height: 16px;
}

.security-page .perm-icon.granted {
  background: #d7f5e3;
  color: #15803d;
}

.security-page .perm-icon.denied {
  background: #fee2e2;
  color: #dc2626;
}

.security-page .perm-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.security-page .perm-name {
  font-size: 14px;
  font-weight: 600;
  color: #1c1917;
}

.security-page .perm-desc {
  font-size: 12px;
  color: #78716c;
}

.security-page .action-btn {
  height: 36px;
  padding: 0 14px;
  border: none;
  border-radius: 10px;
  background: #171717;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.security-page .action-btn:hover {
  background: #262626;
}

.security-page .action-btn.ghost {
  height: 30px;
  border: 1px solid #d2cec6;
  background: #f7f5f1;
  color: #292524;
  font-size: 12px;
  padding: 0 10px;
}

.security-page .action-btn.ghost:hover {
  background: #ece8e2;
}

.security-page .lists-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.security-page .list-panel {
  margin-bottom: 12px;
}

.security-page .list-input {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid #ebe7df;
}

.security-page .text-input {
  flex: 1;
  height: 38px;
  border: 1px solid #d8d4ce;
  border-radius: 10px;
  padding: 0 12px;
  font-size: 14px;
  background: #ffffff;
  color: #1c1917;
}

.security-page .text-input:focus {
  outline: none;
  border-color: #171717;
}

.security-page .list-items {
  min-height: 62px;
  padding: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: flex-start;
}

.security-page .chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.security-page .chip-green {
  background: #dcfce7;
  color: #166534;
}

.security-page .chip-red {
  background: #fee2e2;
  color: #991b1b;
}

.security-page .empty-inline {
  color: #78716c;
  font-size: 13px;
}

.security-page .log-list {
  max-height: 220px;
  overflow-y: auto;
}

.security-page .log-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid #ebe7df;
}

.security-page .log-item:last-child {
  border-bottom: none;
}

.security-page .log-time {
  color: #78716c;
  font-size: 12px;
  flex-shrink: 0;
}

.security-page .log-action {
  color: #292524;
  font-size: 13px;
  flex: 1;
}

.security-page .log-result {
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.security-page .log-result.success {
  color: #166534;
}

.security-page .log-result.error {
  color: #b91c1c;
}

.security-page .empty-state {
  padding: 20px 12px;
  text-align: center;
  color: #78716c;
  font-size: 13px;
}

.security-page .mobile-mask {
  display: none;
}

@media (max-width: 1300px) {
  .security-page .level-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1080px) {
  .security-page {
    grid-template-columns: 1fr;
  }

  .security-page .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    width: 300px;
    transform: translateX(-102%);
    transition: transform 0.22s ease;
  }

  .security-page .sidebar.mobile-open {
    transform: translateX(0);
  }

  .security-page .mobile-mask {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(15, 23, 42, 0.35);
    z-index: 2;
  }
}

@media (max-width: 820px) {
  .security-page .security-shell {
    padding: 12px;
  }

  .security-page .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .security-page .header-left {
    width: 100%;
  }

  .security-page .lists-grid {
    grid-template-columns: 1fr;
  }

  .security-page .level-grid {
    grid-template-columns: 1fr;
  }
}
</style>
