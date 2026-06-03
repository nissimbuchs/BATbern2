import React, { useEffect, useState } from 'react';
import type { AppConfig } from '../config/runtime-config';
import { loadRuntimeConfig } from '../config/runtime-config';
import { updateApiClientConfig } from '../services/api/apiClient';
import { setAmplifyRuntimeConfig } from '../config/amplify';
import { ConfigContext } from './createConfigContext';

export interface ConfigProviderProps {
  /**
   * Explicit configuration. Primarily for tests / Storybook. When supplied, the
   * provider uses it directly and performs NO runtime fetch (preserves the original
   * prop-driven behaviour).
   */
  config?: AppConfig;
  children: React.ReactNode;
}

/**
 * Configuration Provider Component
 *
 * Renders its children IMMEDIATELY (the app shell paints without waiting for the
 * `GET /api/v1/config` round-trip), then loads runtime configuration in the
 * background. On resolve it wires the API client + Amplify and publishes the config
 * via context.
 *
 * Decoupling rationale (perf/public-homepage-followup):
 * - The public shell consumes no config; the API base URL is set synchronously at
 *   bootstrap (see main.tsx → getDefaultApiBaseUrl), so public data loads in parallel.
 * - `useConfig()` still throws if it reads a null config, but its only public-path
 *   consumer (`useTurnstile`) uses `useOptionalConfig()` and treats null as "not ready".
 * - A config-load failure is NON-fatal: public pages keep working; we never blank the
 *   whole app on it (unlike the old blocking bootstrap).
 */
export function ConfigProvider({ config: providedConfig, children }: ConfigProviderProps) {
  const [loadedConfig, setLoadedConfig] = useState<AppConfig | null>(null);

  useEffect(() => {
    // Explicit config supplied (tests) — skip the runtime fetch entirely.
    if (providedConfig) return;

    let cancelled = false;
    loadRuntimeConfig()
      .then((cfg) => {
        if (cancelled) return;
        updateApiClientConfig(cfg.apiBaseUrl);
        setAmplifyRuntimeConfig(cfg);
        setLoadedConfig(cfg);
      })
      .catch((error) => {
        // Non-fatal. Public pages render and fetch via the hostname-derived base URL
        // set at bootstrap; auth/feature-flag UI that needs config surfaces its own
        // error. Do NOT blank the whole app (the old behaviour) — that would defeat
        // "show the page immediately".
        console.error('[Config] Runtime config unavailable; continuing without it:', error);
      });

    return () => {
      cancelled = true;
    };
  }, [providedConfig]);

  const value = providedConfig ?? loadedConfig;

  return <ConfigContext.Provider value={value}>{children}</ConfigContext.Provider>;
}
