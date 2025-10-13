import type { AppConfig } from '../config/runtime-config';
import { useConfig } from './useConfig';

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
