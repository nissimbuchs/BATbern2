# Hybrid Docker Image Strategy

## Overview

The BATbern infrastructure uses a **hybrid Docker image approach** to solve the bootstrap problem while enabling efficient CI/CD deployments.

## The Bootstrap Problem

When deploying infrastructure for the first time:
1. ECR repositories exist but are empty (no Docker images)
2. CDK needs Docker images to create ECS task definitions
3. **Chicken-and-egg**: Can't deploy stacks without images, can't push images without ECR repos

## Solution: Conditional Image Source

The infrastructure automatically chooses the appropriate image source based on the `IMAGE_TAG` environment variable:

### Mode 1: Bootstrap / Local Development (No IMAGE_TAG)
```bash
# First deployment or local development
npm run deploy:staging
```

**Behavior:**
- CDK builds Docker images from source using `DockerImageAsset`
- All services are built and pushed to ECR
- Takes longer (~5-10 minutes) but works without pre-existing images

### Mode 2: CI/CD (IMAGE_TAG set)
```bash
# GitHub Actions deployment
IMAGE_TAG=${{ github.sha }} npm run deploy:staging
```

**Behavior:**
- CDK uses pre-built images from ECR
- Only services with new IMAGE_TAG trigger ECS updates
- Much faster (~30 seconds) and only updates changed services

## Implementation

### Shared Helper (utils/container-image-helper.ts)
```typescript
export function createContainerImage(
  scope: Construct,
  id: string,
  serviceName: string,
  envName: string,
  dockerfilePath: string
): ecs.ContainerImage {
  const imageTag = process.env.IMAGE_TAG;

  if (imageTag) {
    // CI/CD: Use pre-built ECR image
    const repository = ecr.Repository.fromRepositoryName(
      scope, id,
      `batbern/${envName}/${serviceName}`
    );
    return ecs.ContainerImage.fromEcrRepository(repository, imageTag);
  } else {
    // Bootstrap: Build from Dockerfile
    return ecs.ContainerImage.fromAsset(path.join(__dirname, '../..'), {
      file: dockerfilePath,
    });
  }
}
```

### Usage in Domain Services
```typescript
// domain-service-construct.ts
image: createContainerImage(
  scope,
  'ServiceRepository',
  `${serviceName}-service`,
  envName,
  `services/${serviceName}-service/Dockerfile`
)
```

### Usage in API Gateway
```typescript
// api-gateway-service-stack.ts
image: createContainerImage(
  this,
  'ApiGatewayRepository',
  'api-gateway',
  envName,
  'api-gateway/Dockerfile'
)
```

## CI/CD Workflow

### 1. Build Pipeline (build.yml)
- Detects changed services using `dorny/paths-filter`
- Only builds Docker images for changed services
- Pushes images to ECR with git SHA as tag
- Runs on every push to `develop` or `main`

### 2. Deploy Pipeline (deploy-staging.yml)
- Runs after build pipeline completes
- Sets `IMAGE_TAG=${{ github.sha }}`
- CDK deploys infrastructure using pre-built images
- Only services with new images trigger ECS updates

## Benefits

1. **Solves Bootstrap Problem**: First deployment works without pre-existing images
2. **Efficient CI/CD**: Only changed services are rebuilt and deployed
3. **Faster Deployments**: CDK doesn't rebuild images in deploy pipeline
4. **Predictable**: Uses exact image built by CI (same SHA)
5. **Local Development**: Developers can deploy without pushing to ECR

## Example Scenarios

### Scenario 1: First Deployment
```bash
# ECR repos empty, no images
npm run deploy:staging

# Result: All images built from source, ~10 minutes
```

### Scenario 2: Update Company Service
```bash
# GitHub Actions:
# 1. build.yml detects company-user-management-service changed
# 2. Builds only that service, pushes to ECR with SHA abc123
# 3. deploy-staging.yml runs: IMAGE_TAG=abc123 npm run deploy:staging
# 4. Only CompanyManagement stack updates, ~3 minutes
```

### Scenario 3: Update Shared Kernel
```bash
# GitHub Actions:
# 1. build.yml detects shared-kernel changed
# 2. Builds ALL services (they depend on shared-kernel)
# 3. Pushes all new images with SHA def456
# 4. deploy-staging.yml runs: IMAGE_TAG=def456 npm run deploy:staging
# 5. All service stacks update, ~20 minutes
```

## Migration Impact

- **Existing Deployments**: Continue to work (no IMAGE_TAG = build from source)
- **Future Deployments**: Benefit from faster, incremental updates
- **No Breaking Changes**: Fully backward compatible

## Files Modified

- `infrastructure/lib/utils/container-image-helper.ts` - **New**: Shared helper function
- `infrastructure/lib/constructs/domain-service-construct.ts` - Uses shared helper
- `infrastructure/lib/stacks/api-gateway-service-stack.ts` - Uses shared helper

## Related

- `.github/workflows/build.yml` - Builds and pushes Docker images
- `.github/workflows/deploy-staging.yml` - Deploys infrastructure
- `infrastructure/lib/stacks/cicd-stack.ts` - ECR repositories
