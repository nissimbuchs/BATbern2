import type { AppConfig } from '../config/runtime-config';
import { useConfig } from './useConfig';

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
