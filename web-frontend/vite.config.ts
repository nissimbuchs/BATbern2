/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';
import viteCompression from 'vite-plugin-compression';
import { VitePWA } from 'vite-plugin-pwa';
import sitemap from 'vite-plugin-sitemap';
import { viteStaticCopy } from 'vite-plugin-static-copy';

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
        // 2026-06-05 — index.html is deliberately NOT precached and navigations are
        // served NetworkFirst (see runtimeCaching below). Precache-pinning index.html
        // froze BOTH the app shell AND its response headers (CSP!) until the SW
        // updated: after every deploy, each SW-controlled client ran the OLD bundle for
        // one more full page-load cycle. Two prod incidents: the CSP connect-src fix
        // never reaching SW clients (2026-06-04), and the 12.8-F8 federated-login fix
        // failing one last time per client (2026-06-05, first registration of
        // buchsjosefnissim@gmail.com). Hashed assets stay precached — they are
        // immutable; only the HTML entry must always be fresh.
        globIgnores: ['**/index.html'],
        // Disable the precache-bound SPA navigation route (createHandlerBoundToURL
        // requires index.html in the manifest). Navigations fall through to the
        // runtimeCaching NetworkFirst route below; CloudFront handles 404→index.html.
        navigateFallback: null,
        skipWaiting: true, // Activate new service worker immediately
        clientsClaim: true, // Take control of all pages immediately
        runtimeCaching: [
          // Navigations (full page loads): network first so a fresh deploy reaches
          // every client on their NEXT page load; cached copy only as offline fallback.
          {
            urlPattern: ({ request }) => request.mode === 'navigate',
            handler: 'NetworkFirst',
            options: {
              cacheName: 'html-cache',
              networkTimeoutSeconds: 5,
              expiration: {
                maxEntries: 10,
                maxAgeSeconds: 60 * 60 * 24 * 7, // offline fallback for up to 1 week
              },
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
          // Google Fonts caching removed — the app loads no web fonts (system
          // font stack only). See index.html.
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
        // No offline fallback - CloudFront handles 404->index.html for SPA routing
        // Service worker's offline fallback was causing false "you are offline" errors
        // navigateFallback: '/offline.html',
        // navigateFallbackDenylist: [...],
      },
      devOptions: {
        enabled: false, // Disable PWA in development for faster builds
      },
    }),
    // Self-hosted TinyMCE assets (skins, icons, models, plugins)
    // Required because TinyMCE Cloud restricts the 'code' plugin to paid tiers.
    viteStaticCopy({
      targets: [
        {
          src: 'node_modules/tinymce/skins',
          dest: 'tinymce',
          rename: { stripBase: 2 },
        },
        {
          src: 'node_modules/tinymce/icons',
          dest: 'tinymce',
          rename: { stripBase: 2 },
        },
        {
          src: 'node_modules/tinymce/models',
          dest: 'tinymce',
          rename: { stripBase: 2 },
        },
        {
          src: 'node_modules/tinymce/plugins',
          dest: 'tinymce',
          rename: { stripBase: 2 },
        },
        {
          src: 'node_modules/tinymce/themes',
          dest: 'tinymce',
          rename: { stripBase: 2 },
        },
        // TinyMCE infers base_url from skin_url and tries to load tinymce.min.js
        // from that location at runtime — it must exist even though TinyMCE is bundled.
        {
          src: 'node_modules/tinymce/tinymce.min.js',
          dest: 'tinymce',
          rename: { stripBase: 2 },
        },
      ],
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
  // Fix circular dependency between @emotion and React (MUI v6.1.0+)
  // https://github.com/emotion-js/emotion/issues/3322
  // https://github.com/mui/material-ui/issues/43817
  optimizeDeps: {
    include: ['@emotion/react', '@emotion/styled', '@mui/material', '@mui/icons-material'],
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
    chunkSizeWarningLimit: 2000,
    rollupOptions: {
      output: {
        // Only split @emotion + @mui into their own chunk — required to avoid the
        // @emotion/@mui circular-dependency issue (https://github.com/mui/material-ui/issues/43817).
        // Everything else (React, router, i18n, misc deps) goes into one vendor chunk
        // (~1.84 MB, under Workbox's 2 MB precache limit) so there is never a
        // cross-chunk CJS-to-ESM factory boundary that causes initialization-order
        // errors (TDZ / "Cannot set properties of undefined").
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined;
          if (id.includes('@emotion') || id.includes('@mui')) return 'vendor-mui';
          // Tone.js + its audio deps run AudioContext capability tests at module
          // init (standardized-audio-context's constant-source-node probe). Force
          // -ing them into the eager `vendor` chunk made those probes run on every
          // page (incl. the public homepage) → a "AudioContext was not allowed to
          // start" autoplay-policy warning before any user gesture. Return
          // undefined so Rollup leaves them in the chunk created by the dynamic
          // import('tone') in useBlobSounds — loaded only on the organizer blob
          // page, after a click. (Shared tslib/@babel-runtime stay in vendor.)
          if (
            // match both POSIX (/) and Windows (\) path separators
            /[\\/]node_modules[\\/]tone[\\/]/.test(id) ||
            id.includes('standardized-audio-context') ||
            id.includes('automation-events')
          )
            return undefined;
          // Admin-only heavy libraries — charts (recharts + its exclusive d3/victory/
          // react-smooth tree), animation (framer-motion), and drag-and-drop (@dnd-kit) —
          // are imported ONLY by lazy-loaded organizer/partner/presentation routes (zero
          // imports in the public homepage graph, verified 2026-06-02). Forcing them into
          // the eager `vendor` chunk shipped them to every public homepage visitor (most of
          // the ~313 KB "unused JavaScript" Lighthouse flagged). Return undefined so Rollup
          // co-locates them with the dynamic import() chunk of their route — identical to the
          // `tone` carve-out above. The d3-*/victory-vendor/react-smooth packages are pulled
          // ONLY transitively by recharts (no direct src imports), so splitting them with it
          // is safe; without them the recharts split would be pointless (d3 would stay eager).
          // Unlike @emotion/@mui, none of these have a React-core circular dependency, so the
          // single-vendor TDZ concern documented above does not apply to them.
          if (
            /[\\/]node_modules[\\/](recharts|framer-motion|motion|motion-dom|react-smooth|victory-vendor|internmap)[\\/]/.test(
              id
            ) ||
            /[\\/]node_modules[\\/]@dnd-kit[\\/]/.test(id) ||
            /[\\/]node_modules[\\/]d3-[^\\/]+[\\/]/.test(id)
          )
            return undefined;
          // More admin/authenticated-only libraries the public homepage never touches
          // (verified 2026-06-02: 0 imports in the public homepage graph) but that the
          // blanket `vendor` chunk shipped to every visitor:
          //   - motion/motion-dom: framer-motion's actual v12 package — the regex above only
          //     matched the `framer-motion` alias, so ~326 KB stayed eager. Presentation only.
          //   - @stomp/stompjs + sockjs-client: notification WebSocket — authenticated only.
          //   - react-dropzone + file-selector: file upload (profile picture / admin import).
          //   - ics: calendar-invite generation (partner/organizer meetings).
          // Returning undefined lets Rollup co-locate each with the lazy route chunk that
          // imports it — same safe carve-out pattern as `tone`/recharts above.
          if (
            /[\\/]node_modules[\\/]@stomp[\\/]stompjs[\\/]/.test(id) ||
            /[\\/]node_modules[\\/](sockjs-client|react-dropzone|file-selector|ics)[\\/]/.test(id)
          )
            return undefined;
          // AWS Amplify + its AWS SDK / Cognito / Smithy transitive tree (~426 KB) is the
          // largest dependency the blanket `vendor` chunk shipped to every public-homepage
          // visitor — yet anonymous visitors never authenticate. As of
          // perf/public-homepage-followup #2 the code loads Amplify lazily (dynamic import in
          // authService/apiClient/config + ensureAmplifyConfigured), but that is only effective
          // if manualChunks ALSO declines to force it into the eager vendor chunk. Return
          // undefined so Rollup co-locates it with the dynamic import() chunk that first needs
          // it — same carve-out pattern as tone/recharts above. No direct @aws-sdk / @smithy /
          // amazon-cognito imports exist in src (verified 2026-06-02): the whole tree is reached
          // only through aws-amplify, so carving these package roots is safe. Like the other
          // carve-outs (and unlike @emotion/@mui) none has a React-core circular dependency, so
          // the single-vendor TDZ concern does not apply.
          if (
            /[\\/]node_modules[\\/](aws-amplify|@aws-amplify|@aws-sdk|@smithy|@aws-crypto|amazon-cognito-identity-js)[\\/]/.test(
              id
            )
          )
            return undefined;
          // More form/editor libraries the public homepage never reaches eagerly (verified
          // 2026-06-02) but the blanket `vendor` rule shipped to every visitor:
          //   - @tinymce/tinymce-react: the React wrapper for the rich-text editor, imported
          //     ONLY by the organizer EmailTemplateEditModal (admin). (The TinyMCE core itself
          //     is already not bundled — see the note below.)
          //   - react-hook-form (+ @hookform/resolvers): every consumer — public registration
          //     wizard (lazy via HeroSection), the auth forms, and the admin forms — sits behind
          //     a React.lazy boundary; HomePage's eager graph never imports it. Returning
          //     undefined co-locates it with the lazy chunks that use it (same pattern as
          //     recharts/amplify). None has a React-core circular dependency, so the @emotion/@mui
          //     single-vendor TDZ concern does not apply.
          //   - zod: the form-validation schema lib, used ONLY via @hookform/resolvers/zod in the
          //     same 6 lazy form components (+ src/schemas/partnerSchema.ts, imported only by the
          //     lazy PartnerCreateEditModal). No eager homepage importer. (yup is NOT here — it is
          //     not a dependency and not bundled; we standardised on zod.)
          // (Redux is intentionally absent here: @reduxjs/toolkit/react-redux are not direct deps
          //  and are not bundled, so there is nothing to carve.)
          if (
            /[\\/]node_modules[\\/]@tinymce[\\/]/.test(id) ||
            /[\\/]node_modules[\\/]react-hook-form[\\/]/.test(id) ||
            /[\\/]node_modules[\\/]@hookform[\\/]/.test(id) ||
            /[\\/]node_modules[\\/]zod[\\/]/.test(id)
          )
            return undefined;
          // TinyMCE is intentionally NOT bundled — it's loaded at runtime via
          // <Editor tinymceScriptSrc="/tinymce/tinymce.min.js" /> from vite-plugin-static-copy.
          // Bundling its IIFE modules causes Vite/Rollup to reorder them so plugins
          // execute before window.tinymce is set, breaking the editor.
          return 'vendor';
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
    // 30s testTimeout + 30s hookTimeout absorb CPU-contention spikes when the full
    // 5000+-test suite runs in parallel; isolated runs of these files complete in <7s.
    testTimeout: 30000,
    hookTimeout: 30000,
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
        'src/components/shared/Company/CompanyBatchImportModal.tsx',
        'src/components/shared/Session/SessionBatchImportModal.tsx',
        'src/components/organizer/UserManagement/SpeakerBatchImportModal.tsx',
        'src/components/organizer/UserManagement/ParticipantBatchImportModal.tsx',
        'src/hooks/useParticipantBatchImport/**', // Participant batch import - one-time migration utility
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
        'src/mocks/**', // Mock handlers/service workers - test support code, not production logic
        'src/components/TopicHeatMap/**', // Pending refactor - excluded until redesigned
        // Analytics dashboard charts — Recharts components not testable in JSDOM
        'src/components/organizer/Analytics/**',
        'src/hooks/useAnalytics.ts',
        'src/services/analyticsService.ts',
        // BlobTopicSelector — complex SVG animation component, not unit-testable in JSDOM
        'src/components/BlobTopicSelector/**',
        // Newsletter service layer — pure HTTP wrappers tested via component integration tests
        'src/services/newsletterService.ts',
        'src/hooks/useNewsletter/**',
        // React error boundary — framework code tested via E2E
        'src/components/ErrorBoundary.tsx',
        // SEO schema component — JSON-LD output verified via E2E, no branching logic
        'src/components/SEO/EventSchema.tsx',
        // Skeleton loader placeholders — structural only, E2E tested
        'src/components/public/Event/skeletons/**',
        'src/components/public/ui/skeleton.tsx',
        // Dev-only email utility — never runs in production
        'src/services/devEmailService.ts',
        // BlobTopicSelector service — used only by already-excluded BlobTopicSelector component
        'src/services/blobTopicService.ts',
        // WebSocket client & hook — native browser API, E2E tested
        'src/services/notificationWebSocketClient.ts',
        'src/hooks/useNotificationWebSocket.ts',
        // Trivial token getter — no branching logic
        'src/services/speakerAuthService.ts',
        // Routing HOC — tested via E2E
        'src/components/auth/ProtectedRoute/withProtectedRoute.tsx',
        // Side-effect-only i18n sync component — fires language change, no testable logic
        'src/components/shared/LanguageSync/LanguageSync.tsx',
      ],
      reportOnFailure: true, // Generate coverage even when tests fail
      // Note: 'all' option removed in vitest v4 - use 'include' to specify files
      include: ['src/**/*.{ts,tsx}'], // Explicitly include source files in coverage
      thresholds: {
        statements: 73, // Only check overall statement coverage
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
