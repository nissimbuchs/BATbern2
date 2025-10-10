import React, { createContext, useContext } from 'react';
import type { AppConfig } from '../config/runtime-config';

/**
 * Configuration Context
 *
 * Provides runtime configuration to all components in the app.
 * Configuration is loaded once at app startup and made available
 * throughout the component tree.
 */
const ConfigContext = createContext<AppConfig | null>(null);

export interface ConfigProviderProps {
  config: AppConfig;
  children: React.ReactNode;
}

/**
 * Configuration Provider Component
 *
 * Wraps the entire app to provide runtime configuration to all components.
 * Must be rendered after configuration is loaded.
 *
 * @param config - Runtime configuration loaded from backend
 * @param children - App components
 */
export function ConfigProvider({ config, children }: ConfigProviderProps) {
  return <ConfigContext.Provider value={config}>{children}</ConfigContext.Provider>;
}

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
 * Hook to check if a feature is enabled
 *
 * Convenience hook for checking feature flags.
 *
 * @param featureName - Name of the feature to check
 * @returns true if feature is enabled, false otherwise
 *
 * @example
 * ```tsx
 * function Analytics() {
 *   const analyticsEnabled = useFeature('analytics');
 *   if (!analyticsEnabled) return null;
 *   return <AnalyticsComponent />;
 * }
 * ```
 */
export function useFeature(featureName: keyof AppConfig['features']): boolean {
  const config = useConfig();
  return config.features[featureName];
}

/**
 * Hook to get environment name
 *
 * @returns Current environment: 'development', 'staging', or 'production'
 *
 * @example
 * ```tsx
 * function DebugPanel() {
 *   const env = useEnvironment();
 *   if (env === 'production') return null;
 *   return <DebugInfo />;
 * }
 * ```
 */
export function useEnvironment(): AppConfig['environment'] {
  const config = useConfig();
  return config.environment;
}
