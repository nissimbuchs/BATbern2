import { useContext } from 'react';
import type { AppConfig } from '../config/runtime-config';
import { ConfigContext } from './createConfigContext';

/**
 * Hook to access runtime configuration
 *
 * Use this hook in any component that needs access to environment-specific
 * configuration like API endpoints, authentication settings, or feature flags.
 *
 * @returns Runtime configuration
 * @throws Error if used outside ConfigProvider
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const config = useConfig();
 *   const apiUrl = config.apiBaseUrl;
 *   const isAnalyticsEnabled = config.features.analytics;
 *   // ...
 * }
 * ```
 */
export function useConfig(): AppConfig {
  const config = useContext(ConfigContext);

  if (!config) {
    throw new Error(
      'useConfig must be used within ConfigProvider. ' +
        'Ensure that your component is wrapped in <ConfigProvider>.'
    );
  }

  return config;
}

/**
 * Hook to access runtime configuration WITHOUT throwing when it is not yet loaded.
 *
 * Since the config gate was decoupled (the app shell now renders before
 * `GET /api/v1/config` resolves), components that may render during that brief window
 * — notably `useTurnstile`, which sits on the eager public homepage path via the
 * newsletter widget — must tolerate a null config and re-render when it arrives.
 * Treat `null` as "config not ready yet" (e.g. feature flags default to off).
 *
 * @returns Runtime configuration, or `null` if it has not loaded yet.
 */
export function useOptionalConfig(): AppConfig | null {
  return useContext(ConfigContext);
}
