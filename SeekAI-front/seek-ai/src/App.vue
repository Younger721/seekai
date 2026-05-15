<template>
  <div class="app-container">
    <router-view />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useChatStore } from './stores/chat'

const chatStore = useChatStore()

onMounted(() => {
  chatStore.fetchAgents()
})
</script>

<style>
@import './styles/apple-design.css';
@import './styles/glassmorphism.css';

html, body, #app {
  height: 100%;
  margin: 0;
  padding: 0;
  overflow: hidden;
}

.app-container {
  height: 100%;
  width: 100%;
  position: relative;
}

/* 动态网格渐变背景 */
.app-container::before {
  content: '';
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: -1;
  background:
    radial-gradient(ellipse 80% 50% at 20% 40%, rgba(139, 92, 246, 0.06), transparent),
    radial-gradient(ellipse 60% 40% at 80% 20%, rgba(10, 132, 255, 0.06), transparent),
    radial-gradient(ellipse 50% 30% at 60% 80%, rgba(236, 72, 153, 0.04), transparent),
    linear-gradient(180deg, #f8fafc 0%, #f1f5f9 50%, #e8eef6 100%);
  background-attachment: fixed;
}

/* 浮动光效动画 */
.app-container::after {
  content: '';
  position: fixed;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background:
    radial-gradient(circle at 30% 30%, rgba(10, 132, 255, 0.04) 0%, transparent 40%),
    radial-gradient(circle at 70% 70%, rgba(139, 92, 246, 0.04) 0%, transparent 40%);
  animation: meshFloat 25s ease-in-out infinite;
  pointer-events: none;
  z-index: -1;
}

@keyframes meshFloat {
  0%, 100% { transform: translate(0, 0) rotate(0deg); }
  33% { transform: translate(2%, 2%) rotate(1deg); }
  66% { transform: translate(-1%, 1%) rotate(-1deg); }
}

/* Scrollbar styling */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
  border-radius: 10px;
}

::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.25);
}

* {
  box-sizing: border-box;
}

/* 页面过渡动画 */
.page-view {
  position: absolute;
  width: 100%;
  height: 100%;
  overflow: auto;
}
</style>