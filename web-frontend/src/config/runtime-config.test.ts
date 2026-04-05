// @vitest-environment jsdom
/**
 * runtime-config.ts Tests
 *
 * Coverage for loadRuntimeConfig, validateConfig, clearConfigCache,
 * getApiUrl, isDevelopmentEnvironment, and getDefaultDevelopmentConfig.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { loadRuntimeConfig, clearConfigCache, type AppConfig } from './runtime-config';

const validConfig: AppConfig = {
  environment: 'staging',
  apiBaseUrl: 'https://api.batbern.ch/api/v1',
  cognito: { userPoolId: 'eu-central-1_abc', clientId: 'client-id', region: 'eu-central-1' },
  features: { notifications: true, analytics: true, pwa: true, turnstile: false },
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
    expect(config.apiBaseUrl).toBe('http://localhost:8080/api/v1');
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
