import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // 配置 @ 路径别名，指向 src 目录
      // 这样 import { getToken } from '@/utils/auth' 就能正确解析
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      // 开发环境代理：将 /api 开头的请求转发到后端 Spring Boot 服务
      // 避免浏览器跨域限制（开发时前后端端口不同）
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
