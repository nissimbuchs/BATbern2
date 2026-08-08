import React, { useEffect, useState } from 'react';
import type { AppConfig } from '../config/runtime-config';
import { loadRuntimeConfig, resolveApiBaseUrl } from '../config/runtime-config';
import { updateApiClientConfig } from '../services/api/apiClient';
import {
  setAmplifyRuntimeConfig,
  expectAmplifyRuntimeConfig,
  cancelExpectedAmplifyRuntimeConfig,
} from '../config/amplify';
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
    // 12.8 F8: announce the in-flight fetch so ensureAmplifyConfigured() WAITS for it
    // instead of silently no-op'ing (auth-touching calls — e.g. the /auth/callback code
    // exchange — raced this round-trip and left Amplify unconfigured forever).
    expectAmplifyRuntimeConfig();
    loadRuntimeConfig()
      .then((cfg) => {
        // Module-level stashes run regardless of unmount — config arrival is a fact, and
        // skipping them would strand the waiters inside ensureAmplifyConfigured().
        // resolveApiBaseUrl() keeps the backend's value in prod and stays same-origin
        // under `vite dev`, where the advertised absolute localhost URL would resolve
        // against the viewer's machine rather than the dev host.
        updateApiClientConfig(resolveApiBaseUrl(cfg));
        setAmplifyRuntimeConfig(cfg);
        if (cancelled) return;
        setLoadedConfig(cfg);
      })
      .catch((error) => {
        // Non-fatal. Public pages render and fetch via the hostname-derived base URL
        // set at bootstrap; auth/feature-flag UI that needs config surfaces its own
        // error. Do NOT blank the whole app (the old behaviour) — that would defeat
        // "show the page immediately".
        cancelExpectedAmplifyRuntimeConfig();
        console.error('[Config] Runtime config unavailable; continuing without it:', error);
      });

    return () => {
      cancelled = true;
    };
  }, [providedConfig]);

  const value = providedConfig ?? loadedConfig;

  return <ConfigContext.Provider value={value}>{children}</ConfigContext.Provider>;
}
