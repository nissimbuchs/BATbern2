# Frontend Configuration Guide

## Overview

BATbern frontend implements **runtime configuration** following the "build once, deploy everywhere" pattern. This means we build a single production-optimized artifact that works across all environments (development, staging, production) without rebuilding.

## Architecture

### Traditional Approach ❌ (What we DON'T do)

```
Build for dev  → Deploy to dev
Build for staging → Deploy to staging
Build for prod → Deploy to prod
```

**Problems:**

- 3 different builds (can't test exact production artifact in staging)
- Configuration changes require rebuilds
- Environment-specific build artifacts
- Complex CI/CD

### Modern Approach ✅ (What we DO)

```
Build ONCE → Deploy to dev
          → Deploy to staging
          → Deploy to prod
```

**Benefits:**

- Single production build for all environments
- Test exact same artifact that goes to production
- Configuration changes don't require rebuilds
- Simpler CI/CD pipeline

## How It Works

### 1. Build Time

The build process creates an environment-agnostic artifact:

```bash
npm run build  # Creates dist/ with NO environment variables baked in
```

**No VITE\_ variables!** The build doesn't know about:

- API endpoints
- Cognito User Pool IDs
- Feature flags
- Environment names

### 2. Runtime Configuration Loading

When the app starts in the browser:

```typescript
// 1. App loads (shows loading screen)
bootstrap()

// 2. Fetch config from backend based on hostname
const config = await fetch('http://localhost:8080/api/v1/config')
// or: https://api.staging.batbern.ch/api/v1/config
// or: https://api.batbern.ch/api/v1/config

// 3. Backend returns environment-specific config:
{
  "environment": "development",
  "apiBaseUrl": "http://localhost:8080/api/v1",
  "cognito": {
    "userPoolId": "eu-central-1_XXX",
    "clientId": "XXXXX",
    "region": "eu-central-1"
  },
  "features": {
    "notifications": true,
    "analytics": false,
    "pwa": false
  }
}

// 4. App renders with configuration
<ConfigProvider config={config}>
  <App />
</ConfigProvider>
```

### 3. Hostname-Based API Detection

The frontend automatically detects which API to call:

| Hostname             | API Endpoint                     |
| -------------------- | -------------------------------- |
| `localhost`          | `http://localhost:8080`          |
| `staging.batbern.ch` | `https://api.staging.batbern.ch` |
| `batbern.ch`         | `https://api.batbern.ch`         |

**Same build works everywhere!**

## Using Configuration in Components

### Access Full Config

```typescript
import { useConfig } from '@/contexts/ConfigContext';

function MyComponent() {
  const config = useConfig();

  console.log(config.environment); // 'development' | 'staging' | 'production'
  console.log(config.apiBaseUrl); // API endpoint
  console.log(config.cognito); // Cognito configuration
}
```

### Check Feature Flags

```typescript
import { useFeature } from '@/contexts/ConfigContext';

function AnalyticsComponent() {
  const analyticsEnabled = useFeature('analytics');

  if (!analyticsEnabled) return null;

  return <AnalyticsTracker />;
}
```

### Check Environment

```typescript
import { useEnvironment } from '@/contexts/ConfigContext';

function DebugPanel() {
  const env = useEnvironment();

  // Only show debug tools in development
  if (env === 'production') return null;

  return <DevTools />;
}
```

## Local Development

### Starting the App

```bash
# 1. Start backend (serves config at /api/v1/config)
docker-compose up api-gateway

# 2. Start frontend (loads config from backend)
cd web-frontend
npm run dev

# Frontend automatically fetches config from http://localhost:8080/api/v1/config
```

### Fallback Behavior

If the backend API is unavailable during development, the frontend falls back to safe defaults:

```typescript
{
  environment: 'development',
  apiBaseUrl: 'http://localhost:8080/api/v1',
  cognito: {
    userPoolId: 'eu-central-1_XXXXXXXXX',
    clientId: 'XXXXXXXXXXXXXXXXXXXXXXXXXX',
    region: 'eu-central-1'
  },
  features: {
    notifications: true,
    analytics: false,
    pwa: false
  }
}
```

**Note:** This fallback only works in development (localhost). In staging/production, the app will fail fast if it can't load configuration.

## Deployment

### Building

```bash
cd web-frontend
npm run build
```

This creates a **single production-optimized build** in `dist/` that works in all environments.

### Deploying to Staging

```bash
# Same build from above!
aws s3 sync dist/ s3://batbern-staging-frontend/
aws cloudfront create-invalidation --distribution-id XXXXXX --paths "/*"
```

When users visit `staging.batbern.ch`:

1. App loads
2. Fetches config from `https://api.staging.batbern.ch/api/v1/config`
3. Backend returns staging configuration
4. App renders with staging settings

### Deploying to Production

```bash
# Same build from staging!
aws s3 sync dist/ s3://batbern-production-frontend/
aws cloudfront create-invalidation --distribution-id XXXXXX --paths "/*"
```

When users visit `batbern.ch`:

1. App loads
2. Fetches config from `https://api.batbern.ch/api/v1/config`
3. Backend returns production configuration
4. App renders with production settings

**The exact same JavaScript bundle runs in both environments!**

## Testing

### Unit Tests (Vitest)

Unit tests don't need environment configuration:

```bash
npm run test  # Tests source code directly, no config needed
```

### E2E Tests (Playwright)

E2E tests ARE environment-specific (this is OK - they're not deployed):

```bash
# Test local development environment
npm run test:e2e

# Test staging environment
npm run test:e2e:staging

# Test production environment
npm run test:e2e:production
```

Playwright configuration automatically adjusts based on `TEST_ENV`:

```typescript
// playwright.config.ts
const envConfig = {
  development: { baseURL: 'http://localhost:3000' },
  staging: { baseURL: 'https://staging.batbern.ch' },
  production: { baseURL: 'https://batbern.ch' },
}[process.env.TEST_ENV || 'development'];
```

## Backend Configuration

The backend serves configuration via `/api/v1/config` endpoint.

### Spring Boot Properties

```yaml
# application.yml
app:
  environment: ${APP_ENVIRONMENT:development}

aws:
  cognito:
    userPoolId: ${COGNITO_USER_POOL_ID}
    appClientId: ${COGNITO_CLIENT_ID}
    region: ${AWS_REGION:eu-central-1}
```

### Environment Variable Mapping

| Environment | APP_ENVIRONMENT | Cognito Pool    | API URL                                 |
| ----------- | --------------- | --------------- | --------------------------------------- |
| Development | `development`   | Dev Pool        | `http://localhost:8080/api/v1`          |
| Staging     | `staging`       | Staging Pool    | `https://api.staging.batbern.ch/api/v1` |
| Production  | `production`    | Production Pool | `https://api.batbern.ch/api/v1`         |

### Docker Compose

```yaml
api-gateway:
  environment:
    - APP_ENVIRONMENT=development
    - COGNITO_USER_POOL_ID=${COGNITO_USER_POOL_ID}
    - COGNITO_CLIENT_ID=${COGNITO_CLIENT_ID}
```

## Troubleshooting

### "Failed to load configuration" Error

**Symptoms:** Error screen on app startup

**Causes:**

1. Backend API is not running
2. Backend is not serving `/api/v1/config` endpoint
3. CORS issues preventing frontend from calling API

**Solutions:**

```bash
# 1. Verify backend is running
curl http://localhost:8080/api/v1/config

# 2. Check backend logs
docker-compose logs api-gateway

# 3. Verify APP_ENVIRONMENT is set
docker-compose exec api-gateway env | grep APP_ENVIRONMENT
```

### Configuration Not Updating

**Symptoms:** Frontend still using old configuration after backend changes

**Cause:** Browser cache

**Solution:** Hard refresh (Cmd+Shift+R or Ctrl+Shift+R)

### Different Config in Different Tabs

**Symptoms:** Multiple tabs showing different configurations

**Cause:** In-memory cache persists per page load

**Solution:** Expected behavior - refresh all tabs after configuration changes

## Migration from Build-Time Config

### What Changed

**Before (Build-Time Config):**

```bash
# Build for each environment
VITE_API_URL=http://localhost:8080 npm run build  # Dev build
VITE_API_URL=https://api.staging.batbern.ch npm run build   # Staging build
VITE_API_URL=https://api.batbern.batbern.ch npm run build   # Prod build
```

**After (Runtime Config):**

```bash
# Build once
npm run build  # Single build for all environments
```

### Removed Files

- ❌ `web-frontend/.env.development`
- ❌ `web-frontend/.env.staging`
- ❌ `web-frontend/.env.production`

### New Files

- ✅ `web-frontend/src/config/runtime-config.ts` - Config loader
- ✅ `web-frontend/src/contexts/ConfigContext.tsx` - Config provider
- ✅ `api-gateway/.../config/ConfigController.java` - Config endpoint

## Best Practices

### ✅ DO

- Use `useConfig()` hook to access configuration in components
- Use `useFeature()` hook to check feature flags
- Trust the configuration loaded from backend
- Add new config values to backend DTO first, then frontend TypeScript interfaces

### ❌ DON'T

- Don't add `VITE_` environment variables (they bake values into the build)
- Don't hard-code API endpoints in components
- Don't store environment-specific config in frontend code
- Don't use `import.meta.env` for environment detection (use `useEnvironment()` instead)

## References

- [Runtime Config Loader](./src/config/runtime-config.ts)
- [Config Context](./src/contexts/ConfigContext.tsx)
- [Backend Config Controller](../api-gateway/src/main/java/ch/batbern/gateway/config/ConfigController.java)
- [Vite Configuration](./vite.config.ts)
- [Playwright Configuration](./playwright.config.ts)
