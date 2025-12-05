# Fargate Spot Cost Optimization - Priority 2 Implementation

**Date:** December 5, 2025
**Branch:** `refactor/reduce-cloudwatch-costs-priority1`
**Commit:** 9b441888
**Related:** GitHub Issue #288, Priority 1 (CloudWatch): ca5ac30c

---

## Executive Summary

Implemented Priority 2 cost optimization from GitHub issue #288 analysis: **Enabled Fargate Spot capacity providers with 70% Spot / 30% On-Demand split for non-production environments**.

**Expected Monthly Savings:**
- Staging: $15-20/month (20-27% reduction from $73.87)
- **Combined with Priority 1: $65-85/month total savings**

---

## Problem Statement

### Current State (November 2025)
- **Staging ECS Fargate Cost:** $73.87/month (24.6% of total)
- **Position:** #1 cost driver in staging
- **Issue:** Running 100% Fargate On-Demand, missing Spot cost savings
- **Fargate Spot Pricing:** ~70% cheaper than On-Demand

### Why Fargate Spot?

| Metric | On-Demand | Spot | Savings |
|--------|-----------|------|---------|
| vCPU (per hour) | $0.04656 | $0.01334 | 71% |
| Memory (per GB/hour) | $0.00511 | $0.00146 | 71% |
| **Typical Task** | $1.00 | $0.29 | **71%** |

With a 70/30 Spot/On-Demand split, we achieve **~50% savings** on compute costs.

---

## Solution Implemented

### Capacity Provider Strategy

Configured ECS services to use a mixed capacity provider strategy:

```typescript
capacityProviderStrategies: [
  {
    capacityProvider: 'FARGATE_SPOT',
    weight: 70,
    base: 0,
  },
  {
    capacityProvider: 'FARGATE',
    weight: 30,
    base: 1, // At least 1 task on On-Demand
  },
]
```

**How it works:**
- **Weight 70/30:** For every 10 tasks, ~7 run on Spot, ~3 on On-Demand
- **Base 1:** First task always runs on On-Demand for stability
- **Auto-scaling:** Maintains ratio as services scale up/down
- **Interruption handling:** Spot tasks automatically replaced

### Files Modified

| File | Change | Impact |
|------|--------|--------|
| `lib/stacks/cluster-stack.ts` | Enabled `enableFargateCapacityProviders` | Cluster supports both capacity providers |
| `lib/constructs/domain-service-construct.ts` | Added capacity provider strategy | All 5 domain services use Spot |
| `lib/stacks/api-gateway-service-stack.ts` | Added capacity provider strategy via CFN override | API Gateway uses Spot |

### Affected Services (Staging)

1. ✅ API Gateway
2. ✅ Event Management Service
3. ✅ Speaker Coordination Service
4. ✅ Partner Coordination Service
5. ✅ Attendee Experience Service
6. ✅ Company Management Service

**Total: 6 services** using Fargate Spot in staging

---

## Implementation Details

### 1. Enable Capacity Providers on Cluster

```typescript
// lib/stacks/cluster-stack.ts
this.cluster = new ecs.Cluster(this, 'MicroservicesCluster', {
  vpc: props.vpc,
  clusterName: `batbern-${envName}`,
  // ... other config
  enableFargateCapacityProviders: true, // NEW
});
```

This automatically creates:
- `FARGATE` capacity provider (On-Demand)
- `FARGATE_SPOT` capacity provider (Spot instances)

### 2. Configure Domain Services (FargateService)

```typescript
// lib/constructs/domain-service-construct.ts
const service = new ecs.FargateService(scope, 'Service', {
  cluster: props.cluster,
  taskDefinition,
  // ... other config
  ...(!isProd && {
    capacityProviderStrategies: [
      { capacityProvider: 'FARGATE_SPOT', weight: 70, base: 0 },
      { capacityProvider: 'FARGATE', weight: 30, base: 1 },
    ],
  }),
});
```

### 3. Configure API Gateway (ApplicationLoadBalancedFargateService)

```typescript
// lib/stacks/api-gateway-service-stack.ts
const cfnService = this.service.service.node.defaultChild as ecs.CfnService;

if (!isProd) {
  cfnService.addPropertyOverride('CapacityProviderStrategy', [
    { CapacityProvider: 'FARGATE_SPOT', Weight: 70, Base: 0 },
    { CapacityProvider: 'FARGATE', Weight: 30, Base: 1 },
  ]);
}
```

**Why property override?**
`ApplicationLoadBalancedFargateService` doesn't expose `capacityProviderStrategies` property in L2 construct.

---

## Expected Cost Impact

### Staging Environment Savings

**Current ECS Cost:** $73.87/month

**Calculation:**
- On-Demand cost: $73.87
- Spot cost for 70% of tasks: $73.87 × 0.70 × 0.29 = $15.00
- On-Demand cost for 30% of tasks: $73.87 × 0.30 = $22.16
- **New total: $37.16**
- **Savings: $36.71/month (50% reduction)**

**Conservative estimate** (accounting for overhead): **$15-20/month savings**

### Cumulative Savings

| Optimization | Monthly Savings | Status |
|--------------|-----------------|--------|
| Priority 1: CloudWatch (7-day retention) | $50-65 | ✅ Implemented |
| Priority 2: Fargate Spot (70/30 split) | $15-20 | ✅ Implemented |
| **Total Savings** | **$65-85** | **22-28% reduction** |

**New Staging Cost:**
- Before: $300.75/month
- After Priority 1+2: $215-235/month

---

## Deployment Instructions

### 1. Deploy to Staging

```bash
# Set AWS profile
export AWS_PROFILE=batbern-staging

# Navigate to infrastructure directory
cd infrastructure

# Preview changes
npx cdk diff BATbern-staging-Cluster
npx cdk diff BATbern-staging-ApiGatewayService
npx cdk diff BATbern-staging-EventManagement
npx cdk diff BATbern-staging-SpeakerCoordination
npx cdk diff BATbern-staging-PartnerCoordination
npx cdk diff BATbern-staging-AttendeeExperience
npx cdk diff BATbern-staging-CompanyManagement

# Deploy all stacks
npx cdk deploy --all --require-approval never
```

### 2. Verify Capacity Providers

```bash
# Check cluster capacity providers
AWS_PROFILE=batbern-staging aws ecs describe-clusters \
  --clusters batbern-staging \
  --query 'clusters[0].capacityProviders' \
  --output json

# Expected output: ["FARGATE", "FARGATE_SPOT"]
```

### 3. Verify Task Distribution

```bash
# List tasks for a service
AWS_PROFILE=batbern-staging aws ecs list-tasks \
  --cluster batbern-staging \
  --service-name api-gateway \
  --output json

# Describe tasks to see capacity provider
AWS_PROFILE=batbern-staging aws ecs describe-tasks \
  --cluster batbern-staging \
  --tasks <task-arn> \
  --query 'tasks[*].{TaskArn:taskArn,CapacityProvider:capacityProviderName,LaunchType:launchType}' \
  --output table

# Expected: Mix of FARGATE and FARGATE_SPOT capacity providers
```

### 4. Monitor for Spot Interruptions

```bash
# Check CloudWatch for Spot interruption events
AWS_PROFILE=batbern-staging aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name SpotInterruptionRate \
  --dimensions Name=ClusterName,Value=batbern-staging \
  --start-time $(date -u -v-1d +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 3600 \
  --statistics Average \
  --output table
```

---

## How Fargate Spot Works

### Spot Instance Lifecycle

1. **Task Launch:**
   - ECS attempts to place task on Spot capacity
   - Falls back to On-Demand if Spot unavailable
   - Honors weight ratio over time

2. **Running:**
   - Task runs normally on Spot infrastructure
   - ~70% cheaper than On-Demand
   - Performance identical to On-Demand

3. **Interruption (rare):**
   - AWS sends 2-minute warning
   - ECS launches replacement task (Spot or On-Demand)
   - Old task gracefully terminates
   - **Zero downtime** with `minHealthyPercent: 100`

4. **Recovery:**
   - New task starts on available capacity
   - Service maintains desired count
   - Ratio rebalances naturally

### Interruption Rates

Historical Fargate Spot interruption rates:
- **Average:** < 5% of tasks per month
- **Impact:** Minimal with proper configuration
- **Recovery:** Automatic, typically < 1 minute

---

## Validation & Testing

### Success Criteria

- [ ] Cluster shows both FARGATE and FARGATE_SPOT capacity providers
- [ ] Services show capacity provider strategy in CFN template
- [ ] Tasks distributed ~70% Spot / ~30% On-Demand over time
- [ ] No service degradation or increased error rates
- [ ] Spot interruptions handled gracefully (< 1min recovery)
- [ ] ECS costs decrease by 20-27% within 30 days

### Testing Checklist

**Immediate (Day 1):**
- [ ] Deploy to staging
- [ ] Verify capacity providers enabled
- [ ] Confirm tasks launching on Spot
- [ ] Check service health endpoints
- [ ] Verify no ALB 5xx errors

**Short-term (Week 1):**
- [ ] Monitor task distribution (should trend toward 70/30)
- [ ] Observe Spot interruption frequency
- [ ] Verify zero-downtime during interruptions
- [ ] Check application logs for issues
- [ ] Monitor API response times (should be unchanged)

**Long-term (30 Days):**
- [ ] Validate 20-27% ECS cost reduction
- [ ] Compare December costs to November baseline
- [ ] Assess interruption impact (should be minimal)
- [ ] Collect team feedback on stability

### Rollback Plan

If Spot interruptions cause issues:

**Option 1: Adjust Ratio**
```typescript
// Reduce Spot to 50%, increase On-Demand to 50%
capacityProviderStrategies: [
  { capacityProvider: 'FARGATE_SPOT', weight: 50, base: 0 },
  { capacityProvider: 'FARGATE', weight: 50, base: 1 },
]
```

**Option 2: Increase Base**
```typescript
// Ensure 2 tasks always On-Demand
capacityProviderStrategies: [
  { capacityProvider: 'FARGATE_SPOT', weight: 70, base: 0 },
  { capacityProvider: 'FARGATE', weight: 30, base: 2 }, // Increased from 1
]
```

**Option 3: Disable Spot**
```typescript
// Revert to 100% On-Demand
// Remove capacityProviderStrategies configuration
```

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Spot interruptions | Medium | Low | 30% On-Demand + base=1 + minHealthyPercent=100 |
| Increased interruptions during high demand | Low | Medium | Auto-scaling uses both capacity types |
| Service degradation | Very Low | High | Only non-prod, easy rollback, production unchanged |
| Cost savings less than expected | Low | Low | Worst case: minimal savings, no degradation |
| Spot unavailability | Very Low | Low | Falls back to On-Demand automatically |

**Overall Risk Level:** 🟢 Low

**Why low risk?**
- Only affects staging/dev (not production)
- 30% On-Demand ensures availability
- Zero-downtime deployment settings
- Automatic interruption handling
- Easy rollback if needed

---

## Spot Interruption Best Practices

### What We're Already Doing ✅

1. **Capacity Provider Strategy:**
   - Mixed 70/30 ratio prevents "all Spot" risk
   - Base of 1 ensures minimum On-Demand presence

2. **Zero-Downtime Configuration:**
   - `minHealthyPercent: 100` - Never drop below desired count
   - `maxHealthyPercent: 200` - Allow extra tasks during replacement

3. **Health Checks:**
   - ALB health checks detect unhealthy tasks
   - Spring Boot Actuator `/health` endpoint

4. **Auto-scaling:**
   - Scales based on CPU utilization
   - Maintains 70/30 ratio automatically

### Additional Recommendations

**Monitor These Metrics:**
- ECS Service `DesiredTaskCount` vs `RunningTaskCount`
- ALB `TargetResponseTime` and `HTTPCode_Target_5XX_Count`
- CloudWatch Logs for "Spot interruption" events
- Cost Explorer for actual ECS spend

**Alert On:**
- Running tasks < desired count for > 5 minutes
- 5xx error rate > 1%
- Average response time > 500ms

---

## Next Steps

### Immediate (This Week)
1. ✅ Create refactoring branch
2. ✅ Update CDK code for capacity providers
3. ✅ Commit changes
4. ✅ Document implementation
5. ⏳ Deploy to staging (requires approval)
6. ⏳ Verify deployment and task distribution

### Short-term (Next 2 Weeks)
7. Monitor Spot interruption frequency
8. Verify zero-downtime during interruptions
9. Collect feedback from team
10. Adjust ratio if needed (unlikely)

### Long-term (30 Days)
11. Validate $15-20/month cost savings
12. Compare December ECS costs to November
13. If successful, proceed to Priority 3 (VPC Endpoints)

---

## Priority 3 Preview: VPC Endpoints

**Next optimization:** Add VPC Endpoints to reduce NAT Gateway costs

**Potential Savings:** $15-20/month net
**Effort:** Medium (4-6 hours)
**Risk:** Medium (requires testing)

**Combined Savings (Priorities 1-3):** $80-105/month (27-35% reduction)

---

## Questions & Answers

**Q: What happens when a Spot task is interrupted?**
A: ECS receives 2-minute warning, launches replacement task (Spot or On-Demand), old task terminates gracefully. With `minHealthyPercent: 100`, we maintain service availability.

**Q: How often are Spot tasks interrupted?**
A: Historically < 5% per month for Fargate Spot. With 30% On-Demand baseline, impact is minimal.

**Q: Why not 100% Spot for maximum savings?**
A: Too risky. During high AWS demand, all-Spot could leave us with insufficient capacity. 70/30 balances savings with reliability.

**Q: Can we use Spot in production?**
A: Not recommended. Production requires maximum reliability. Current implementation only enables Spot for staging/dev.

**Q: What if Spot is unavailable?**
A: ECS automatically uses On-Demand capacity. The weight ratio is a goal, not a hard requirement.

**Q: How do we know if tasks are on Spot?**
A: Use `aws ecs describe-tasks` and check `capacityProviderName` field. Should show mix of `FARGATE` and `FARGATE_SPOT`.

---

## References

- **GitHub Issue:** #288 - Cost-optimized staging2 environment
- **Cost Analysis:** `/tmp/github-issue-288-analysis.md`
- **Priority 1:** CloudWatch log retention (commit ca5ac30c, docs: `cost-optimization-priority1-cloudwatch.md`)
- **Priority 2:** Fargate Spot (commit 9b441888, this document)
- **AWS Docs:** https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-capacity-providers.html

---

## Approval Required

This change requires deployment approval from:
- [ ] Infrastructure Lead
- [ ] DevOps Team
- [ ] Product Owner (for staging environment)

**Recommended Action:** Approve deployment to staging environment to validate $15-20/month cost savings with minimal risk.

**Cumulative Impact:** With Priority 1 + Priority 2, we expect **$65-85/month total savings** (22-28% reduction) for staging environment.
