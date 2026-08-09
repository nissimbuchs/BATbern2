// @vitest-environment jsdom
/**
 * runtime-config.ts Tests
 *
 * Coverage for loadRuntimeConfig, validateConfig, clearConfigCache,
 * getApiUrl, isDevelopmentEnvironment, and getDefaultDevelopmentConfig.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  loadRuntimeConfig,
  clearConfigCache,
  getDefaultApiBaseUrl,
  resolveApiBaseUrl,
  type AppConfig,
} from './runtime-config';

const validConfig: AppConfig = {
  environment: 'staging',
  apiBaseUrl: 'https://api.batbern.ch/api/v1',
  cognito: { userPoolId: 'eu-central-1_abc', clientId: 'client-id', region: 'eu-central-1' },
  features: { notifications: true, analytics: true, pwa: true, turnstile: false, sso: false },
};

describe('runtime-config', () => {
  let fetchSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    clearConfigCache();
    fetchSpy = vi.spyOn(globalThis, 'fetch');
    vi.spyOn(console, 'log').mockImplementation(() => {});
    vi.spyOn(console, 'error').mockImplementation(() => {});
    vi.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ---- loadRuntimeConfig: success path
  it('loads config from backend and caches it', async () => {
    fetchSpy.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve(validConfig),
    } as Response);

    const config = await loadRuntimeConfig();

    expect(config.environment).toBe('staging');
    expect(config.features.turnstile).toBe(false);
    expect(config.features.sso).toBe(false); // Story 12.9: sso flag flows through the config contract
    expect(fetchSpy).toHaveBeenCalledTimes(1);

    // Second call returns cached — no second fetch
    const cached = await loadRuntimeConfig();
    expect(cached).toBe(config);
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it('loads config with optional turnstile block', async () => {
    const configWithTurnstile = { ...validConfig, turnstile: { siteKey: 'site-key-abc' } };
    fetchSpy.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve(configWithTurnstile),
    } as Response);

    const config = await loadRuntimeConfig();

    expect(config.turnstile?.siteKey).toBe('site-key-abc');
  });

  // ---- loadRuntimeConfig: HTTP error on localhost → fallback
  it('falls back to default dev config when fetch fails on localhost', async () => {
    // JSDOM defaults to localhost
    fetchSpy.mockRejectedValueOnce(new Error('Network error'));

    const config = await loadRuntimeConfig();

    expect(config.environment).toBe('development');
    expect(config.apiBaseUrl).toBe('http://localhost:8000/api/v1');
    expect(config.features.turnstile).toBe(false);
  });

  it('falls back to default dev config when response is not ok on localhost', async () => {
    fetchSpy.mockResolvedValueOnce({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
    } as Response);

    const config = await loadRuntimeConfig();

    expect(config.environment).toBe('development');
  });

  // ---- validateConfig: invalid inputs
  it('throws for null config', async () => {
    fetchSpy.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve(null),
    } as Response);

    // On localhost this falls back to dev config instead of throwing
    const config = await loadRuntimeConfig();
    expect(config.environment).toBe('development');
  });

  it('throws for config missing environment', async () => {
    fetchSpy.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ apiBaseUrl: 'x', cognito: {}, features: {} }),
    } as Response);

    const config = await loadRuntimeConfig();
    expect(config.environment).toBe('development'); // fallback on localhost
  });

  it('throws for config missing apiBaseUrl', async () => {
    fetchSpy.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ environment: 'staging', cognito: {}, features: {} }),
    } as Response);

    const config = await loadRuntimeConfig();
    expect(config.environment).toBe('development');
  });

  it('throws for config missing cognito', async () => {
    fetchSpy.mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ environment: 'staging', apiBaseUrl: 'x', features: {} }),
    } as Response);

    const config = await loadRuntimeConfig();
    expect(config.environment).toBe('development');
  });

  it('throws for config with incomplete cognito', async () => {
    fetchSpy.mockResolvedValueOnce({
      ok: true,
      json: () =>
        Promise.resolve({
          environment: 'staging',
          apiBaseUrl: 'x',
          cognito: { userPoolId: 'x' },
          features: {},
        }),
    } as Response);

    const config = await loadRuntimeConfig();
    expect(config.environment).toBe('development');
  });

  it('throws for config missing features', async () => {
    fetchSpy.mockResolvedValueOnce({
      ok: true,
      json: () =>
        Promise.resolve({
          environment: 'staging',
          apiBaseUrl: 'x',
          cognito: { userPoolId: 'x', clientId: 'y', region: 'z' },
        }),
    } as Response);

    const config = await loadRuntimeConfig();
    expect(config.environment).toBe('development');
  });

  // ---- API base URL resolution (dev must never reach production)
  describe('API base URL resolution', () => {
    // getApiUrl() used to send EVERY non-localhost hostname to https://api.batbern.ch.
    // Browsing a `make dev-native-up` frontend by LAN IP therefore aimed a dev UI at
    // PRODUCTION (batbern-staging serves www.batbern.ch) — only CORS stopped it.
    //
    // Under `vite dev` the base URL is now same-origin, so /api goes through the Vite
    // proxy to whichever gateway that dev server is configured for. Production builds
    // (import.meta.env.DEV === false) keep the original hostname-derived behaviour.

    it('should_useSameOriginRelativeUrl_when_runningUnderViteDev', () => {
      vi.stubEnv('DEV', true);
      // A LAN IP is the case that used to fall through to production.
      vi.spyOn(window, 'location', 'get').mockReturnValue({
        ...window.location,
        hostname: '192.168.1.37',
      } as Location);

      expect(getDefaultApiBaseUrl()).toBe('/api/v1');
      expect(getDefaultApiBaseUrl()).not.toContain('api.batbern.ch');

      vi.unstubAllEnvs();
    });

    it('should_keepProductionHost_when_notRunningUnderViteDev', () => {
      vi.stubEnv('DEV', false);
      vi.spyOn(window, 'location', 'get').mockReturnValue({
        ...window.location,
        hostname: 'www.batbern.ch',
      } as Location);

      expect(getDefaultApiBaseUrl()).toBe('https://api.batbern.ch/api/v1');

      vi.unstubAllEnvs();
    });

    it('should_adoptBackendApiBaseUrl_when_notRunningUnderViteDev', () => {
      vi.stubEnv('DEV', false);

      expect(resolveApiBaseUrl(validConfig)).toBe('https://api.batbern.ch/api/v1');

      vi.unstubAllEnvs();
    });

    it('should_ignoreBackendApiBaseUrl_when_runningUnderViteDev', () => {
      // The backend advertises an absolute http://localhost:{port}/api/v1. "localhost"
      // is resolved by the BROWSER, so adopting it breaks every viewer that is not
      // sitting on the dev host itself.
      vi.stubEnv('DEV', true);
      const devConfig: AppConfig = { ...validConfig, apiBaseUrl: 'http://localhost:8000/api/v1' };

      expect(resolveApiBaseUrl(devConfig)).toBe('/api/v1');

      vi.unstubAllEnvs();
    });
  });

  // ---- clearConfigCache
  it('clearConfigCache forces a fresh fetch', async () => {
    fetchSpy.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(validConfig),
    } as Response);

    await loadRuntimeConfig();
    expect(fetchSpy).toHaveBeenCalledTimes(1);

    clearConfigCache();
    await loadRuntimeConfig();
    expect(fetchSpy).toHaveBeenCalledTimes(2);
  });
});
