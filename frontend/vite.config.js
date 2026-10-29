/* 文件作用：配置 Vue 构建、开发端口、接口代理及资源分包。 */
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: process.env.CRM_API_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
    },
  },
  preview: {
    proxy: {
      '/api': { target: process.env.CRM_API_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
    },
  },
  build: {
    rollupOptions: {
      output: {
        /** 将图表和组件库拆分为独立构建资源。 */
        manualChunks(id) {
          if (id.includes('node_modules/echarts') || id.includes('node_modules/zrender'))
            return 'charts';
          if (id.includes('node_modules/element-plus') || id.includes('node_modules/@element-plus'))
            return 'element-plus';
        },
      },
    },
  },
});
