import type { AppConfig } from '../config/runtime-config';
import { useOptionalConfig } from './useConfig';

/**
 * Hook to check if a feature is enabled
 *
 * Convenience hook for checking feature flags.
 *
 * Safe before runtime config arrives. <ConfigProvider> renders its children immediately
 * with a null value and fills the config in once GET /api/v1/config resolves, so any
 * component rendering in that window reads null. Using the throwing useConfig() here
 * meant a cold deep-link to /login or /register crashed into the ErrorBoundary with
 * "useConfig must be used within ConfigProvider" despite the provider being present —
 * LoginForm and RegistrationStep1 both call useFeature('sso') on first render.
 *
 * While the flag is unknown it reports false: a feature flag must fail CLOSED. Briefly
 * hiding an SSO button is harmless; showing one the backend has switched off is not.
 *
 * @param featureName - Name of the feature to check
 * @returns true if the feature is enabled; false if disabled OR config has not loaded yet
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
  const config = useOptionalConfig();
  return config?.features[featureName] ?? false;
}
