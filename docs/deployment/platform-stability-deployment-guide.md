# Platform Stability Improvements - Deployment Guide

**Date**: 2026-01-10
**Branch**: `fix/platform-stability-improvements` (to be created)
**Status**: ✅ **Phases 1 & 2 COMPLETE** | 🔜 Phase 3 PENDING

---

## Executive Summary

This deployment implements critical fixes for the OOM crash in Event Management service (exit code 137, 2026-01-10 09:21 UTC). **Phases 1 & 2 are complete and ready for deployment**. Phase 3 (monitoring) is optional and can be deployed separately.

### What Changed

**✅ COMPLETE - Phase 1: Event Management Targeted Fixes**
- Memory increased from 512MB to 1024MB (fixes OOM crash)
- Desired task count set to 2 (ensures zero-downtime deployments)
- **Cost Impact**: +$24/month staging (~$6 → ~$30)

**✅ COMPLETE - Phase 2: Async EventBridge Publishing**
- Eliminated blocking `.get(5, TimeUnit.SECONDS)` calls (root cause of thread exhaustion)
- Added Resilience4j circuit breaker, bulkhead, and retry patterns
- Fire-and-forget async publishing across ALL 6 services
- Bounded thread pool prevents unbounded growth
- **Benefit**: ALL services benefit from improved EventBridge stability

**🔜 PENDING - Phase 3: CloudWatch Monitoring** (Optional, can be done later)
- 24 alarms across 6 services (4 per service)
- GitHub Issues integration for automated bug creation
- Proactive detection of memory pressure, OOM kills, EventBridge failures

---

## Files Changed

### Phase 1 (1 file)
- `infrastructure/lib/stacks/event-management-stack.ts`

### Phase 2 (6 files)
- `shared-kernel/build.gradle`
- `shared-kernel/src/main/resources/application-shared.yml`
- `shared-kernel/src/main/java/ch/batbern/shared/config/EventBridgeConfig.java`
- `shared-kernel/src/main/java/ch/batbern/shared/events/EventBridgeEventPublisher.java`
- `shared-kernel/src/test/java/ch/batbern/shared/config/TestResilience4jConfig.java` (NEW)
- `shared-kernel/src/test/java/ch/batbern/shared/integration/events/EventBridgePublisherTest.java`
- `shared-kernel/src/test/java/ch/batbern/shared/config/TestEventBridgeConfig.java`

**Total**: 7 files modified, 2 new files created

---

## Pre-Deployment Checklist

### Development Environment
- [ ] All changes committed to feature branch `fix/platform-stability-improvements`
- [ ] Shared-kernel built and published to Maven Local: `./gradlew :shared-kernel:clean :shared-kernel:build :shared-kernel:publishToMavenLocal`
- [ ] Infrastructure TypeScript compiled: `cd infrastructure && npm run build`
- [ ] All tests passing:
  - `./gradlew :shared-kernel:test` (249 tests, all passing ✅)
  - `npm test` (infrastructure tests)

### Code Review
- [ ] Review `EventBridgeEventPublisher.java` - async fire-and-forget pattern
- [ ] Review `event-management-stack.ts` - memory and task count changes
- [ ] Verify Resilience4j configuration in `application-shared.yml`

---

## Deployment Steps

### Step 1: Create Feature Branch & Commit

```bash
# Create feature branch
git checkout -b fix/platform-stability-improvements

# Stage all changes
git add infrastructure/lib/stacks/event-management-stack.ts
git add shared-kernel/build.gradle
git add shared-kernel/src/main/resources/application-shared.yml
git add shared-kernel/src/main/java/ch/batbern/shared/config/EventBridgeConfig.java
git add shared-kernel/src/main/java/ch/batbern/shared/events/EventBridgeEventPublisher.java
git add shared-kernel/src/test/java/ch/batbern/shared/config/TestResilience4jConfig.java
git add shared-kernel/src/test/java/ch/batbern/shared/integration/events/EventBridgePublisherTest.java
git add shared-kernel/src/test/java/ch/batbern/shared/config/TestEventBridgeConfig.java

# Commit with descriptive message
git commit -m "fix(platform-stability): resolve OOM crash and improve EventBridge reliability

Phase 1: Event Management Targeted Fixes
- Increase memory from 512MB to 1024MB (fixes OOM crash exit code 137)
- Set desired task count to 2 for zero-downtime deployments
- Cost impact: +\$24/month staging

Phase 2: Async EventBridge Publishing (ALL Services)
- Replace blocking .get(5, TimeUnit.SECONDS) with fire-and-forget async
- Add Resilience4j circuit breaker, bulkhead, and retry patterns
- Configure bounded thread pool (10-50 threads, 100 queue limit)
- Benefits all 6 microservices with improved fault tolerance

Test Coverage:
- All 249 shared-kernel tests passing
- Added TestResilience4jConfig for test environments
- Updated EventBridgePublisherTest for async patterns

Breaking Changes: None
Deployment: Requires shared-kernel republish + service redeployments

Relates-to: OOM incident 2026-01-10 09:21 UTC
"
```

### Step 2: Push to Remote

```bash
# Push feature branch
git push -u origin fix/platform-stability-improvements
```

### Step 3: Deploy to Staging

**Option A: Via GitHub Actions** (Recommended)
1. Open pull request from `fix/platform-stability-improvements` → `develop`
2. Wait for CI checks to pass
3. Merge to `develop` - auto-deploys to staging
4. Monitor deployment progress in GitHub Actions

**Option B: Manual Deployment**
```bash
# Ensure AWS_PROFILE is set
export AWS_PROFILE=batbern-staging

# Build and publish shared-kernel
./gradlew :shared-kernel:clean :shared-kernel:build :shared-kernel:publishToMavenLocal

# Deploy infrastructure (Phase 1 changes)
cd infrastructure
npm run deploy:staging:layer4-services

# Services will auto-deploy via GitHub Actions when merged to develop
```

### Step 4: Validation (24-48 hours)

**Immediate (0-2 hours):**
- [ ] Event Management: 2 tasks running, 1024MB memory
- [ ] Other services: unchanged (1 task, 512MB or 1024MB)
- [ ] All Bruno API tests pass: `./scripts/ci/run-bruno-tests.sh`
- [ ] No EventBridge timeout errors in logs
- [ ] Check CloudWatch Logs for circuit breaker state (should stay CLOSED)

```bash
# Tail Event Management logs
AWS_PROFILE=batbern-staging aws logs tail /aws/ecs/BATbern-staging/event-management --since 30m --follow

# Search for EventBridge errors
AWS_PROFILE=batbern-staging aws logs tail /aws/ecs/BATbern-staging/event-management --since 1h --filter-pattern "ERROR.*EventBridge"

# Check circuit breaker status
AWS_PROFILE=batbern-staging aws logs tail /aws/ecs/BATbern-staging/event-management --since 1h --filter-pattern "Circuit breaker"
```

**24-48 hours:**
- [ ] Event Management memory utilization: 45-50% (down from 90%+)
- [ ] Zero OOM crashes in Event Management
- [ ] Circuit breaker stays CLOSED across all services
- [ ] EventBridge publishing success rate > 99.9%
- [ ] Other services: normal operation continues

```bash
# Monitor ECS service status
AWS_PROFILE=batbern-staging aws ecs describe-services \
  --cluster batbern-staging \
  --services $(aws ecs list-services --cluster batbern-staging --query 'serviceArns' --output text) \
  --query 'services[*].{Name:serviceName,Status:status,Running:runningCount,Desired:desiredCount}'

# Check for recent OOM events
AWS_PROFILE=batbern-staging aws logs insights query \
  --log-group-names /aws/ecs/BATbern-staging/event-management \
  --start-time $(date -v-24H +%s) \
  --end-time $(date +%s) \
  --query-string 'fields @timestamp, @message | filter @message like /exit code 137/ | sort @timestamp desc'
```

**1 week:**
- [ ] No OOM crashes in ANY service
- [ ] MTBF > 1 week (mean time between failures)

---

## Rollback Plan

If issues arise, rollback is simple since Phase 1+2 are backward compatible:

### Quick Rollback (5 minutes)

```bash
# Revert Event Management stack changes
git revert <commit-hash>
git push origin develop

# Or manually decrease memory back to 512MB and desiredCount to 1
cd infrastructure
# Edit event-management-stack.ts: memoryLimitMiB: 512, remove desiredCount override
npm run deploy:staging:layer4-services
```

### Full Rollback (15 minutes)

```bash
# Revert all changes
git revert <commit-hash>
git push origin develop

# Rebuild and republish old shared-kernel
git checkout develop~1
./gradlew :shared-kernel:clean :shared-kernel:build :shared-kernel:publishToMavenLocal
cd infrastructure
npm run deploy:staging:layer4-services
```

**Note**: EventBridge async changes (Phase 2) are backward compatible. Old services continue working with new shared-kernel.

---

## Production Deployment

**Only Event Management memory change applies** - production already has 2 tasks per service.

### When to Deploy to Production
- [ ] Staging stable for 48 hours
- [ ] Zero OOM crashes observed
- [ ] Circuit breaker stays CLOSED
- [ ] Team approval obtained

### Production Deployment Steps

1. **Merge to main**
   ```bash
   git checkout main
   git merge develop
   git push origin main
   ```

2. **Tag release**
   ```bash
   git tag v1.5.0-stability-fix
   git push origin v1.5.0-stability-fix
   ```

3. **Deploy via GitHub Actions**
   - Actions → Deploy to Production → Run workflow
   - Select tag: `v1.5.0-stability-fix`

4. **Monitor production (same as staging validation)**

---

## Next Steps (Phase 3 - Optional)

Phase 3 adds proactive monitoring but is NOT required for stability fix. Can be implemented later as a separate deployment:

1. **Create ECS Service Alarms Construct** (`infrastructure/lib/constructs/ecs-service-alarms.ts`)
   - 4 alarm types per service (24 total):
     - High Memory Utilization (80% threshold)
     - OOM Kill Detection (Container Insights logs)
     - Task Failure Rate (abnormal restarts)
     - EventBridge Publishing Failures (custom metric)

2. **Integrate with All Service Stacks** (6 files)
   - Add alarms to each service stack

3. **Update GitHub Issues Integration** (1 file)
   - Add severity labels for OOM kills
   - Add component labels for service attribution

4. **Add Backend Instrumentation** (3 services)
   - Publish CloudWatch metrics on EventBridge failures

**Estimated effort**: 4-6 hours
**Deployment**: Layer 0 (foundation) + Layer 4 (services)

---

## Cost Impact Summary

| Environment | Before | After (Phase 1+2) | Increase |
|-------------|--------|-------------------|----------|
| **Staging** | $6/month | $30/month | +$24/month |
| **Production** | $60/month | $60/month | $0 (already 2 tasks) |

**Phase 3 (Optional)**: No additional cost (CloudWatch alarms free tier sufficient)

---

## Support & Troubleshooting

### Common Issues

**1. EventBridge timeout errors persist**
- Check circuit breaker state: Should be CLOSED
- Verify bulkhead not rejecting calls: Check logs for "Bulkhead full"
- Increase `maxConcurrentCalls` in `application-shared.yml` if needed

**2. Memory still high after deployment**
- Wait 15-30 minutes for new tasks to fully start
- Check if old code is still cached: Verify image tags match
- Force new deployment: `aws ecs update-service --force-new-deployment`

**3. Tests failing locally**
- Ensure Resilience4j dependencies downloaded: `./gradlew :shared-kernel:dependencies`
- Clear Gradle cache: `./gradlew clean`
- Rebuild: `./gradlew :shared-kernel:build`

### Logs to Check

**Circuit Breaker State Changes:**
```bash
aws logs tail /aws/ecs/BATbern-staging/event-management --filter-pattern "Circuit breaker state transition"
```

**EventBridge Publishing Errors:**
```bash
aws logs tail /aws/ecs/BATbern-staging/event-management --filter-pattern "ERROR.*EventBridge"
```

**Memory Pressure:**
```bash
aws logs tail /aws/ecs/BATbern-staging/event-management --filter-pattern "OutOfMemoryError"
```

---

## References

- Original Plan: `docs/plans/platform-stability-improvements.md`
- OOM Incident: Event Management exit code 137, 2026-01-10 09:21 UTC
- Resilience4j Docs: https://resilience4j.readme.io/
- AWS ECS Capacity Providers: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cluster-capacity-providers.html

---

## Sign-off

- [x] Phase 1 Implementation: Complete
- [x] Phase 2 Implementation: Complete
- [x] All Tests Passing: ✅ 249/249
- [x] Infrastructure Builds: ✅
- [ ] Code Review: Pending
- [ ] Staging Deployment: Pending
- [ ] Production Deployment: Pending
