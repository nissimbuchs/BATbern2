/**
 * API Client Configuration
 * Story 1.2.1: HTTP Client with i18n Support
 */

import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import i18n from '@/i18n/config';
import { hasCognitoSession } from '@/utils/auth/cognitoSession';
import { ensureAmplifyConfigured } from '@/config/amplify';
import { authService } from '@/services/auth/authService';
import { setLogoutReason } from '@/services/auth/logoutReason';
import { safeRandomUUID } from '@/utils/uuid';

/**
 * Generate a unique correlation ID for request tracing
 *
 * Uses safeRandomUUID(): crypto.randomUUID() is undefined outside a secure context, and
 * the TypeError it threw aborted EVERY outgoing request from the interceptor when the
 * dev server was browsed over plain http:// by LAN IP. Tracing must never fail a request.
 *
 * @returns UUID v4 format correlation ID
 */
function generateCorrelationId(): string {
  return safeRandomUUID();
}

/**
 * Get ID token from AWS Amplify (secure token management)
 * Uses ID token (not access token) to include custom Cognito attributes (custom:role)
 * @returns ID token string or null if not authenticated
 */
async function getIdToken(): Promise<string | null> {
  // Anonymous visitors (public homepage / archive / event discovery) have no Cognito
  // tokens in storage. Short-circuit BEFORE importing aws-amplify so the ~426 KB dependency
  // never loads for them (perf/public-homepage-followup #2). Authenticated requests fall
  // through to lazily configure + query Amplify.
  if (!hasCognitoSession()) {
    return null;
  }
  try {
    await ensureAmplifyConfigured();
    const { fetchAuthSession } = await import('aws-amplify/auth');
    const session = await fetchAuthSession();
    return session.tokens?.idToken?.toString() || null;
  } catch {
    return null;
  }
}

/**
 * Navigation callback for handling redirects from API interceptor
 * Set this from App.tsx after router initialization
 */
let navigateCallback: ((path: string) => void) | null = null;

/**
 * Set the navigation callback for API client redirects
 * Call this from App.tsx: setNavigationCallback(navigate)
 */
export function setNavigationCallback(navigate: (path: string) => void): void {
  navigateCallback = navigate;
}

/**
 * Create axios instance with default configuration
 *
 * Note: baseURL is set to '/api' initially, but will be updated
 * with runtime config from backend via updateApiClientConfig()
 */
const apiClient: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
  // Configure params serialization for Spring compatibility
  // Arrays are sent as repeated parameters: status=CONFIRMED&status=REGISTERED
  // instead of bracket notation: status[]=CONFIRMED&status[]=REGISTERED
  paramsSerializer: {
    indexes: null, // This removes brackets from array parameters
  },
});

/**
 * Update API client configuration with runtime config from backend
 * Called after runtime config is loaded in main.tsx
 *
 * @param apiBaseUrl - Base URL from runtime config (e.g., "http://localhost:8080/api/v1")
 */
export function updateApiClientConfig(apiBaseUrl: string): void {
  apiClient.defaults.baseURL = apiBaseUrl;
  console.log(`[ApiClient] Updated baseURL to: ${apiBaseUrl}`);
}

/**
 * Request interceptor to add headers (Accept-Language, X-Correlation-ID, Authorization)
 */
apiClient.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    // Add Accept-Language header based on current i18n language
    config.headers['Accept-Language'] = i18n.language;

    // Add X-Correlation-ID for distributed tracing (Story 1.17 AC6)
    config.headers['X-Correlation-ID'] = generateCorrelationId();

    // Skip authentication for public endpoints (e.g., partner showcase, event discovery)
    // This prevents 401 errors when expired tokens exist in browser storage
    const skipAuth = config.headers['Skip-Auth'] === 'true';
    if (skipAuth) {
      delete config.headers['Skip-Auth']; // Remove marker header
    } else {
      // Add authentication token from AWS Amplify (secure storage)
      // SEC-001 Fix: Use AWS Amplify's secure token management instead of localStorage
      const token = await getIdToken();
      if (token) {
        config.headers['Authorization'] = `Bearer ${token}`;
      }
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Archive robustness (Story 7.5 follow-up): requests for PUBLIC, optional reads — e.g. the
// per-session Q&A thread rendered on public archive pages — set `skipAuthRedirect` so a 401
// does NOT force-logout the page. The caller's query handles the error and renders empty.
// Without this, an optional read that 401s (an endpoint not yet deployed, or a transient auth
// blip) would bounce an anonymous visitor on a public archive page to /login.
declare module 'axios' {
  interface AxiosRequestConfig {
    skipAuthRedirect?: boolean;
  }
}

/**
 * Response interceptor for error handling with correlation ID tracking
 */
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Extract correlation ID from request or response headers
    const correlationId =
      error.config?.headers?.['X-Correlation-ID'] ||
      error.response?.headers?.['x-correlation-id'] ||
      'unknown';

    // Handle common HTTP errors
    if (error.response) {
      const { status } = error.response;

      switch (status) {
        case 401:
          // Public/optional read (e.g. archive Q&A): a 401 must NOT force-logout the page.
          if (error.config?.skipAuthRedirect) {
            console.warn(`[${correlationId}] 401 on skipAuthRedirect request — not redirecting`);
            break;
          }
          // Unauthorized - redirect to login using React Router navigate
          console.error(`[${correlationId}] Unauthorized - session expired`);
          if (navigateCallback) {
            navigateCallback('/login');
          } else {
            // Fallback to window.location if navigate not configured (shouldn't happen in production)
            console.warn('Navigate callback not set - falling back to window.location');
            window.location.href = '/login';
          }
          break;
        case 403:
          // Story 12.7 / G1 (from Story 12.2 is_active gate): the gateway emits
          //   403 { "error": "ACCOUNT_DEACTIVATED", "message": "..." }
          // when a user's account is deactivated — for BOTH password and federated
          // sessions (the gate is provider-agnostic). Force a clean logout + route to
          // the login surface with a deactivated indicator so the user sees a clear
          // message rather than a raw 403.
          // CRITICAL: this is a 403, NOT a 401 — it must NOT go through the
          // token-refresh path (a refresh loop would result).
          if (error.response.data?.error === 'ACCOUNT_DEACTIVATED') {
            console.error(`[${correlationId}] Account deactivated - forcing logout`);
            // Story 12.8 F5: for a FEDERATED session, Amplify signOut() performs a
            // full-page redirect to the Cognito hosted-UI /logout — which clobbers the
            // in-app navigation below (and its ?reason= param). Persist the reason in
            // sessionStorage so it survives the round-trip; LogoutPage/LoginForm consume it.
            setLogoutReason('account_deactivated');
            // Best-effort sign-out; clears the Amplify session (native or federated).
            void authService.signOut().catch(() => {
              /* ignore — we redirect regardless */
            });
            const target = '/login?reason=account_deactivated';
            if (navigateCallback) {
              navigateCallback(target);
            } else {
              window.location.href = target;
            }
          } else {
            // Forbidden - insufficient permissions
            console.error(`[${correlationId}] Forbidden: Insufficient permissions`);
          }
          break;
        case 500:
          // Server error
          console.error(`[${correlationId}] Server error occurred`);
          break;
        default:
          // Log other errors with correlation ID
          console.error(`[${correlationId}] API error: ${status}`);
      }
    } else {
      // Network error or timeout
      console.error(`[${correlationId}] Network error: ${error.message}`);
    }

    return Promise.reject(error);
  }
);

export default apiClient;
