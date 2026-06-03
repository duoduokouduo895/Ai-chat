import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import viteCompression from 'vite-plugin-compression';
import { visualizer } from 'rollup-plugin-visualizer';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const isProduction = mode === 'production';
  const enableCompression = env.VITE_ENABLE_COMPRESSION === 'true';
  const useTerser = env.VITE_MINIFY === 'terser';

  return {
    plugins: [
      vue(),
      enableCompression && viteCompression({
        algorithm: 'gzip',
        ext: '.gz',
        threshold: 1024,
        deleteOriginFile: false
      }),
      enableCompression && viteCompression({
        algorithm: 'brotliCompress',
        ext: '.br',
        threshold: 1024,
        deleteOriginFile: false
      }),
      env.VITE_ENABLE_ANALYZE === 'true' && visualizer({
        filename: 'dist/stats.html',
        open: false,
        gzipSize: true,
        brotliSize: true
      })
    ].filter(Boolean),
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    build: {
      rolldownOptions: {
        output: {
          minify: useTerser ? undefined : {
            compress: {
              dropConsole: isProduction && env.VITE_DROP_CONSOLE !== 'false',
              dropDebugger: true
            }
          },
          manualChunks(id) {
            if (!id.includes('node_modules')) {
              return undefined;
            }
            if (id.includes('element-plus')) {
              return 'ui-libs';
            }
            if (id.includes('highlight.js') || id.includes('marked') || id.includes('dompurify')) {
              return 'markdown';
            }
            if (id.includes('axios')) {
              return 'network';
            }
            if (id.includes('vue') || id.includes('vue-router')) {
              return 'vendor';
            }
            return undefined;
          },
          chunkFileNames: 'js/[name]-[hash].js',
          entryFileNames: 'js/[name]-[hash].js',
          assetFileNames: 'assets/[name]-[hash].[ext]'
        }
      },
      chunkSizeWarningLimit: 1000,
      minify: useTerser ? 'terser' : 'oxc',
      terserOptions: useTerser ? {
        compress: {
          drop_console: isProduction ? env.VITE_DROP_CONSOLE !== 'false' : false,
          drop_debugger: true,
          dead_code: true
        },
        mangle: {
          safari10: true
        },
        format: {
          comments: false
        }
      } : undefined,
      target: 'es2020',
      cssCodeSplit: true,
      sourcemap: env.VITE_SOURCEMAP === 'true',
      modulePreload: true,
      reportCompressedSize: enableCompression
    },
    base: env.VITE_BASE_PATH || '/',
    server: {
      host: '0.0.0.0',
      port: 3000,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          ws: true,
          secure: false
        }
      }
    }
  };
});
