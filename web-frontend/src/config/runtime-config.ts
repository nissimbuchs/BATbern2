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
      'Failed to load application configuration. Please refresh the page or contact support if the problem persists.'
    );
  }
}

/**
 * Determine API URL based on current hostname
 *
 * This enables the same build to work in different environments:
 * - localhost → local API Gateway (Docker)
 * - staging.batbern.ch → staging API Gateway
 * - batbern.ch → production API Gateway
 */
function getApiUrl(): string {
  const hostname = window.location.hostname;

  if (hostname === 'localhost' || hostname === '127.0.0.1') {
    return 'http://localhost:8080';
  }

  if (hostname === 'staging.batbern.ch') {
    return 'https://api.staging.batbern.ch';
  }

  // Production (batbern.ch or www.batbern.ch)
  return 'https://api.batbern.ch';
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
    apiBaseUrl: 'http://localhost:8080/api/v1',
    cognito: {
      userPoolId: 'eu-central-1_XXXXXXXXX',
      clientId: 'XXXXXXXXXXXXXXXXXXXXXXXXXX',
      region: 'eu-central-1',
    },
    features: {
      notifications: true,
      analytics: false,
      pwa: false,
    },
  };
}

/**
 * Clear cached config (useful for testing or hot-reload scenarios)
 */
export function clearConfigCache(): void {
  cachedConfig = null;
}
