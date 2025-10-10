/**
 * Web Vitals Reporting Utility
 * Story 1.17, Task 13b: Core Web Vitals tracking
 *
 * Tracks and reports Core Web Vitals metrics:
 * - LCP (Largest Contentful Paint) - Target: < 2.5s
 * - INP (Interaction to Next Paint) - Target: < 200ms (replaces FID in web-vitals v5)
 * - CLS (Cumulative Layout Shift) - Target: < 0.1
 * - FCP (First Contentful Paint) - Target: < 1.5s
 * - TTFB (Time to First Byte) - Target: < 600ms
 */

import { onCLS, onINP, onFCP, onLCP, onTTFB, Metric } from 'web-vitals';

export interface WebVitalsMetric {
  name: string;
  value: number;
  id: string;
  delta: number;
  rating: 'good' | 'needs-improvement' | 'poor';
}

type ReportHandler = (metric: WebVitalsMetric) => void;

/**
 * Reports Core Web Vitals metrics
 * @param onPerfEntry - Callback function to handle metric reporting
 *
 * @example
 * ```ts
 * reportWebVitals((metric) => {
 *   console.log(metric.name, metric.value);
 *   // Send to analytics service
 *   analytics.track('web_vitals', {
 *     name: metric.name,
 *     value: metric.value,
 *     rating: metric.rating
 *   });
 * });
 * ```
 */
export function reportWebVitals(onPerfEntry?: ReportHandler): void {
  if (onPerfEntry && typeof onPerfEntry === 'function') {
    const handleMetric = (metric: Metric) => {
      onPerfEntry({
        name: metric.name,
        value: metric.value,
        id: metric.id,
        delta: metric.delta,
        rating: metric.rating,
      });
    };

    // Measure and report Core Web Vitals (web-vitals v5 API)
    onCLS(handleMetric); // Cumulative Layout Shift
    onINP(handleMetric); // Interaction to Next Paint (replaces FID)
    onFCP(handleMetric); // First Contentful Paint
    onLCP(handleMetric); // Largest Contentful Paint
    onTTFB(handleMetric); // Time to First Byte
  }
}

/**
 * Sends web vitals to console (development mode)
 */
export function logWebVitals(): void {
  reportWebVitals((metric) => {
    console.log(`[Web Vitals] ${metric.name}:`, {
      value: `${metric.value.toFixed(2)}ms`,
      rating: metric.rating,
      id: metric.id,
    });
  });
}

/**
 * Sends web vitals to analytics service (production mode)
 * TODO: Integrate with actual analytics service (Google Analytics, Sentry, etc.)
 */
export function sendWebVitalsToAnalytics(): void {
  reportWebVitals((metric) => {
    // TODO: Replace with actual analytics integration
    // Example for Google Analytics 4:
    // window.gtag?.('event', metric.name, {
    //   value: Math.round(metric.name === 'CLS' ? metric.value * 1000 : metric.value),
    //   metric_id: metric.id,
    //   metric_value: metric.value,
    //   metric_delta: metric.delta,
    //   metric_rating: metric.rating,
    // });

    // For now, just log to console in production
    if (import.meta.env.PROD) {
      console.log(`[Analytics] ${metric.name}:`, metric.value, metric.rating);
    }
  });
}

export default reportWebVitals;
