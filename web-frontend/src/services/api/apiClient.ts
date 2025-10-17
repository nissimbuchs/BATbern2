/**
 * API Client Configuration
 * Story 1.2.1: HTTP Client with i18n Support
 */

import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import { fetchAuthSession } from 'aws-amplify/auth';
import i18n from '@/i18n/config';

/**
 * Generate a unique correlation ID for request tracing
 * @returns UUID v4 format correlation ID
 */
function generateCorrelationId(): string {
  return crypto.randomUUID();
}

/**
 * Get access token from AWS Amplify (secure token management)
 * @returns Access token string or null if not authenticated
 */
async function getAccessToken(): Promise<string | null> {
  try {
    const session = await fetchAuthSession();
    return session.tokens?.accessToken?.toString() || null;
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

    // Add authentication token from AWS Amplify (secure storage)
    // SEC-001 Fix: Use AWS Amplify's secure token management instead of localStorage
    const token = await getAccessToken();
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

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
          // Forbidden - insufficient permissions
          console.error(`[${correlationId}] Forbidden: Insufficient permissions`);
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
