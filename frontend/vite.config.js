import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import viteCompression from 'vite-plugin-compression';
import { visualizer } from 'rollup-plugin-visualizer';

export default defineConfig(({ mode }) => {
  // 加载环境变量
  const env = loadEnv(mode, process.cwd(), '');
  
  return {
  plugins: [
    vue(),
    // Gzip 压缩
    viteCompression({
      algorithm: 'gzip',
      ext: '.gz',
      threshold: 1024, // 只压缩大于1KB的文件
      deleteOriginFile: false
    }),
    // Brotli 压缩
    viteCompression({
      algorithm: 'brotliCompress',
      ext: '.br',
      threshold: 1024,
      deleteOriginFile: false
    }),
    // Bundle 分析工具
    visualizer({
      filename: 'dist/stats.html',
      open: false,
      gzipSize: true,
      brotliSize: true
    })
  ],
  resolve: {
    alias: {
      '@': '/src'
    }
  },
  build: {
    rollupOptions: {
      output: {
        // 手动分包策略
        manualChunks: {
          // 第三方库单独打包
          'vendor': ['vue', 'vue-router'],
          'ui-libs': ['element-plus', '@fortawesome/fontawesome-svg-core', '@fortawesome/vue-fontawesome'],
          'utils': ['axios', 'highlight.js'],
          // 组件分包
          'chat-components': [
            './src/components/layout/MarketingPlatform.vue',
            './src/components/chat/MessageContent.vue',
            './src/components/common/CodeBlock.vue'
          ],
          'search-components': [
            './src/components/search/SearchPanel.vue'
          ]
        },
        // 文件命名优化
        chunkFileNames: 'js/[name]-[hash].js',
        entryFileNames: 'js/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash].[ext]'
      }
    },
    // 代码分割阈值
    chunkSizeWarningLimit: 1000,
    // 启用压缩
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
        pure_funcs: ['console.log', 'console.info'],
        dead_code: true
      },
      mangle: {
        safari10: true
      }
    },
    // 构建优化
    target: 'es2020',
    cssCodeSplit: true,
    sourcemap: false,
    // 预加载优化 - 禁用自动预加载，使用智能预加载策略
    modulePreload: false
  },
  // 基础路径配置 - 解决部署时的资源路径问题
  base: env.VITE_BASE_PATH || '/',
  
  server: {
    host: '0.0.0.0',
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true, // 启用WebSocket代理，这通常有助于SSE
        secure: false
      }
    }
  }
  };
});