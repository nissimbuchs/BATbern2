# BATbern Platform Stability Improvements - REVISED Implementation Plan

**Created**: 2026-01-10
**Updated**: 2026-01-10 (Phases 1 & 2 Complete)
**Status**: ✅ **PHASES 1 & 2 COMPLETE** - Ready for deployment | 🔜 Phase 3 Pending
**Branch**: `fix/platform-stability-improvements`
**Commit**: `47e50831`
**Cost Impact**: ~$24/month for staging

## Executive Summary

**Root Cause Analysis**: Event Management service OOM crash (exit code 137) on 2026-01-10 09:21 UTC caused by:
1. **Insufficient memory** (512MB too low for Spring Boot + EventBridge)
2. **Blocking EventBridge publishing** (`.get(5, TimeUnit.SECONDS)` blocks threads under load)
3. **FARGATE_SPOT interruptions** (4-5 min outages in staging - DEFERRED for cost reasons)

**Solution**: Three-phase targeted approach:
- **Phase 1**: Fix Event Management only (memory + 2nd task)
- **Phase 2**: Async EventBridge for ALL services (fixes root cause)
- **Phase 3**: Proactive monitoring for ALL services (24 alarms)

---

## Phase 1: Targeted Event Management Fixes ✅ COMPLETE

### Objective
Fix the OOM crash in Event Management specifically while keeping other services unchanged.

**Status**: ✅ Implemented and tested
**Files Changed**: 1 (event-management-stack.ts)

### Changes Required

#### 1.1 Increase Event Management Memory

**File**: `infrastructure/lib/stacks/event-management-stack.ts`

**Line 51 - Change**:
```typescript
// Before:
memoryLimitMiB: 512, // Reduced from 1024 MB (Priority 4: ECS Right-Sizing - was at 46% utilization)

// After:
memoryLimitMiB: 1024, // Increased from 512 MB to fix OOM crash (exit code 137, 2026-01-10)
```

#### 1.2 Add Second Task for Event Management

**File**: `infrastructure/lib/stacks/event-management-stack.ts`

**After line 79 - Add override**:
```typescript
// Override desiredCount for Event Management specifically (2 tasks for HA)
const cfnService = this.service.service.node.defaultChild as ecs.CfnService;
cfnService.addPropertyOverride('DesiredCount', 2);
```

**Note**: This overrides the domain-service-construct's default `desiredCount: 1` for staging.

#### 1.3 Keep FARGATE_SPOT for Other Services

**No changes** to `domain-service-construct.ts` - other services keep:
- 512MB memory (Company/Partner already at 1024MB)
- 1 task in staging
- FARGATE_SPOT 70/30 split

### Cost Impact

**Event Management only**:
- Before: 512MB × 1 task × 0.7 (Spot) = ~$6/month
- After: 1024MB × 2 tasks × 1.0 (on-demand) = ~$30/month
- **Increase**: ~$24/month

**Rationale**: With only 2 tasks, must use on-demand (can't split Spot effectively). Acceptable for critical service stability.

---

## Phase 2: Async EventBridge Publishing ✅ COMPLETE

### Objective
Replace blocking `.get(5, TimeUnit.SECONDS)` with fire-and-forget async publishing + Resilience4j patterns.

**Status**: ✅ Implemented and tested
**Files Changed**: 6 (shared-kernel), 2 new test files
**Test Results**: 249/249 tests passing ✅

### 2.1 Add Resilience4j Dependencies

**File**: `shared-kernel/build.gradle` (after line 52)

```gradle
// Resilience4j for circuit breaker, bulkhead, and retry patterns
implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.3.0'
implementation 'io.github.resilience4j:resilience4j-circuitbreaker:2.3.0'
implementation 'io.github.resilience4j:resilience4j-bulkhead:2.3.0'
implementation 'io.github.resilience4j:resilience4j-retry:2.3.0'
implementation 'io.github.resilience4j:resilience4j-reactor:2.3.0'

// ThreadFactoryBuilder for bounded thread pools
implementation 'com.google.guava:guava:33.0.0-jre'
```

### 2.2 Configure Resilience4j

**File**: `shared-kernel/src/main/resources/application-shared.yml` (after line 14)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      eventBridgePublisher:
        slidingWindowSize: 50
        failureRateThreshold: 60          # Open after 60% failures
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 5

  bulkhead:
    instances:
      eventBridgePublisher:
        maxConcurrentCalls: 50            # Limit concurrent publishes
        maxWaitDuration: 100ms

  retry:
    instances:
      eventBridgePublisher:
        maxAttempts: 3
        waitDuration: 1000ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
```

### 2.3 Configure Bounded Thread Pool

**File**: `shared-kernel/src/main/java/ch/batbern/shared/config/EventBridgeConfig.java`

Replace `eventBridgeAsyncClient()` method (lines 34-47) with bounded thread pool configuration.

**Key additions**:
- ThreadPoolExecutor: 10 core, 50 max threads
- Bounded queue: 100 max pending tasks
- CallerRunsPolicy: backpressure when queue full

### 2.4 Refactor EventBridgeEventPublisher

**File**: `shared-kernel/src/main/java/ch/batbern/shared/events/EventBridgeEventPublisher.java`

**Key changes**:
1. Add Resilience4j components (CircuitBreaker, Bulkhead, Retry)
2. Replace `publish()`: Remove `.get(5, TimeUnit.SECONDS)`, make fire-and-forget
3. Update `publishBatch()`: Make concurrent and non-blocking
4. Deprecate `publishWithRetry()`: Built-in retry via Resilience4j

**Benefits**:
- Eliminates thread blocking (root cause of OOM)
- Circuit breaker prevents cascade failures
- Bulkhead limits concurrent calls (prevents thread pool exhaustion)
- All 6 services benefit from this shared-kernel change

---

## Phase 3: CloudWatch Monitoring & Alarms

### Objective
Proactive detection of memory pressure, OOM kills, and EventBridge failures across ALL services.

### 3.1 Create ECS Service Alarms Construct

**File**: `infrastructure/lib/constructs/ecs-service-alarms.ts` (NEW)

**4 alarm types per service** (24 total alarms for 6 services):
1. **High Memory Utilization** (80% threshold, 5-min evaluation)
2. **OOM Kill Detection** (metric filter on Container Insights logs)
3. **Task Failure Rate** (abnormal restarts)
4. **EventBridge Publishing Failures** (custom application metric)

**Pattern**: Follow `user-sync-alarms.ts` structure.

### 3.2 Integrate with All Service Stacks

**Files to modify** (6 service stacks):
- `infrastructure/lib/stacks/event-management-stack.ts`
- `infrastructure/lib/stacks/speaker-coordination-stack.ts`
- `infrastructure/lib/stacks/partner-coordination-stack.ts`
- `infrastructure/lib/stacks/attendee-experience-stack.ts`
- `infrastructure/lib/stacks/company-management-stack.ts`
- `infrastructure/lib/stacks/api-gateway-service-stack.ts`

**Add after service creation** (~line 82):
```typescript
import { EcsServiceAlarms } from '../constructs/ecs-service-alarms';

const serviceAlarms = new EcsServiceAlarms(this, 'ServiceAlarms', {
  environment: envName,
  clusterName: props.cluster.clusterName,
  serviceName: this.service.service.serviceName,
  alarmTopic: props.alarmTopic,
  thresholds: {
    memoryUtilization: 80,
    oomKillCount: envName === 'production' ? 1 : 3,
    taskFailureCount: envName === 'production' ? 2 : 5,
    eventBridgePublishingFailures: envName === 'production' ? 5 : 10,
  },
});
```

### 3.3 Update GitHub Issues Integration

**File**: `infrastructure/lambda/github-issues-integration/index.ts`

**Update `getLabels()` function** (lines 222-252):
- Add `severity:critical` for OOM kills
- Add `component:ecs` for ECS-related alarms
- Add `service:{service-name}` for service attribution

### 3.4 Backend Instrumentation

**Files** (3 services with EventBridge):
- `services/event-management-service/src/main/java/.../events/EventBridgePublisher.java`
- `services/company-user-management-service/src/main/java/.../events/EventBridgePublisher.java`
- `services/partner-coordination-service/src/main/java/.../events/EventBridgePublisher.java`

**Add CloudWatch metric publishing** on EventBridge failures:
```java
cloudWatchClient.putMetricData(
    PutMetricDataRequest.builder()
        .namespace("BATbern/EventBridge")
        .metricData(MetricDatum.builder()
            .metricName("PublishingFailures")
            .value(1)
            .dimensions(Dimension.builder()
                .name("ServiceName")
                .value(serviceName)
                .build())
            .build())
        .build()
);
```

---

## Deployment Sequence

### Day 1: Event Management + EventBridge

**Morning** (2 hours):
1. Create feature branch: `fix/platform-stability-improvements`
2. **Phase 1**: Modify `event-management-stack.ts` (2 changes)
3. **Phase 2**: Implement EventBridge async changes (5 files in shared-kernel)
4. Run tests: `npm test` and `./gradlew :shared-kernel:test`

**Afternoon** (2 hours):
1. Deploy Phase 1 + 2 to staging:
   ```bash
   ./gradlew :shared-kernel:clean :shared-kernel:build :shared-kernel:publishToMavenLocal
   cd infrastructure
   npm run deploy:staging:layer4-services
   ```
2. Validate Event Management: 2 tasks, 1024MB, healthy
3. Monitor logs for EventBridge async behavior

### Day 2: Monitoring Implementation

**Morning** (3 hours):
1. **Phase 3**: Implement CloudWatch alarms (new construct + 6 stack integrations)
2. Update GitHub Issues Lambda
3. Add backend instrumentation (3 services)

**Afternoon** (2 hours):
1. Deploy Phase 3:
   ```bash
   npm run deploy:staging:layer0-foundation
   npm run deploy:staging:layer4-services
   ```
2. Verify 24 alarms created (4 × 6 services)
3. Test alarm workflow with intentional memory spike

### Day 3-4: Validation & Production

**Validation** (48 hours):
- Monitor Event Management memory: should be 45-50% (down from 90%+)
- Zero OOM crashes
- EventBridge publishing: no timeouts, circuit breaker stays CLOSED
- Other services: continue normal operation at 512MB

**Production Deployment**:
Only Event Management memory change applies (production already has 2 tasks per service).

---

## Critical Files Summary

### Phase 1 - Event Management Only (1 file)
1. `infrastructure/lib/stacks/event-management-stack.ts` (line 51, add override after line 79)

### Phase 2 - Shared Kernel (5 files, all services benefit)
2. `shared-kernel/build.gradle`
3. `shared-kernel/src/main/resources/application-shared.yml`
4. `shared-kernel/src/main/java/ch/batbern/shared/config/EventBridgeConfig.java`
5. `shared-kernel/src/main/java/ch/batbern/shared/events/EventBridgeEventPublisher.java`
6. `shared-kernel/src/test/java/ch/batbern/shared/config/TestResilience4jConfig.java` (NEW)

### Phase 3 - Monitoring (11 files, all services)
7. `infrastructure/lib/constructs/ecs-service-alarms.ts` (NEW)
8. `infrastructure/lib/stacks/event-management-stack.ts` (add alarms)
9. `infrastructure/lib/stacks/speaker-coordination-stack.ts` (add alarms)
10. `infrastructure/lib/stacks/partner-coordination-stack.ts` (add alarms)
11. `infrastructure/lib/stacks/attendee-experience-stack.ts` (add alarms)
12. `infrastructure/lib/stacks/company-management-stack.ts` (add alarms)
13. `infrastructure/lib/stacks/api-gateway-service-stack.ts` (add alarms)
14. `infrastructure/lambda/github-issues-integration/index.ts`
15. `services/event-management-service/src/main/java/.../events/*` (metrics)
16. `services/company-user-management-service/src/main/java/.../events/*` (metrics)
17. `services/partner-coordination-service/src/main/java/.../events/*` (metrics)

**Total**: 17 files

---

## Success Criteria

### Immediate (0-2 hours)
- ✅ Event Management: 2 tasks running, 1024MB memory
- ✅ Other services: unchanged (1 task, 512MB or 1024MB)
- ✅ All API tests pass
- ✅ No EventBridge timeout errors in logs

### 24-48 hours
- ✅ Event Management memory utilization: 45-50% (healthy)
- ✅ Zero OOM crashes in Event Management
- ✅ Circuit breaker stays CLOSED across all services
- ✅ EventBridge publishing success rate > 99.9%
- ✅ Other services: normal operation continues

### 1 week
- ✅ No OOM alarms triggered for ANY service
- ✅ If other services approach 80% memory, alarms trigger proactively
- ✅ MTBF > 1 week

---

## How to Resume Implementation

**In new session, run**:
```bash
cd /Users/nissim/dev/bat/BATbern-feature/infrastructure
/dev  # Start with dev agent
```

Then say: **"Implement the platform stability improvements plan from docs/plans/platform-stability-improvements.md"**

The plan contains all details needed for implementation.
