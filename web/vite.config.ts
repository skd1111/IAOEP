import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// IAOEP Web Dashboard - Vite 配置
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      // 代理 API 请求到 backend
      '/api/v1': {
        target: process.env.VITE_API_BASE_URL || 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
