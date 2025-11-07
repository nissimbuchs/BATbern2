# ECS Service Connect Implementation

**Date**: 2025-11-07
**Issue**: Event-management service unable to access company-user-management service in staging
**Solution**: Implemented AWS ECS Service Connect for inter-service communication

## Problem Summary

The event-management service was failing to enrich speaker data with user information, showing "Unknown Speaker" in API responses. Root cause:

```json
{
  "firstName": "Unknown",
  "lastName": "Speaker",
  "username": "igor.masen"
}
```

### Root Cause

The `COMPANY_USER_MANAGEMENT_SERVICE_URL` environment variable was **not being set** for event-management service in ECS, causing it to default to `http://localhost:8081` which doesn't exist in the ECS environment.

## Solution: ECS Service Connect

Implemented **AWS ECS Service Connect** - a modern service discovery mechanism that provides:

✅ **Automatic DNS-based service discovery** (no environment variables needed)
✅ **Faster failover** than traditional CloudMap DNS lookups
✅ **No load balancer overhead** for service-to-service calls
✅ **Zero cost** (CloudMap is free when used by Service Connect)

## Architecture Changes

### Before (Failed Approach)
```
event-management → [tries http://localhost:8081] → ❌ Connection refused
```

### After (Service Connect)
```
event-management → http://company-user-management:8080 → CloudMap DNS → ✅ Direct connection
```

## Implementation Details

### 1. Infrastructure (CDK)

**File**: `infrastructure/lib/stacks/microservices-stack.ts`

#### Added CloudMap Namespace
```typescript
const namespace = new servicediscovery.PrivateDnsNamespace(this, 'ServiceDiscoveryNamespace', {
  name: `batbern.local`,
  vpc: props.vpc,
  description: `Service discovery namespace for BATbern ${envName} microservices`,
});
```

#### Enabled Service Connect on Cluster
```typescript
this.cluster = new ecs.Cluster(this, 'MicroservicesCluster', {
  vpc: props.vpc,
  clusterName: `batbern-${envName}`,
  defaultCloudMapNamespace: {
    name: `batbern.local`,
    useForServiceConnect: true,
  },
});
```

#### Configured Each Service
```typescript
// Named port mapping (required for Service Connect)
container.addPortMappings({
  name: `${serviceConfig.name}-port`,
  containerPort: serviceConfig.port,
  protocol: ecs.Protocol.TCP,
});

// Service Connect configuration
const cfnService = service.service.node.defaultChild as ecs.CfnService;
cfnService.serviceConnectConfiguration = {
  enabled: true,
  namespace: `batbern.local`,
  services: [{
    portName: `${serviceConfig.name}-port`,
    discoveryName: serviceConfig.name,
    clientAliases: [{
      port: serviceConfig.port,
      dnsName: serviceConfig.name,
    }],
  }],
};
```

### 2. Application Configuration

**File**: `services/event-management-service/src/main/resources/application.yml`

#### Updated Default URL (for AWS environments)
```yaml
user-service:
  base-url: ${COMPANY_USER_MANAGEMENT_SERVICE_URL:http://company-user-management:8080}
```

**Key Change**: Default changed from `http://localhost:8081` to `http://company-user-management:8080`

#### Added Local Profile Override
```yaml
---
spring:
  config:
    activate:
      on-profile: local

user-service:
  base-url: http://localhost:8081  # For local development
```

## Service Discovery DNS Names

All microservices are now accessible via Service Connect DNS:

| Service | Service Connect DNS | Port |
|---------|-------------------|------|
| company-user-management | `http://company-user-management:8080` | 8080 |
| event-management | `http://event-management:8080` | 8080 |
| speaker-coordination | `http://speaker-coordination:8080` | 8080 |
| partner-coordination | `http://partner-coordination:8080` | 8080 |
| attendee-experience | `http://attendee-experience:8080` | 8080 |

**DNS Resolution**: All names resolve within the `batbern.local` CloudMap namespace.

## Deployment Instructions

### Step 1: Synthesize CDK Changes
```bash
cd infrastructure
npm run build
npm run synth
```

### Step 2: Deploy to Staging
```bash
# Review changes
npm run cdk diff BATbern-staging-Microservices

# Deploy (this will update all services)
npm run cdk deploy BATbern-staging-Microservices --require-approval never
```

**Expected Changes**:
- Creates CloudMap namespace `batbern.local`
- Updates all ECS task definitions with Service Connect configuration
- Redeploys all services with new task definitions

### Step 3: Verify Deployment

#### Check Service Connect Configuration
```bash
aws ecs describe-services \
  --cluster batbern-staging \
  --services event-management \
  --region eu-central-1 \
  --query 'services[0].serviceConnectConfiguration'
```

Expected output:
```json
{
  "enabled": true,
  "namespace": "batbern.local",
  "services": [{
    "portName": "event-management-port",
    "discoveryName": "event-management",
    "clientAliases": [{
      "port": 8080,
      "dnsName": "event-management"
    }]
  }]
}
```

#### Test User API Call
```bash
# Get authentication token
TOKEN=$(./scripts/auth/get-token.sh)

# Fetch event with speakers (should show enriched user data)
curl -H "Authorization: Bearer $TOKEN" \
  https://api.staging.batbern.ch/api/v1/events/batbern57 | jq '.speakers'
```

Expected output (enriched with user data):
```json
{
  "username": "igor.masen",
  "firstName": "Igor",
  "lastName": "Masen",
  "company": "GoogleZH",
  "profilePictureUrl": "https://cdn.batbern.ch/...",
  "speakerRole": "PRIMARY_SPEAKER",
  "isConfirmed": true
}
```

## Rollback Plan

If issues occur, rollback to previous task definition:

```bash
# List previous task definitions
aws ecs list-task-definitions \
  --family-prefix batbern-staging-event-management \
  --region eu-central-1 \
  --sort DESC

# Update service to previous task definition
aws ecs update-service \
  --cluster batbern-staging \
  --service event-management \
  --task-definition batbern-staging-event-management:<previous-revision> \
  --region eu-central-1
```

## Cost Impact

**Service Connect Cost**: $0.00 (CloudMap is free when used by Service Connect)

**Additional Resource Cost**: Minimal sidecar proxy container overhead
- CPU: ~0.25 vCPU per task
- Memory: ~64 MB per task
- Estimated: $2-5/month for staging environment

## Benefits

1. **Simplified Configuration**: No environment variables needed for service URLs
2. **Automatic Service Discovery**: Services register/deregister automatically
3. **Faster Failover**: Real-time service discovery vs DNS TTL delays
4. **Future-Proof**: Supports cross-VPC and cross-cluster communication (same region)
5. **Maintainability**: No dependency on CDK service creation order

## Testing

### Local Development
No changes needed - local profile still uses `http://localhost:8081`

```bash
cd services/event-management-service
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

### Integration Tests
Integration tests unaffected (use test profile with mocked `UserApiClient`)

```bash
./gradlew :services:event-management-service:test
```

## Related Documentation

- **AWS Service Connect**: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-connect.html
- **ADR-004**: `docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md`
- **API-Based User Access**: Section "Evolution: API-Based Access Pattern (2025-11-06)" in ADR-004

## Files Changed

```
infrastructure/lib/stacks/microservices-stack.ts                     (Service Connect config)
services/event-management-service/src/main/resources/application.yml (DNS endpoint)
```

## Success Criteria

✅ Event-management can fetch user data from company-user-management
✅ Speaker enrichment shows correct user data (firstName, lastName, etc.)
✅ No "Unknown Speaker" fallbacks in API responses
✅ Service Connect metrics visible in CloudWatch
✅ Local development still works with localhost URLs

---

**Status**: Ready for Deployment
**Approver**: DevOps Team
**Deployment Window**: Non-business hours recommended (updates all services)
