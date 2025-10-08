import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.tsx';
import './index.css'; // Tailwind CSS
import './i18n/config'; // Initialize i18n before rendering
import { logWebVitals, sendWebVitalsToAnalytics } from './utils/performance/reportWebVitals';
import { registerSW } from 'virtual:pwa-register'; // Vite PWA plugin (Task 14b)
import { loadRuntimeConfig } from './config/runtime-config';
import { ConfigProvider } from './contexts/ConfigContext';

/**
 * Loading Screen Component
 * Shown while fetching runtime configuration from backend
 */
const LoadingScreen = () => (
  <div
    style={{
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'center',
      alignItems: 'center',
      height: '100vh',
      fontFamily: 'system-ui, -apple-system, sans-serif',
    }}
  >
    <div style={{ fontSize: '24px', marginBottom: '16px' }}>Loading BATbern...</div>
    <div
      style={{
        width: '200px',
        height: '4px',
        backgroundColor: '#e0e0e0',
        borderRadius: '2px',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          width: '40%',
          height: '100%',
          backgroundColor: '#D52B1E',
          animation: 'loading 1.5s ease-in-out infinite',
        }}
      />
    </div>
    <style>{`
      @keyframes loading {
        0% { transform: translateX(-100%); }
        100% { transform: translateX(350%); }
      }
    `}</style>
  </div>
);

/**
 * Error Screen Component
 * Shown if configuration loading fails
 */
const ErrorScreen = ({ error }: { error: Error }) => (
  <div
    style={{
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'center',
      alignItems: 'center',
      height: '100vh',
      padding: '20px',
      fontFamily: 'system-ui, -apple-system, sans-serif',
    }}
  >
    <div
      style={{
        maxWidth: '500px',
        padding: '24px',
        backgroundColor: '#fee',
        border: '1px solid #fcc',
        borderRadius: '8px',
      }}
    >
      <h2 style={{ margin: '0 0 12px 0', color: '#c00' }}>Configuration Load Failed</h2>
      <p style={{ margin: '0 0 16px 0', color: '#666' }}>{error.message}</p>
      <button
        onClick={() => window.location.reload()}
        style={{
          padding: '10px 20px',
          backgroundColor: '#D52B1E',
          color: 'white',
          border: 'none',
          borderRadius: '4px',
          cursor: 'pointer',
          fontSize: '14px',
          fontWeight: '500',
        }}
      >
        Reload Page
      </button>
    </div>
  </div>
);

/**
 * Bootstrap Application
 * Loads runtime configuration before rendering the app
 */
async function bootstrap() {
  const root = ReactDOM.createRoot(document.getElementById('root')!);

  // Show loading screen
  root.render(<LoadingScreen />);

  try {
    // Load runtime config from backend API
    const config = await loadRuntimeConfig();

    console.log('[Bootstrap] Configuration loaded successfully');

    // Render app with configuration
    root.render(
      <React.StrictMode>
        <ConfigProvider config={config}>
          <App />
        </ConfigProvider>
      </React.StrictMode>
    );
  } catch (error) {
    console.error('[Bootstrap] Failed to load configuration:', error);

    // Show error screen
    root.render(<ErrorScreen error={error as Error} />);
  }
}

// Start the application
bootstrap();

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
