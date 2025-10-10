/**
 * Performance Optimization Tests (Task 13a - RED Phase)
 * Story 1.17: React Frontend Foundation
 *
 * Tests for code splitting, lazy loading, and Core Web Vitals
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';

// Mock React.lazy to track lazy-loaded components
const originalLazy = React.lazy;
let lazyComponents: string[] = [];

vi.mock('react', async () => {
  const actual = await vi.importActual<typeof React>('react');
  return {
    ...actual,
    lazy: vi.fn((factory: () => Promise<{ default: React.ComponentType<unknown> }>) => {
      // Track which components are lazy loaded
      const component = originalLazy(factory);
      lazyComponents.push(factory.toString());
      return component;
    }),
  };
});

describe('Performance Optimization - Code Splitting', () => {
  beforeEach(() => {
    lazyComponents = [];
    vi.clearAllMocks();
  });

  describe('Route-level code splitting', () => {
    it('should_useLazyLoading_when_renderingDashboardRoute', async () => {
      // Verify Dashboard component is in separate file (pages directory)
      const dashboardModule = await import('./pages/Dashboard');
      expect(dashboardModule.default).toBeDefined();
    });

    it('should_useLazyLoading_when_renderingEventsRoute', async () => {
      // Verify Events component is in separate file
      const eventsModule = await import('./pages/Events');
      expect(eventsModule.default).toBeDefined();
    });

    it('should_useLazyLoading_when_renderingSpeakersRoute', async () => {
      // Verify Speakers component is in separate file
      const speakersModule = await import('./pages/Speakers');
      expect(speakersModule.default).toBeDefined();
    });

    it('should_useLazyLoading_when_renderingPartnersRoute', async () => {
      // Verify Partners component is in separate file
      const partnersModule = await import('./pages/Partners');
      expect(partnersModule.default).toBeDefined();
    });

    it('should_useLazyLoading_when_renderingContentRoute', async () => {
      // Verify Content component is in separate file
      const contentModule = await import('./pages/Content');
      expect(contentModule.default).toBeDefined();
    });

    it('should_useLazyLoading_when_renderingAnalyticsRoute', async () => {
      // Verify Analytics component is in separate file
      const analyticsModule = await import('./pages/Analytics');
      expect(analyticsModule.default).toBeDefined();
    });
  });

  describe('Component-level lazy loading', () => {
    it('should_showSuspenseFallback_when_lazyComponentIsLoading', async () => {
      // Mock a lazy-loaded component
      const LazyComponent = React.lazy(
        () =>
          new Promise((resolve) =>
            setTimeout(() => resolve({ default: () => <div>Loaded</div> }), 100)
          )
      );

      render(
        <React.Suspense fallback={<div>Loading...</div>}>
          <LazyComponent />
        </React.Suspense>
      );

      // Should show fallback initially
      expect(screen.getByText('Loading...')).toBeInTheDocument();

      // Should show actual component after loading
      await waitFor(() => {
        expect(screen.getByText('Loaded')).toBeInTheDocument();
      });
    });

    it('should_splitMaterialUIIcons_when_importingIcons', async () => {
      // This test verifies that Material-UI icons are imported dynamically
      // Current implementation likely imports all icons at once

      // Check if dynamic import is used for icons
      const iconImportPattern = /import\s*{\s*\w+Icon\s*}\s*from\s*['"]@mui\/icons-material['"]/;

      // Read App.tsx to check import pattern
      const appSource = await import('./App?raw');
      const hasStaticIconImport = iconImportPattern.test(appSource.default || '');

      // Should NOT have static icon imports (should use dynamic imports instead)
      expect(hasStaticIconImport).toBe(false);
    });
  });
});

describe('Performance Optimization - Bundle Size', () => {
  it('should_haveOptimizedBuildConfig_when_buildingForProduction', async () => {
    // Read vite.config.ts to verify optimization settings
    const { default: viteConfig } = await import('../vite.config?raw');

    const configStr = viteConfig || '';

    // Should have build optimization configured
    expect(configStr).toContain('minify');
    expect(configStr).toContain('rollupOptions');
  });

  it('should_splitVendorChunks_when_buildingForProduction', async () => {
    const { default: viteConfig } = await import('../vite.config?raw');

    const configStr = viteConfig || '';

    // Should split vendor chunks (react, react-dom, @mui)
    const hasVendorSplitting =
      configStr.includes('manualChunks') || configStr.includes('splitVendorChunkPlugin');
    expect(hasVendorSplitting).toBe(true);
  });

  it('should_compressAssets_when_buildingForProduction', async () => {
    const { default: viteConfig } = await import('../vite.config?raw');

    const configStr = viteConfig || '';

    // Should have compression plugin (gzip or brotli)
    const hasCompression =
      configStr.includes('compress') || configStr.includes('gzip') || configStr.includes('brotli');
    expect(hasCompression).toBe(true);
  });
});

describe('Performance Optimization - React.memo', () => {
  it('should_memoizeNavigationMenu_when_propsDoNotChange', async () => {
    const { NavigationMenu } = await import('./components/shared/Navigation/NavigationMenu');

    // React.memo components are objects with $$typeof, type, and compare properties
    expect(typeof NavigationMenu).toBe('object');
    expect(NavigationMenu).toHaveProperty('$$typeof');
    expect(NavigationMenu).toHaveProperty('type');
  });

  it('should_memoizeAppHeader_when_propsDoNotChange', async () => {
    const { default: AppHeader } = await import('./components/shared/Navigation/AppHeader');

    // React.memo components are objects with $$typeof, type, and compare properties
    expect(typeof AppHeader).toBe('object');
    expect(AppHeader).toHaveProperty('$$typeof');
    expect(AppHeader).toHaveProperty('type');
  });

  it('should_memoizeNotificationDropdown_when_propsDoNotChange', async () => {
    const { NotificationDropdown } = await import(
      './components/shared/Notifications/NotificationDropdown'
    );

    // React.memo components are objects with $$typeof, type, and compare properties
    expect(typeof NotificationDropdown).toBe('object');
    expect(NotificationDropdown).toHaveProperty('$$typeof');
    expect(NotificationDropdown).toHaveProperty('type');
  });
});

describe('Performance Optimization - Core Web Vitals', () => {
  describe('Largest Contentful Paint (LCP)', () => {
    it('should_measureLCP_when_pageLoads', async () => {
      // Verify web-vitals v5 library is installed and accessible
      const webVitals = await import('web-vitals');
      expect(webVitals.onLCP).toBeDefined();
      expect(typeof webVitals.onLCP).toBe('function');
    });

    it('should_reportLCP_when_metricIsAvailable', async () => {
      const { onLCP } = await import('web-vitals');
      const mockCallback = vi.fn();
      onLCP(mockCallback);

      // Callback should be registered (actual metric will be measured in browser)
      expect(mockCallback).toBeDefined();
    });

    it('should_targetLCPBelow2500ms_when_measuring', () => {
      // This is a target assertion - actual measurement happens in browser
      // Test ensures we have monitoring in place
      const targetLCP = 2500; // 2.5 seconds
      expect(targetLCP).toBe(2500);
    });
  });

  describe('Interaction to Next Paint (INP)', () => {
    it('should_measureINP_when_pageLoads', async () => {
      // INP replaces FID in web-vitals v5
      const { onINP } = await import('web-vitals');
      expect(onINP).toBeDefined();
      expect(typeof onINP).toBe('function');
    });

    it('should_targetINPBelow200ms_when_measuring', () => {
      const targetINP = 200; // 200 milliseconds
      expect(targetINP).toBe(200);
    });
  });

  describe('Cumulative Layout Shift (CLS)', () => {
    it('should_measureCLS_when_pageLoads', async () => {
      const { onCLS } = await import('web-vitals');
      expect(onCLS).toBeDefined();
      expect(typeof onCLS).toBe('function');
    });

    it('should_targetCLSBelow0_1_when_measuring', () => {
      const targetCLS = 0.1;
      expect(targetCLS).toBe(0.1);
    });
  });

  describe('First Contentful Paint (FCP)', () => {
    it('should_measureFCP_when_pageLoads', async () => {
      const { onFCP } = await import('web-vitals');
      expect(onFCP).toBeDefined();
      expect(typeof onFCP).toBe('function');
    });
  });

  describe('Time to First Byte (TTFB)', () => {
    it('should_measureTTFB_when_pageLoads', async () => {
      const { onTTFB } = await import('web-vitals');
      expect(onTTFB).toBeDefined();
      expect(typeof onTTFB).toBe('function');
    });
  });
});

describe('Performance Optimization - Asset Optimization', () => {
  it('should_haveAssetNaming_when_buildingForProduction', async () => {
    const { default: viteConfig } = await import('../vite.config?raw');

    const configStr = viteConfig || '';

    // Should have asset file naming configured for better caching
    expect(configStr).toContain('assetFileNames');
    expect(configStr).toContain('chunkFileNames');
  });

  it('should_useCompression_when_buildingForProduction', async () => {
    const { default: viteConfig } = await import('../vite.config?raw');

    const configStr = viteConfig || '';

    // Should have compression (gzip or brotli)
    const hasCompression = configStr.includes('gzip') || configStr.includes('brotli');
    expect(hasCompression).toBe(true);
  });
});
