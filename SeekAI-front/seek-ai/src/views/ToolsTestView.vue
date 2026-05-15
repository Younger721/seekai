<template>
  <div class="test-container">
    <h2>🔧 工具测试页面</h2>

    <!-- SSH 测试 -->
    <div class="test-section">
      <h3>🖥️ SSH 连接测试</h3>
      <el-form :model="sshForm" label-width="100px">
        <el-form-item label="主机地址">
          <el-input v-model="sshForm.host" placeholder="例如: 192.168.1.100 或 example.com" />
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="sshForm.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="sshForm.username" placeholder="SSH 用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="sshForm.password" type="password" show-password placeholder="SSH 密码" />
        </el-form-item>
        <el-form-item label="执行命令">
          <el-input v-model="sshForm.command" placeholder="例如: whoami, ls -la, pwd" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="testSSH" :loading="sshLoading">测试 SSH</el-button>
        </el-form-item>
      </el-form>

      <div v-if="sshResult" class="result-box">
        <h4>结果:</h4>
        <pre>{{ sshResult }}</pre>
      </div>
    </div>

    <!-- Ping 测试 -->
    <div class="test-section">
      <h3>📡 Ping 测试</h3>
      <el-form inline>
        <el-form-item label="主机">
          <el-input v-model="pingHost" placeholder="IP 或域名" style="width: 200px;" />
        </el-form-item>
        <el-form-item>
          <el-button @click="testPing" :loading="pingLoading">Ping</el-button>
        </el-form-item>
      </el-form>

      <div v-if="pingResult" class="result-box">
        <pre>{{ pingResult }}</pre>
      </div>
    </div>

    <!-- 本地命令测试 -->
    <div class="test-section">
      <h3>💻 本地命令测试</h3>
      <el-form inline>
        <el-form-item label="命令">
          <el-input v-model="localCmd" placeholder="例如: dir, ipconfig, whoami" style="width: 300px;" />
        </el-form-item>
        <el-form-item>
          <el-button @click="testCmd" :loading="cmdLoading">执行</el-button>
        </el-form-item>
      </el-form>

      <div v-if="cmdResult" class="result-box">
        <pre>{{ cmdResult }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'

// SSH 测试
const sshForm = reactive({
  host: '',
  port: 22,
  username: '',
  password: '',
  command: 'echo "Hello from SSH!"'
})
const sshLoading = ref(false)
const sshResult = ref('')

const testSSH = async () => {
  if (!sshForm.host || !sshForm.username || !sshForm.password) {
    ElMessage.warning('请填写主机、用户名和密码')
    return
  }

  sshLoading.value = true
  sshResult.value = ''

  try {
    const data = await request.get('/test/ssh', {
      params: {
        host: sshForm.host,
        port: sshForm.port,
        username: sshForm.username,
        password: sshForm.password,
        command: sshForm.command
      }
    })
    sshResult.value = data || '无响应'
  } catch (error) {
    sshResult.value = '错误: ' + error.message
  } finally {
    sshLoading.value = false
  }
}

// Ping 测试
const pingHost = ref('')
const pingLoading = ref(false)
const pingResult = ref('')

const testPing = async () => {
  if (!pingHost.value) {
    ElMessage.warning('请输入主机地址')
    return
  }

  pingLoading.value = true
  pingResult.value = ''

  try {
    const data = await request.get('/test/ping', {
      params: { host: pingHost.value }
    })
    pingResult.value = data || '无响应'
  } catch (error) {
    pingResult.value = '错误: ' + error.message
  } finally {
    pingLoading.value = false
  }
}

// 本地命令测试
const localCmd = ref('whoami')
const cmdLoading = ref(false)
const cmdResult = ref('')

const testCmd = async () => {
  if (!localCmd.value) {
    ElMessage.warning('请输入命令')
    return
  }

  cmdLoading.value = true
  cmdResult.value = ''

  try {
    const data = await request.get('/test/cmd', {
      params: { command: localCmd.value }
    })
    cmdResult.value = data || '无响应'
  } catch (error) {
    cmdResult.value = '错误: ' + error.message
  } finally {
    cmdLoading.value = false
  }
}
</script>

<style scoped>
.test-container {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}

h2 {
  margin-bottom: 24px;
  color: #1f2937;
}

.test-section {
  background: white;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.test-section h3 {
  margin: 0 0 16px 0;
  color: #374151;
  font-size: 16px;
}

.result-box {
  margin-top: 16px;
  padding: 12px;
  background: #1f2937;
  border-radius: 8px;
  overflow-x: auto;
}

.result-box h4 {
  margin: 0 0 8px 0;
  color: #10a37f;
  font-size: 14px;
}

.result-box pre {
  margin: 0;
  color: #e5e7eb;
  font-family: 'Fira Code', monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>