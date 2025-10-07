import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.tsx';
import './index.css'; // Tailwind CSS
import './i18n/config'; // Initialize i18n before rendering
import { logWebVitals, sendWebVitalsToAnalytics } from './utils/performance/reportWebVitals';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
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
