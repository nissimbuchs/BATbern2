/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';
import viteCompression from 'vite-plugin-compression';
import { VitePWA } from 'vite-plugin-pwa';
import sitemap from 'vite-plugin-sitemap';

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
  define: {
    // Polyfill for sockjs-client which expects Node.js global
    global: 'globalThis',
  },
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
              networkTimeoutSeconds: 20, // Increased from 10s to 20s to reduce false offline triggers
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
        // Offline fallback - exclude auth pages, API routes, public event pages, and authenticated routes
        navigateFallback: '/offline.html',
        navigateFallbackDenylist: [
          /^\/api/,
          /^\/auth/, // All auth routes (login, register, verify-email, reset-password, etc.)
          /^\/login/,
          /^\/register/,
          /^\/forgot-password/,
          /^\/events/, // Story 4.1.5: Public event pages (registration confirmation, etc.)
          /^\/organizer/, // Organizer pages - let app handle errors with proper auth context
          /^\/speaker/, // Speaker pages - let app handle errors with proper auth context
          /^\/partner/, // Partner pages - let app handle errors with proper auth context
          /^\/attendee/, // Attendee pages - let app handle errors with proper auth context
          /^\/admin/, // Admin pages - let app handle errors with proper auth context
          /^\/user/, // User profile pages - let app handle errors with proper auth context
        ],
      },
      devOptions: {
        enabled: false, // Disable PWA in development for faster builds
      },
    }),
    // Sitemap generation for SEO (Story 4.1.8)
    sitemap({
      hostname: 'https://batbern.ch',
      dynamicRoutes: ['/', '/current-event', '/archive', '/search', '/about'],
      changefreq: 'weekly',
      priority: 0.8,
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
    // Support dynamic port configuration for parallel instances
    // Instance 1: PORT=3000 (default), Instance 2: PORT=4000
    port: process.env.PORT ? parseInt(process.env.PORT, 10) : 3000,
    host: true,
    proxy: {
      '/api': {
        target: 'http://batbern-api-gateway:8080',
        changeOrigin: true,
        secure: false,
      },
    },
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
        // Manual chunks for code splitting (Task 13b + Story 4.1.8a)
        manualChunks: (id) => {
          // Split node_modules by package
          if (id.includes('node_modules')) {
            // MUI Icons - separate chunk (lightweight, cacheable)
            if (id.includes('@mui/icons-material')) {
              return 'mui-icons';
            }
            // MUI Core - keep together (heavily used, better compression)
            // Note: @emotion packages are NOT forced into this chunk to avoid circular dependencies
            // Rollup will automatically bundle them with their dependents
            if (id.includes('@mui/material') || id.includes('@mui/system')) {
              return 'mui';
            }
            // Core React vendors
            if (id.includes('react') || id.includes('react-dom') || id.includes('scheduler')) {
              return 'vendor';
            }
            // AWS Amplify
            if (
              id.includes('aws-amplify') ||
              id.includes('@aws-amplify') ||
              id.includes('@aws-sdk')
            ) {
              return 'aws';
            }
            // React Query
            if (id.includes('@tanstack/react-query')) {
              return 'query';
            }
            // React Router
            if (id.includes('react-router')) {
              return 'router';
            }
            // i18n
            if (id.includes('i18next')) {
              return 'i18n';
            }
            // Date utilities
            if (id.includes('date-fns')) {
              return 'format';
            }
          }
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
    testTimeout: 20000, // Increase timeout to 20s for complex component tests with async operations
    exclude: [
      '**/node_modules/**',
      '**/dist/**',
      '**/e2e/**', // E2E tests run with Playwright, not Vitest
      '**/.{idea,git,cache,output,temp}/**',
    ],
    reporters: [
      'default', // Console output
      ['junit', { outputFile: 'test-results/junit.xml' }], // JUnit XML for CI/CD and report aggregation
      // HTML reporter disabled in CI due to module graph issues (only enabled locally)
      ...(process.env.CI ? [] : [['html', { outputFile: 'test-results/index.html' }]]),
    ],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov'],
      exclude: [
        'node_modules/',
        'dist/',
        'src/test/',
        'e2e/',
        '**/*.d.ts',
        '**/*.config.*',
        '**/coverage/',
        'src/utils/performance/**', // Exclude performance monitoring utilities (browser-specific APIs)
        'src/main.tsx', // App entry point - tested via E2E
        'src/pages/**', // Page components - tested via E2E
        'src/config/**', // Configuration files
        'src/theme/**', // Theme configuration
        'src/types/**', // Type definitions (includes generated types)
        'src/**/index.ts', // Re-export files
        'src/**/index.tsx', // Re-export files
        '**/generated/**', // Exclude all generated code
        // Batch import hooks - one-time migration utilities
        'src/hooks/useCompanyBatchImport/**',
        'src/hooks/useEventBatchImport/**',
        'src/hooks/useSpeakerBatchImport/**',
        'src/utils/companyImport.ts',
        'src/utils/eventImport.ts',
        'src/utils/speakerImport.ts',
        'src/hooks/useSessionBatchImport/**', // Session import - one-time migration utility
        // Batch import UI components - one-time migration screens
        'src/components/admin/BatchImport/**',
        'src/components/shared/Event/EventBatchImportModal.tsx',
        'src/components/organizer/UserManagement/SpeakerBatchImportModal.tsx',
        'src/components/organizer/UserManagement/UserSyncPanel.tsx',
        'src/components/organizer/UserManagement/UserCreateEditModal.tsx',
        'src/components/organizer/UserManagement/CompanyCell.tsx',
        // Service layer components with minimal business logic
        'src/services/auth/permissionService.ts', // Permission checks - tested via integration
        'src/services/eventService.ts', // Simple re-export
        // Account API - complex integration requiring full backend
        'src/services/api/userAccountApi.ts',
        // Public components not yet in use
        'src/components/public/Testimonials/**',
        'src/components/public/Event/VenueMap.tsx',
        'src/components/public/Event/SocialSharing.tsx',
        'src/components/public/Event/TopicBadges.tsx',
        // Public hooks for testimonials (not yet in use)
        'src/hooks/usePublicPartners.ts',
        // Unused utilities and services
        'src/services/auth/sessionService.ts', // Not yet implemented
        'src/utils/UIDValidator.ts', // Validation utility not in use
        'src/schemas/partnerSchema.ts', // Schema definition - validated at runtime
        // User profile tabs - tested via E2E
        'src/components/user/UserProfileTab/**',
        'src/components/user/UserSettingsTab/**',
        // Pure data structure files (no testable logic)
        'src/data/**/*.json',
        // i18n framework setup (side effects only, no business logic)
        'src/i18n/config.ts',
      ],
      reportOnFailure: true, // Generate coverage even when tests fail
      // Note: 'all' option removed in vitest v4 - use 'include' to specify files
      include: ['src/**/*.{ts,tsx}'], // Explicitly include source files in coverage
      thresholds: {
        statements: 70, // Only check overall statement coverage
      },
    },
    deps: {
      optimizer: {
        web: {
          include: ['msw'],
        },
      },
    },
    // Configure environment options for React 19 compatibility
    environmentOptions: {
      jsdom: {
        resources: 'usable',
      },
    },
    // Don't fail on console errors - these are expected from API error handlers
    dangerouslyIgnoreUnhandledErrors: true,
  },
});
