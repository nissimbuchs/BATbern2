/**
 * ensureAmplifyConfigured — config-arrival wait (12.8 F8 central hardening, 2026-06-05)
 *
 * The silent `if (!runtimeConfig) return` no-op was the root cause of the unstable
 * federated login: any auth-touching call that won the race against ConfigProvider's
 * background `GET /api/v1/config` left Amplify unconfigured FOREVER (nothing retried),
 * so the v6 OAuth listener never ran the ?code= exchange. Central contract now:
 *
 *  - config already stashed            → configure immediately (unchanged)
 *  - config fetch PENDING (announced)  → wait (bounded) for it, then configure
 *  - no config and nothing pending     → no-op (unit-test environments, unchanged)
 *  - pending fetch fails / times out   → resolve as no-op (callers fail loudly downstream)
 *
 * amplify.ts holds module-level state — every test re-imports a fresh module copy.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import type { AppConfig } from './runtime-config';

const mockConfigure = vi.hoisted(() => vi.fn());
vi.mock('aws-amplify', () => ({ Amplify: { configure: mockConfigure } }));
vi.mock('aws-amplify/auth/cognito', () => ({
  cognitoUserPoolsTokenProvider: { setKeyValueStorage: vi.fn() },
}));

const testConfig = {
  environment: 'staging',
  cognito: { userPoolId: 'eu-central-1_TEST', clientId: 'test-client-id' },
} as unknown as AppConfig;

async function freshAmplifyModule() {
  vi.resetModules();
  return import('./amplify');
}

describe('ensureAmplifyConfigured — config-arrival wait', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should_configureImmediately_when_configAlreadyStashed', async () => {
    const amplify = await freshAmplifyModule();
    amplify.setAmplifyRuntimeConfig(testConfig);

    await amplify.ensureAmplifyConfigured();

    expect(mockConfigure).toHaveBeenCalledTimes(1);
  });

  it('should_noop_when_noConfigAndNoFetchPending', async () => {
    // Unit-test environments never stash a config — the historical no-op is preserved
    // so mocked-Amplify tests don't hang.
    const amplify = await freshAmplifyModule();

    await amplify.ensureAmplifyConfigured();

    expect(mockConfigure).not.toHaveBeenCalled();
  });

  it('should_waitAndConfigure_when_configArrivesAfterCallStarted', async () => {
    // THE F8 scenario: an auth-touching call (e.g. /auth/callback) starts before the
    // runtime-config fetch resolves. It must WAIT and then configure — not no-op.
    const amplify = await freshAmplifyModule();
    amplify.expectAmplifyRuntimeConfig();

    const pending = amplify.ensureAmplifyConfigured();
    await new Promise((r) => setTimeout(r, 10));
    expect(mockConfigure).not.toHaveBeenCalled();

    amplify.setAmplifyRuntimeConfig(testConfig);
    await pending;

    expect(mockConfigure).toHaveBeenCalledTimes(1);
  });

  it('should_configureOnlyOnce_when_concurrentCallersWaited', async () => {
    const amplify = await freshAmplifyModule();
    amplify.expectAmplifyRuntimeConfig();

    const first = amplify.ensureAmplifyConfigured();
    const second = amplify.ensureAmplifyConfigured();
    amplify.setAmplifyRuntimeConfig(testConfig);
    await Promise.all([first, second]);

    expect(mockConfigure).toHaveBeenCalledTimes(1);
  });

  it('should_resolveAsNoop_when_pendingFetchFails', async () => {
    // GET /api/v1/config failed → ConfigProvider cancels the expectation. Waiters
    // resolve as no-op; downstream Amplify calls surface their own errors.
    const amplify = await freshAmplifyModule();
    amplify.expectAmplifyRuntimeConfig();

    const pending = amplify.ensureAmplifyConfigured();
    amplify.cancelExpectedAmplifyRuntimeConfig();
    await pending;

    expect(mockConfigure).not.toHaveBeenCalled();
  });

  it('should_resolveAsNoop_when_configNeverArrivesWithinBound', async () => {
    // Safety bound: a hung config fetch must not block auth calls forever.
    vi.useFakeTimers();
    const amplify = await freshAmplifyModule();
    amplify.expectAmplifyRuntimeConfig();

    const pending = amplify.ensureAmplifyConfigured();
    await vi.advanceTimersByTimeAsync(15000);
    await pending;

    expect(mockConfigure).not.toHaveBeenCalled();
  });

  it('should_configureLateCaller_when_configArrivedAfterEarlierTimeout', async () => {
    // A timed-out waiter must not poison the module: once the config eventually lands,
    // later callers configure normally.
    vi.useFakeTimers();
    const amplify = await freshAmplifyModule();
    amplify.expectAmplifyRuntimeConfig();

    const timedOut = amplify.ensureAmplifyConfigured();
    await vi.advanceTimersByTimeAsync(15000);
    await timedOut;
    expect(mockConfigure).not.toHaveBeenCalled();

    amplify.setAmplifyRuntimeConfig(testConfig);
    await amplify.ensureAmplifyConfigured();
    expect(mockConfigure).toHaveBeenCalledTimes(1);
  });
});
