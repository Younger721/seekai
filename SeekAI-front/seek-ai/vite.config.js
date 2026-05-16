import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8080'

  return {
    plugins: [vue()],
    server: {
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true
        },
        '/chat': {
          target: apiTarget,
          changeOrigin: true
        }
      }
    },
    build: {
      target: 'esnext',
      minify: 'esbuild',
      sourcemap: false,
      rollupOptions: {
        output: {
          manualChunks: {
            'element-plus': ['element-plus'],
            'echarts': ['echarts'],
            'markdown-it': ['markdown-it']
          }
        }
      }
    },
    optimizeDeps: {
      include: ['element-plus', 'echarts', 'markdown-it']
    }
  }
})
