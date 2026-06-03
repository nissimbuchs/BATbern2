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

  // Determine OAuth redirect URLs based on environment
  let redirectSignIn = 'http://localhost:3000/auth/callback';
  let redirectSignOut = 'http://localhost:3000/';

  switch (environment) {
    case 'production':
      redirectSignIn = 'https://www.batbern.ch/auth/callback';
      redirectSignOut = 'https://www.batbern.ch/';
      break;
    case 'staging':
      // Staging account now serves production traffic
      redirectSignIn = 'https://www.batbern.ch/auth/callback';
      redirectSignOut = 'https://www.batbern.ch/';
      break;
  }

  // Cognito hosted-UI domain prefix. This is FIXED per user pool and must match the
  // CDK `cognito-stack` domainPrefix `batbern-${envName}-auth` (envName = 'staging' for the
  // single consolidated account that serves production). It CANNOT be derived from
  // `environment`: the backend serves environment='production' while the pool's domain
  // segment is 'staging' (and the old code also dropped the required `-auth` suffix), so
  // `batbern-${environment}.auth…` resolved to the non-existent
  // `batbern-production.auth…` → the /auth/callback OAuth code-exchange hit a dead domain
  // and no session was established (Story 12.8 finding F3, 2026-06-03).
  // TODO(12.7/12.9): serve this domain via runtime config (GET /api/v1/config) instead of a
  // constant, so a future second pool isn't silently mis-targeted.
  const cognitoDomainPrefix = 'batbern-staging-auth';

  const config: ResourcesConfig = {
    Auth: {
      Cognito: {
        userPoolId: cognito.userPoolId,
        userPoolClientId: cognito.clientId,
        loginWith: {
          oauth: {
            domain: `${cognitoDomainPrefix}.auth.${cognito.region}.amazoncognito.com`,
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

// Runtime config stashed by main.tsx at bootstrap — consumed lazily by
// ensureAmplifyConfigured() on the first auth-touching code path.
let runtimeConfig: AppConfig | null = null;
// Memoised configure promise — guarantees Amplify.configure runs at most once even under
// concurrent first-time callers (e.g. several API requests firing together post-login).
let configurePromise: Promise<void> | null = null;

/**
 * Stash the runtime config WITHOUT importing or configuring Amplify.
 * Called once from main.tsx at bootstrap. Cheap + synchronous — keeps aws-amplify off the
 * eager bundle for anonymous visitors.
 */
export const setAmplifyRuntimeConfig = (config: AppConfig): void => {
  runtimeConfig = config;
};

/**
 * Idempotently load + configure AWS Amplify the first time an auth-touching code path needs
 * it. Dynamically imports aws-amplify (and its Cognito token provider) so the ~426 KB
 * dependency lands in a lazy chunk rather than the eager homepage bundle.
 *
 * No-ops when no runtime config has been stashed yet (e.g. unit tests that mock
 * `aws-amplify/auth` directly and never call setAmplifyRuntimeConfig). In that case the
 * caller's own dynamically-imported (and mocked) Amplify functions still run.
 */
export const ensureAmplifyConfigured = async (): Promise<void> => {
  if (configurePromise) return configurePromise;
  if (!runtimeConfig) return; // nothing to configure (tests / pre-bootstrap)

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
