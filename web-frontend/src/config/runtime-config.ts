/**
 * Runtime Configuration Loader
 *
 * Implements "build once, deploy everywhere" pattern by loading
 * environment-specific configuration from the backend API at runtime
 * instead of baking it into the build.
 *
 * Benefits:
 * - Single production build for all environments (dev/staging/prod)
 * - Configuration changes don't require rebuilds
 * - Test exact same artifact in staging and production
 * - Backend controls what configuration frontend receives
 */

export interface AppConfig {
  environment: 'development' | 'staging' | 'production';
  apiBaseUrl: string;
  cognito: {
    userPoolId: string;
    clientId: string;
    region: string;
  };
  features: {
    notifications: boolean;
    analytics: boolean;
    pwa: boolean;
    turnstile: boolean;
  };
  turnstile?: {
    siteKey: string;
  };
}

let cachedConfig: AppConfig | null = null;

/**
 * Load runtime configuration from backend API
 *
 * This function is called once at app startup before rendering.
 * The configuration is cached for the lifetime of the app session.
 *
 * @returns Promise resolving to application configuration
 * @throws Error if config fetch fails and no fallback is available
 */
export async function loadRuntimeConfig(): Promise<AppConfig> {
  // Return cached config if already loaded
  if (cachedConfig) {
    return cachedConfig;
  }

  // Determine API endpoint based on hostname
  const apiUrl = getApiUrl();

  console.log(`[Config] Loading runtime config from: ${apiUrl}/api/v1/config`);

  try {
    const response = await fetch(`${apiUrl}/api/v1/config`, {
      headers: {
        Accept: 'application/json',
      },
      // Use credentials if needed for authenticated endpoints
      credentials: 'omit',
    });

    if (!response.ok) {
      throw new Error(`Config fetch failed: ${response.status} ${response.statusText}`);
    }

    const config = await response.json();

    // Validate config structure
    validateConfig(config);

    cachedConfig = config;
    console.log(`[Config] Loaded config for environment: ${config.environment}`);

    return cachedConfig;
  } catch (error) {
    console.error('[Config] Failed to load runtime config:', error);

    // In development, fall back to defaults to improve DX
    if (isDevelopmentEnvironment()) {
      console.warn('[Config] Using default development config as fallback');
      return getDefaultDevelopmentConfig();
    }

    // In production, fail fast - don't allow app to start with wrong config
    throw new Error(
      'Failed to load application configuration. Please refresh the page or contact support if the problem persists.',
      { cause: error }
    );
  }
}

/**
 * Determine API URL based on current hostname
 *
 * This enables the same build to work in different environments:
 * - localhost → local API Gateway (supports custom ports via VITE_API_PORT)
 * - batbern.ch / www.batbern.ch → production API Gateway
 */
function getApiUrl(): string {
  const hostname = window.location.hostname;

  if (hostname === 'localhost' || hostname === '127.0.0.1') {
    // Support custom API port for multi-instance development
    // Set via VITE_API_PORT when starting frontend (e.g., VITE_API_PORT=8500 npm run dev)
    // Default is 8000 — the canonical native-dev gateway port (`make dev-native-up`,
    // which also sets VITE_API_PORT=8000 explicitly). docker-compose maps the gateway
    // to :8080, so docker-compose browser access must set VITE_API_PORT=8080.
    // No production impact: prod hostnames resolve below to https://api.batbern.ch.
    const apiPort = import.meta.env.VITE_API_PORT || '8000';
    return `http://localhost:${apiPort}`;
  }

  // Production (batbern.ch or www.batbern.ch)
  return 'https://api.batbern.ch';
}

/**
 * Synchronous, best-effort API base URL derived purely from the current hostname.
 *
 * Mirrors the backend's `ConfigController.getApiBaseUrl()`, which returns exactly
 * `<host>/api/v1` in every environment (`http://localhost:{port}/api/v1` in dev,
 * `https://api.batbern.ch/api/v1` in staging+prod). Because it needs no network call,
 * the API client can be initialised with this at bootstrap — BEFORE the
 * `GET /api/v1/config` round-trip — so public data (e.g. the homepage current event)
 * loads in parallel with the config fetch instead of waiting for it.
 *
 * `loadRuntimeConfig()` later supplies the authoritative `apiBaseUrl` (identical in
 * prod) via `updateApiClientConfig()`.
 */
export function getDefaultApiBaseUrl(): string {
  return `${getApiUrl()}/api/v1`;
}

/**
 * Check if running in development environment (localhost)
 */
function isDevelopmentEnvironment(): boolean {
  const hostname = window.location.hostname;
  return hostname === 'localhost' || hostname === '127.0.0.1';
}

/**
 * Validate config structure to catch API contract violations
 */
function validateConfig(config: unknown): asserts config is AppConfig {
  if (!config || typeof config !== 'object') {
    throw new Error('Invalid config: not an object');
  }

  const cfg = config as Record<string, unknown>;

  if (!cfg.environment || typeof cfg.environment !== 'string') {
    throw new Error('Invalid config: missing or invalid environment');
  }

  if (!cfg.apiBaseUrl || typeof cfg.apiBaseUrl !== 'string') {
    throw new Error('Invalid config: missing or invalid apiBaseUrl');
  }

  if (!cfg.cognito || typeof cfg.cognito !== 'object') {
    throw new Error('Invalid config: missing or invalid cognito config');
  }

  const cognito = cfg.cognito as Record<string, unknown>;
  if (!cognito.userPoolId || !cognito.clientId || !cognito.region) {
    throw new Error('Invalid config: incomplete cognito configuration');
  }

  if (!cfg.features || typeof cfg.features !== 'object') {
    throw new Error('Invalid config: missing or invalid features');
  }
}

/**
 * Get default development configuration for local development
 *
 * Used as fallback when backend API is unavailable during development.
 * This improves developer experience by allowing frontend to start
 * even if backend isn't running yet.
 */
function getDefaultDevelopmentConfig(): AppConfig {
  return {
    environment: 'development',
    apiBaseUrl: 'http://localhost:8000/api/v1',
    cognito: {
      userPoolId: 'eu-central-1_XXXXXXXXX',
      clientId: 'XXXXXXXXXXXXXXXXXXXXXXXXXX',
      region: 'eu-central-1',
    },
    features: {
      notifications: true,
      analytics: false,
      pwa: false,
      turnstile: false,
    },
  };
}

/**
 * Clear cached config (useful for testing or hot-reload scenarios)
 */
export function clearConfigCache(): void {
  cachedConfig = null;
}
