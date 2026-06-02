// App entry point / bootstrap.

import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.tsx';
import './index.css'; // Tailwind CSS
import './i18n/config'; // Initialize i18n before rendering
import { logWebVitals, sendWebVitalsToAnalytics } from './utils/performance/reportWebVitals';
import { registerSW } from 'virtual:pwa-register'; // Vite PWA plugin (Task 14b)
import { getDefaultApiBaseUrl } from './config/runtime-config';
import { updateApiClientConfig } from './services/api/apiClient';
import { ConfigProvider } from './contexts/ConfigContext';
import { ErrorBoundary } from './components/ErrorBoundary'; // Task 4: Error boundaries

/**
 * Bootstrap Application
 *
 * Renders the app shell IMMEDIATELY — no blocking `await loadRuntimeConfig()`, no
 * full-screen bootstrap spinner. The previous bespoke red progress-bar `LoadingScreen`
 * has been removed: the public shell needs no runtime config to paint, and the BATbern
 * logo spinner (`BATbernLoader`) is used only for the genuinely slow data regions
 * (e.g. the homepage event block). This is the structural prerequisite for prerendering
 * the public routes (see docs/plans/public-homepage-prerender.md).
 *
 * Runtime config (Cognito, feature flags) loads in the background via <ConfigProvider>;
 * the API base URL is set synchronously below so public data fetches fire in parallel
 * with — not after — the /api/v1/config round-trip.
 */

// Give the API client a correct base URL right away. getDefaultApiBaseUrl() is the
// hostname-derived `<host>/api/v1`, byte-identical to the backend's config value in
// prod. <ConfigProvider> overrides it with the authoritative value once config resolves.
updateApiClientConfig(getDefaultApiBaseUrl());

const container = document.getElementById('root')!;

// createRoot (not hydrateRoot) is intentional: for prerendered routes the static HTML
// paints first (the FCP/LCP win) and React mounts fresh and replaces it ("paint-and-
// replace"), which avoids hydration-mismatch risk across our 10-locale i18n. For normal
// SPA loads the container is empty and this is a plain mount.
ReactDOM.createRoot(container).render(
  <React.StrictMode>
    <ErrorBoundary>
      <ConfigProvider>
        <App />
      </ConfigProvider>
    </ErrorBoundary>
  </React.StrictMode>
);

// Report Core Web Vitals (Task 13b)
if (import.meta.env.DEV) {
  // Log to console in development
  logWebVitals();
} else {
  // Send to analytics in production
  sendWebVitalsToAnalytics();
}

// Register Service Worker for PWA (Task 14b)
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  const updateSW = registerSW({
    onNeedRefresh() {
      // Show prompt to user to reload the page for updates
      if (confirm('New version available! Reload to update?')) {
        updateSW(true);
      }
    },
    onOfflineReady() {
      console.log('App is ready to work offline');
    },
    onRegistered(registration) {
      console.log('Service Worker registered:', registration);
      // Check for updates every hour
      setInterval(
        () => {
          registration?.update();
        },
        60 * 60 * 1000
      );
    },
    onRegisterError(error) {
      console.error('Service Worker registration error:', error);
    },
  });
}
