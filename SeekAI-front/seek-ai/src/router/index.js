import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/ChatView.vue'),
    meta: { index: 0 }
  },
  {
    path: '/tools',
    name: 'Tools',
    component: () => import('../views/ToolsView.vue'),
    meta: { index: 1 }
  },
  {
    path: '/skills',
    name: 'Skills',
    component: () => import('../views/SkillsView.vue'),
    meta: { index: 1 }
  },
  {
    path: '/memory',
    name: 'Memory',
    component: () => import('../views/MemoryView.vue'),
    meta: { index: 1 }
  },
  {
    path: '/browser',
    name: 'Browser',
    component: () => import('../views/BrowserView.vue'),
    meta: { index: 1 }
  },
  {
    path: '/security',
    name: 'Security',
    component: () => import('../views/SecurityView.vue'),
    meta: { index: 1 }
  },
  {
    path: '/test',
    name: 'ToolsTest',
    component: () => import('../views/ToolsTestView.vue'),
    meta: { index: 2 }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router