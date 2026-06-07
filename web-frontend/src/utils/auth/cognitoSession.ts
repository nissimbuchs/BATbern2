/**
 * Cheap, Amplify-free probe for an existing Cognito session.
 *
 * Public-homepage performance (perf/public-homepage-followup #2): aws-amplify (~426 KB)
 * is the largest remaining chunk on the homepage bundle. It is now lazy-loaded — but to
 * actually keep it off the wire for anonymous visitors, the eager code paths that would
 * otherwise pull it in (the API client's auth interceptor and AuthProvider's session
 * restore) must first decide whether there is even a session to restore, WITHOUT importing
 * Amplify.
 *
 * Amplify v6 persists its tokens under keys of the form
 * `CognitoIdentityServiceProvider.{clientId}.{username}.{tokenType}` in localStorage
 * ("remember me") or sessionStorage. A visitor who never logged in has none of these, so
 * scanning for the key prefix is a synchronous, dependency-free way to short-circuit before
 * the dynamic `import('aws-amplify/auth')`.
 *
 * This mirrors the storage-detection logic in `config/amplify.ts#detectTokenStorage`, kept
 * here as a standalone module so both `apiClient` and `AuthContext` can import it without
 * dragging in the Amplify config module's (type-only) dependency on aws-amplify.
 */
const COGNITO_KEY_PREFIX = 'CognitoIdentityServiceProvider.';

function storageHasCognitoToken(storage: Storage): boolean {
  try {
    // Iterate via the Storage API (length + key(i)) rather than Object.keys(storage):
    // Storage keys are not own-enumerable in every environment (notably jsdom), so
    // Object.keys would miss them. This form is correct in both browsers and tests.
    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i);
      if (key && key.startsWith(COGNITO_KEY_PREFIX)) return true;
    }
    return false;
  } catch {
    // Storage access can throw (e.g. disabled cookies / privacy mode). Treat as "no session".
    return false;
  }
}

/**
 * @returns true if Amplify-managed Cognito tokens exist in either localStorage or
 * sessionStorage — i.e. there is a session worth restoring and Amplify must be loaded.
 */
export function hasCognitoSession(): boolean {
  return storageHasCognitoToken(localStorage) || storageHasCognitoToken(sessionStorage);
}
