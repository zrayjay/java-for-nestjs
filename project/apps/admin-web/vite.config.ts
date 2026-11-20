import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    port: 5174,
    proxy: {
      '/api': {
        target: process.env.CAPSTONE_BACKEND_URL || 'http://127.0.0.1:8091',
        changeOrigin: true,
      },
    },
  },
})
