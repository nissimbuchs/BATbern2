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
