# Configuration Migration Guide: Build-Time to Runtime

## Overview

BATbern has migrated from **build-time configuration** (environment variables baked into builds) to **runtime configuration** (loaded from backend API). This enables "build once, deploy everywhere."

## What Changed

### Before: Build-Time Configuration ❌

**Frontend** had environment-specific builds:
```bash
# Different builds for each environment
npm run build  # Read .env.development
npm run build  # Read .env.staging
npm run build  # Read .env.production
```

**Docker Compose** passed VITE_ variables:
```yaml
environment:
  - VITE_API_BASE_URL=http://localhost:8080
  - VITE_COGNITO_USER_POOL_ID=xxx
  - VITE_COGNITO_CLIENT_ID=xxx
```

**Problems:**
- Can't test exact production artifact in staging
- Configuration changes require rebuilds
- 3 different build artifacts
- Complex CI/CD

### After: Runtime Configuration ✅

**Frontend** has single build:
```bash
# One build for all environments
npm run build  # No environment variables!
```

**Configuration loaded at runtime:**
```typescript
// App startup
const config = await fetch('/api/v1/config');
// Returns environment-specific config based on hostname
```

**Benefits:**
- Single build deployed everywhere
- Test exact same artifact
- Configuration changes without rebuilds
- Simple CI/CD

## Migration Steps

### 1. Backend Changes

#### Created Files:
- `api-gateway/.../config/ConfigController.java` - Serves config endpoint
- `api-gateway/.../config/dto/FrontendConfigDTO.java` - Config response
- `api-gateway/.../config/dto/CognitoConfigDTO.java` - Cognito config
- `api-gateway/.../config/dto/FeatureFlagsDTO.java` - Feature flags

#### Updated Files:
- `api-gateway/src/main/resources/application.yml` - Added `app.environment`

#### New Endpoint:
```
GET /api/v1/config

Response:
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
```

### 2. Frontend Changes

#### Created Files:
- `web-frontend/src/config/runtime-config.ts` - Config loader
- `web-frontend/src/contexts/ConfigContext.tsx` - Config provider
- `web-frontend/CONFIG.md` - Configuration documentation

#### Updated Files:
- `web-frontend/src/main.tsx` - Bootstrap with runtime config
- `web-frontend/vite.config.ts` - Added documentation comments
- `web-frontend/playwright.config.ts` - Environment-aware testing
- `web-frontend/package.json` - Added test:e2e:staging and test:e2e:production

#### Deleted Files:
- ❌ `web-frontend/.env.development`
- ❌ `web-frontend/.env.staging`
- ❌ `web-frontend/.env.production`

### 3. Infrastructure Changes

#### Updated Files:
- `docker-compose.yml` - Removed VITE_ variables, added APP_ENVIRONMENT

**Before:**
```yaml
web-frontend:
  environment:
    - VITE_API_BASE_URL=http://localhost:8080
    - VITE_COGNITO_USER_POOL_ID=${COGNITO_USER_POOL_ID}
    - VITE_COGNITO_CLIENT_ID=${COGNITO_CLIENT_ID}
```

**After:**
```yaml
web-frontend:
  environment:
    - NODE_ENV=development  # Only for Vite dev server
    # NO VITE_ variables! Config loaded from API at runtime
```

## How to Use New Configuration

### In Components

**Access Configuration:**
```typescript
import { useConfig } from '@/contexts/ConfigContext';

function MyComponent() {
  const config = useConfig();

  // Access any config value
  const apiUrl = config.apiBaseUrl;
  const userPoolId = config.cognito.userPoolId;
  const isPwaEnabled = config.features.pwa;
}
```

**Check Feature Flags:**
```typescript
import { useFeature } from '@/contexts/ConfigContext';

function AnalyticsComponent() {
  const analyticsEnabled = useFeature('analytics');

  if (!analyticsEnabled) return null;

  return <AnalyticsTracker />;
}
```

**Check Environment:**
```typescript
import { useEnvironment } from '@/contexts/ConfigContext';

function DebugTools() {
  const env = useEnvironment();

  if (env === 'production') return null;

  return <DevTools />;
}
```

## Testing Changes

### Unit Tests (No Changes)

Unit tests still work the same:
```bash
npm run test
```

### E2E Tests (Enhanced)

E2E tests are now environment-aware:

```bash
# Test development (localhost)
npm run test:e2e

# Test staging
npm run test:e2e:staging

# Test production
npm run test:e2e:production
```

## CI/CD Changes

### Before: Multiple Builds

```yaml
jobs:
  build-dev:
    - npm run build  # With dev env vars
  build-staging:
    - npm run build  # With staging env vars
  build-prod:
    - npm run build  # With prod env vars
```

### After: Single Build

```yaml
jobs:
  build:
    - npm run build  # Single production build

  deploy-staging:
    - aws s3 sync dist/ s3://staging-bucket/
    - npm run test:e2e:staging

  deploy-production:
    - aws s3 sync dist/ s3://prod-bucket/
    - npm run test:e2e:production
```

**Same artifact deployed to both!**

## Rollback Plan

If issues occur, you can temporarily rollback:

### 1. Restore Old Environment Files

```bash
cd web-frontend

# Restore from git history
git checkout HEAD~1 -- .env.development
git checkout HEAD~1 -- .env.staging
git checkout HEAD~1 -- .env.production
```

### 2. Update docker-compose.yml

```yaml
web-frontend:
  environment:
    - VITE_API_BASE_URL=${VITE_API_BASE_URL}
    - VITE_COGNITO_USER_POOL_ID=${VITE_COGNITO_USER_POOL_ID}
    - VITE_COGNITO_CLIENT_ID=${VITE_COGNITO_CLIENT_ID}
```

### 3. Revert main.tsx Changes

```bash
git checkout HEAD~1 -- web-frontend/src/main.tsx
```

**Note:** This is a temporary rollback. The runtime config approach is best practice and should be maintained.

## Common Issues

### Issue: "Failed to load configuration"

**Cause:** Backend API not running or not serving config endpoint

**Solution:**
```bash
# Verify backend is running
curl http://localhost:8080/api/v1/config

# Check logs
docker-compose logs api-gateway

# Ensure APP_ENVIRONMENT is set
docker-compose exec api-gateway env | grep APP_ENVIRONMENT
```

### Issue: Frontend shows default config instead of real config

**Cause:** In development, fallback config is used if API fails

**Solution:** Start backend first, then frontend:
```bash
docker-compose up api-gateway  # Wait for health check
npm run dev                     # Start frontend
```

### Issue: Environment-specific values not updating

**Cause:** Browser cache

**Solution:** Hard refresh (Cmd+Shift+R or Ctrl+Shift+R)

## FAQ

### Q: Where do I add new configuration values?

**A:** Add to backend DTO first, then frontend types:

1. Update `FrontendConfigDTO.java`
2. Update `AppConfig` interface in `runtime-config.ts`
3. Update `ConfigController.java` to populate new value

### Q: Can I still use environment variables for secrets?

**A:** Yes! Secrets go in backend environment variables (not frontend):

```yaml
# docker-compose.yml
api-gateway:
  environment:
    - DB_PASSWORD=${DB_PASSWORD}        # ✅ Backend secret
    - COGNITO_USER_POOL_ID=${...}      # ✅ Backend config

web-frontend:
  environment:
    - VITE_SECRET=xxx                  # ❌ DON'T DO THIS
```

Frontend never has secrets. Backend serves only what frontend needs.

### Q: How does this work with CDK deployments?

**A:** CDK deploys backend with environment-specific values:

```typescript
// infrastructure/lib/config/dev-config.ts
export const devConfig = {
  envName: 'development',
  cognito: { ... },
  // ...
};
```

Backend reads these from environment variables and serves them via `/api/v1/config`.

### Q: What about feature flags?

**A:** Feature flags are in backend config:

```java
FeatureFlagsDTO.builder()
  .notifications(true)
  .analytics(!"development".equals(environment))
  .pwa(!"development".equals(environment))
  .build()
```

Change flags without rebuilding frontend!

## Resources

- [Configuration Guide](../web-frontend/CONFIG.md)
- [Runtime Config Loader](../web-frontend/src/config/runtime-config.ts)
- [Config Context](../web-frontend/src/contexts/ConfigContext.tsx)
- [Backend Config Controller](../api-gateway/src/main/java/ch/batbern/gateway/config/ConfigController.java)

## Support

If you encounter issues:

1. Check [Configuration Guide](../web-frontend/CONFIG.md)
2. Verify backend is serving config: `curl http://localhost:8080/api/v1/config`
3. Check backend logs: `docker-compose logs api-gateway`
4. Review browser console for errors

---

**Migration completed:** Runtime configuration enables "build once, deploy everywhere" - a modern best practice for web applications.
