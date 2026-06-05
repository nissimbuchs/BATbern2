/**
 * AWS Amplify Configuration
 * Story 1.2: AWS Cognito Integration Setup
 * Updated to use runtime config instead of build-time env vars
 *
 * Public-homepage performance (perf/public-homepage-followup #2): Amplify is no longer
 * eagerly imported + configured at bootstrap. `aws-amplify` (~426 KB) is the largest
 * remaining chunk on the public homepage, yet anonymous visitors never authenticate.
 *
 * The new contract:
 *  - `main.tsx` calls `setAmplifyRuntimeConfig(config)` at bootstrap — a synchronous stash
 *    that does NOT import aws-amplify.
 *  - Every auth-touching entry point (authService, apiClient.getIdToken, the password /
 *    email-verification / live-control flows) calls `await ensureAmplifyConfigured()` before
 *    invoking any Amplify API. The first such call dynamically imports + configures Amplify;
 *    subsequent calls are no-ops. Anonymous visitors never trigger it (the eager callers
 *    gate on `hasCognitoSession()` first), so aws-amplify stays off their first paint.
 *
 * Note: the `import type` below is erased at build time, so this module has NO runtime
 * dependency on aws-amplify — it is safe to import from eager code.
 */

import type { ResourcesConfig } from 'aws-amplify';
import type { AppConfig } from './runtime-config';

// Storage adapter for Amplify token persistence
function createStorageAdapter(storage: Storage) {
  return {
    setItem(key: string, value: string): Promise<void> {
      return Promise.resolve(storage.setItem(key, value));
    },
    getItem(key: string): Promise<string | null> {
      return Promise.resolve(storage.getItem(key));
    },
    removeItem(key: string): Promise<void> {
      return Promise.resolve(storage.removeItem(key));
    },
    clear(): Promise<void> {
      return Promise.resolve(storage.clear());
    },
  };
}

// Build Amplify config from runtime config
const getAmplifyConfig = (runtimeConfig: AppConfig): ResourcesConfig => {
  const { environment, cognito } = runtimeConfig;

  // Determine OAuth redirect URLs based on environment.
  // redirectSignOut MUST exactly match a registered Cognito client logout URL
  // (cognito-stack.ts logoutUrls = ['…/logout']) — Cognito's /logout endpoint rejects an
  // unregistered logout_uri with the cryptic "Required String parameter 'redirect_uri' is
  // not present" error page (Story 12.8 finding F4, 2026-06-04: the old value '…/' was not
  // registered, so every federated sign-out dead-ended on that Cognito error). The /logout
  // app route (Story 12.7 LogoutPage) finishes local cleanup and routes onward.
  let redirectSignIn = 'http://localhost:3000/auth/callback';
  let redirectSignOut = 'http://localhost:3000/logout';

  switch (environment) {
    case 'production':
      redirectSignIn = 'https://www.batbern.ch/auth/callback';
      redirectSignOut = 'https://www.batbern.ch/logout';
      break;
    case 'staging':
      // Staging account now serves production traffic
      redirectSignIn = 'https://www.batbern.ch/auth/callback';
      redirectSignOut = 'https://www.batbern.ch/logout';
      break;
  }

  // Cognito hosted-UI domain (Story 12.9 DF-1, 2026-06-04): the CUSTOM domain
  // `auth.batbern.ch` (CDK cognito-stack `CustomUserPoolDomain`). User-visible benefit:
  // Google's consent screen shows the redirect domain — batbern.ch instead of the default
  // `batbern-staging-auth.auth.eu-central-1.amazoncognito.com`. The default prefix domain
  // still exists on the pool (kept for zero-downtime transition), but new sign-ins go
  // through the custom domain. History: deriving this from `environment` was finding F3
  // (backend serves environment='production' while the pool's prefix segment is 'staging'
  // → dead domain, broken code-exchange) — so this stays an explicit constant.
  // TODO(12.7/12.9): serve this domain via runtime config (GET /api/v1/config) instead of a
  // constant, so a future second pool isn't silently mis-targeted.
  const cognitoHostedUiDomain = 'auth.batbern.ch';

  const config: ResourcesConfig = {
    Auth: {
      Cognito: {
        userPoolId: cognito.userPoolId,
        userPoolClientId: cognito.clientId,
        loginWith: {
          oauth: {
            domain: cognitoHostedUiDomain,
            scopes: ['email', 'openid', 'profile'],
            redirectSignIn: [redirectSignIn],
            redirectSignOut: [redirectSignOut],
            responseType: 'code',
          },
        },
      },
    },
  };

  return config;
};

// Detect which storage has Cognito tokens (localStorage or sessionStorage)
// This ensures we restore sessions correctly regardless of "remember me" choice
function detectTokenStorage(): Storage {
  // Check for Cognito tokens in both storages
  // Token keys follow pattern: CognitoIdentityServiceProvider.{clientId}.{username}.{tokenType}
  const localKeys = Object.keys(localStorage);
  const sessionKeys = Object.keys(sessionStorage);

  const hasCognitoTokenInLocal = localKeys.some((key) =>
    key.startsWith('CognitoIdentityServiceProvider.')
  );
  const hasCognitoTokenInSession = sessionKeys.some((key) =>
    key.startsWith('CognitoIdentityServiceProvider.')
  );

  // Prefer sessionStorage if tokens found there (user didn't check "remember me")
  // Otherwise use localStorage (user checked "remember me" or no session yet)
  if (hasCognitoTokenInSession) {
    console.log('[Amplify] Detected tokens in sessionStorage (temporary session)');
    return sessionStorage;
  } else if (hasCognitoTokenInLocal) {
    console.log('[Amplify] Detected tokens in localStorage (persistent session)');
    return localStorage;
  } else {
    console.log('[Amplify] No existing session found, defaulting to localStorage');
    return localStorage;
  }
}

// Runtime config stashed by ConfigProvider when GET /api/v1/config resolves — consumed
// lazily by ensureAmplifyConfigured() on the first auth-touching code path.
let runtimeConfig: AppConfig | null = null;
// Memoised configure promise — guarantees Amplify.configure runs at most once even under
// concurrent first-time callers (e.g. several API requests firing together post-login).
let configurePromise: Promise<void> | null = null;

// ── Config-arrival wait (12.8 F8 central hardening, 2026-06-05) ────────────────────────
// The old `if (!runtimeConfig) return` silent no-op was the root cause of the unstable
// federated login: any auth-touching call that fired before ConfigProvider's background
// `GET /api/v1/config` resolved left Amplify unconfigured FOREVER (nothing retried), so
// Amplify v6's OAuth listener — triggered ONLY by Amplify.configure() — never ran the
// /auth/callback ?code= exchange. Contract now: while a config fetch is announced as
// pending, ensureAmplifyConfigured() WAITS (bounded) for it instead of no-op'ing. The
// no-op is preserved only when nothing is pending (unit tests / config fetch failed).
const CONFIG_ARRIVAL_TIMEOUT_MS = 15000;
let configFetchPending = false;
let configWaiters: Array<() => void> = [];

const settleConfigWaiters = (): void => {
  const waiters = configWaiters;
  configWaiters = [];
  waiters.forEach((resolve) => resolve());
};

/**
 * Announce that a runtime-config fetch is in flight. Called by ConfigProvider right
 * before `loadRuntimeConfig()` — from this moment ensureAmplifyConfigured() waits for
 * the outcome instead of silently no-op'ing.
 */
export const expectAmplifyRuntimeConfig = (): void => {
  configFetchPending = true;
};

/**
 * Announce that the pending runtime-config fetch FAILED. Releases all waiters (they
 * resolve as no-ops; downstream Amplify calls surface their own errors).
 */
export const cancelExpectedAmplifyRuntimeConfig = (): void => {
  configFetchPending = false;
  settleConfigWaiters();
};

/** Bounded wait for the pending config fetch to settle (arrival, failure, or timeout). */
const waitForRuntimeConfig = (): Promise<void> =>
  new Promise((resolve) => {
    const timer = setTimeout(() => {
      console.warn(
        '[Amplify] Runtime config still pending after ' +
          `${CONFIG_ARRIVAL_TIMEOUT_MS}ms — proceeding unconfigured`
      );
      resolve();
    }, CONFIG_ARRIVAL_TIMEOUT_MS);
    configWaiters.push(() => {
      clearTimeout(timer);
      resolve();
    });
  });

/**
 * Stash the runtime config WITHOUT importing or configuring Amplify.
 * Called by ConfigProvider when GET /api/v1/config resolves. Cheap + synchronous — keeps
 * aws-amplify off the eager bundle for anonymous visitors. Releases every caller
 * currently waiting inside ensureAmplifyConfigured().
 */
export const setAmplifyRuntimeConfig = (config: AppConfig): void => {
  runtimeConfig = config;
  configFetchPending = false;
  settleConfigWaiters();
};

/**
 * Idempotently load + configure AWS Amplify the first time an auth-touching code path needs
 * it. Dynamically imports aws-amplify (and its Cognito token provider) so the ~426 KB
 * dependency lands in a lazy chunk rather than the eager homepage bundle.
 *
 * While a runtime-config fetch is pending (announced via expectAmplifyRuntimeConfig),
 * WAITS — bounded — for it to settle, then configures (12.8 F8). No-ops only when no
 * config is stashed AND none is pending (e.g. unit tests that mock `aws-amplify/auth`
 * directly, or the config fetch failed). In that case the caller's own
 * dynamically-imported (and mocked) Amplify functions still run.
 */
export const ensureAmplifyConfigured = async (): Promise<void> => {
  if (configurePromise) return configurePromise;
  if (!runtimeConfig && configFetchPending) {
    await waitForRuntimeConfig();
    // Another caller may have started configuring while we waited.
    if (configurePromise) return configurePromise;
  }
  if (!runtimeConfig) return; // nothing to configure (tests / failed config fetch)

  const config = runtimeConfig;
  configurePromise = (async () => {
    const [{ Amplify }, { cognitoUserPoolsTokenProvider }] = await Promise.all([
      import('aws-amplify'),
      import('aws-amplify/auth/cognito'),
    ]);

    try {
      Amplify.configure(getAmplifyConfig(config));

      // Detect and configure correct storage based on existing tokens
      // This fixes the issue where page refresh loses auth when "remember me" wasn't checked
      const storage = detectTokenStorage();
      cognitoUserPoolsTokenProvider.setKeyValueStorage(createStorageAdapter(storage));

      console.log('✅ AWS Amplify configured (lazy) for environment:', config.environment);
    } catch (error) {
      // Reset so a later call can retry rather than be wedged on a rejected promise.
      configurePromise = null;
      console.error('❌ Failed to configure AWS Amplify:', error);
      throw new Error('Amplify configuration failed', { cause: error });
    }
  })();

  return configurePromise;
};
