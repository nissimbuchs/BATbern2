/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';
import viteCompression from 'vite-plugin-compression';
import { VitePWA } from 'vite-plugin-pwa';

/**
 * Vite Configuration - Environment-Agnostic Build
 *
 * This configuration builds a single production-optimized artifact that works
 * in all environments (development, staging, production).
 *
 * NO ENVIRONMENT VARIABLES are baked into the build!
 * - NO VITE_ variables (API endpoints, Cognito config, etc.)
 * - Configuration is loaded at RUNTIME from backend API: GET /api/v1/config
 *
 * This enables "build once, deploy everywhere":
 * - Single build deployed to dev/staging/prod
 * - Configuration changes don't require rebuilds
 * - Test exact same artifact in staging and production
 */

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    // PWA configuration with service worker (Task 14b)
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'apple-touch-icon.png', 'masked-icon.svg'],
      manifest: {
        name: 'BATbern Platform',
        short_name: 'BATbern',
        description: 'BATbern Event Management Platform - Business Accelerator for Tomorrow',
        theme_color: '#D52B1E', // Swiss red from theme
        background_color: '#FFFFFF',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        icons: [
          {
            src: '/icons/icon-192x192.png',
            sizes: '192x192',
            type: 'image/png',
            purpose: 'any',
          },
          {
            src: '/icons/icon-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any',
          },
          {
            src: '/icons/icon-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        // Service worker caching strategies
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff,woff2}'],
        skipWaiting: true, // Activate new service worker immediately
        clientsClaim: true, // Take control of all pages immediately
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/fonts\.googleapis\.com\/.*/i,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'google-fonts-stylesheets',
              networkTimeoutSeconds: 3, // Fallback to cache if network is slow
              expiration: {
                maxEntries: 10,
                maxAgeSeconds: 60 * 60 * 24 * 365, // 1 year
              },
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
          {
            urlPattern: /^https:\/\/fonts\.gstatic\.com\/.*/i,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'google-fonts-webfonts',
              networkTimeoutSeconds: 3, // Fallback to cache if network is slow
              expiration: {
                maxEntries: 30,
                maxAgeSeconds: 60 * 60 * 24 * 365, // 1 year
              },
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
          {
            urlPattern: /^https:\/\/.*\.cloudfront\.net\/.*/i,
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'cdn-cache',
              expiration: {
                maxEntries: 50,
                maxAgeSeconds: 60 * 60 * 24 * 7, // 1 week
              },
            },
          },
          {
            urlPattern: /^https:\/\/api\.(staging\.)?batbern\.ch\/api\/.*/i,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'api-cache',
              networkTimeoutSeconds: 10,
              expiration: {
                maxEntries: 50,
                maxAgeSeconds: 60 * 5, // 5 minutes
              },
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
        ],
        // Offline fallback - exclude auth pages and API routes
        navigateFallback: '/offline.html',
        navigateFallbackDenylist: [/^\/api/, /^\/login/, /^\/register/, /^\/forgot-password/],
      },
      devOptions: {
        enabled: false, // Disable PWA in development for faster builds
      },
    }),
    // Gzip compression for production builds (Task 13b)
    viteCompression({
      algorithm: 'gzip',
      ext: '.gz',
      threshold: 1024, // Only compress files larger than 1KB
    }),
    // Brotli compression for modern browsers (Task 13b)
    viteCompression({
      algorithm: 'brotliCompress',
      ext: '.br',
      threshold: 1024,
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, './src'),
      '@components': resolve(__dirname, './src/components'),
      '@hooks': resolve(__dirname, './src/hooks'),
      '@services': resolve(__dirname, './src/services'),
      '@stores': resolve(__dirname, './src/stores'),
      '@types': resolve(__dirname, './src/types'),
      '@utils': resolve(__dirname, './src/utils'),
      '@pages': resolve(__dirname, './src/pages'),
      'msw/node': resolve(__dirname, './node_modules/msw/lib/node/index.mjs'),
    },
  },
  server: {
    port: 3000,
    host: true,
  },
  build: {
    target: 'esnext',
    outDir: 'dist',
    sourcemap: true,
    // Minification enabled (Task 13b)
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true, // Remove console.log in production
        drop_debugger: true,
      },
    },
    // Chunk size warnings
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        // Manual chunks for code splitting (Task 13b)
        manualChunks: {
          vendor: ['react', 'react-dom'],
          mui: ['@mui/material', '@mui/icons-material'],
          aws: ['aws-amplify', '@aws-amplify/ui-react'],
          query: ['@tanstack/react-query'],
          router: ['react-router-dom'],
          i18n: ['react-i18next', 'i18next', 'i18next-browser-languagedetector'],
        },
        // Asset file naming for better caching
        assetFileNames: (assetInfo) => {
          const info = assetInfo.name?.split('.');
          const ext = info?.[info.length - 1];
          if (/png|jpe?g|svg|gif|tiff|bmp|ico/i.test(ext || '')) {
            return `assets/images/[name]-[hash][extname]`;
          } else if (/woff|woff2|ttf|otf|eot/i.test(ext || '')) {
            return `assets/fonts/[name]-[hash][extname]`;
          }
          return `assets/[name]-[hash][extname]`;
        },
        chunkFileNames: 'assets/js/[name]-[hash].js',
        entryFileNames: 'assets/js/[name]-[hash].js',
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    exclude: [
      '**/node_modules/**',
      '**/dist/**',
      '**/e2e/**', // E2E tests run with Playwright, not Vitest
      '**/.{idea,git,cache,output,temp}/**',
    ],
    coverage: {
      reporter: ['text', 'json', 'html', 'lcov'],
      exclude: ['node_modules/', 'src/test/', 'e2e/', '**/*.d.ts', '**/*.config.*', '**/coverage/'],
      reportOnFailure: true, // Generate coverage even when tests fail
      all: true, // Include all source files in coverage report
    },
    deps: {
      optimizer: {
        web: {
          include: ['msw'],
        },
      },
    },
  },
});
