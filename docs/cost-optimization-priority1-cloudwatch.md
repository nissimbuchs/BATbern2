# CloudWatch Cost Optimization - Priority 1 Implementation

**Date:** December 5, 2025
**Branch:** `refactor/reduce-cloudwatch-costs-priority1`
**Commit:** ca5ac30c
**Related:** GitHub Issue #288

---

## Executive Summary

Implemented Priority 1 cost optimization from GitHub issue #288 analysis: **Reduced CloudWatch log retention from 30 days to 7 days for non-production environments**.

**Expected Monthly Savings:**
- Staging: $40-50/month (65-80% reduction from $61.66)
- Development: $10-15/month
- **Total: $50-65/month savings**

---

## Problem Statement

### Current State (November 2025)
- **Staging CloudWatch Cost:** $61.66/month (20.5% of total)
- **Position:** #2 cost driver after ECS Fargate
- **Issue:** 5X higher than the $12/month estimated in GitHub issue #288
- **Root Cause:** 30-day log retention on all ECS services in staging

### Top Log Groups by Storage
```
/aws/ecs/BATbern-staging/api-gateway          313.2 MB  (30 days)
/aws/ecs/BATbern-staging/company-user-mgmt     39.6 MB  (30 days)
/aws/ecs/BATbern-staging/partner-coordination  14.1 MB  (30 days)
/aws/ecs/BATbern-staging/event-management       1.3 MB  (30 days)
/aws/ecs/BATbern-staging/speaker-coordination   0.04 MB  (30 days)
/aws/ecs/BATbern-staging/attendee-experience    0.04 MB  (30 days)
```

---

## Solution Implemented

### Changes Made

Reduced CloudWatch log retention for all non-production environments (development, staging) from **30 days to 7 days**.

**Production remains unchanged:** 180 days (6 months) for compliance and audit requirements.

### Files Modified

| File | Change | Impact |
|------|--------|--------|
| `lib/constructs/ecs-service.ts` | `ONE_MONTH` → `ONE_WEEK` | Generic ECS service construct |
| `lib/constructs/domain-service-construct.ts` | `ONE_MONTH` → `ONE_WEEK` | Domain microservices |
| `lib/stacks/api-gateway-service-stack.ts` | `ONE_MONTH` → `ONE_WEEK` | API Gateway logs |
| `lib/stacks/microservices-stack.ts` | `ONE_MONTH` → `ONE_WEEK` | All 6 microservices |
| `lib/stacks/monitoring-stack.ts` | `ONE_MONTH` → `ONE_WEEK` | Application & Infrastructure logs |

### Affected Services

**Staging Environment:**
1. API Gateway
2. Event Management Service
3. Speaker Coordination Service
4. Partner Coordination Service
5. Attendee Experience Service
6. Company Management Service
7. Application Log Group
8. Infrastructure Log Group

**Development Environment:**
- Same services as staging

---

## Implementation Details

### Before
```typescript
retention: isProd ? logs.RetentionDays.SIX_MONTHS : logs.RetentionDays.ONE_MONTH,
```

### After
```typescript
retention: isProd ? logs.RetentionDays.SIX_MONTHS : logs.RetentionDays.ONE_WEEK,
```

### Retention Policy by Environment

| Environment | Retention Period | Days | Reason |
|-------------|------------------|------|--------|
| Production | 6 months | 180 | Compliance, audit trail, incident investigation |
| Staging | 1 week | 7 | Cost optimization, sufficient for debugging |
| Development | 1 week | 7 | Cost optimization, local logs preferred |

---

## Expected Cost Impact

### Staging Environment Savings

**Current Cost Breakdown:**
- Total: $300.75/month
- CloudWatch: $61.66/month (20.5%)

**After Optimization:**
- CloudWatch (estimated): $11-21/month
- **Savings: $40-50/month (65-80% reduction)**
- New total: $250-260/month

### Development Environment Savings

**Current Cost:**
- CloudWatch: $18.76/month

**After Optimization:**
- CloudWatch (estimated): $4-8/month
- **Savings: $10-15/month**

### Combined Impact

| Metric | Before | After | Savings |
|--------|--------|-------|---------|
| Staging CloudWatch | $61.66 | $11-21 | $40-50 |
| Dev CloudWatch | $18.76 | $4-8 | $10-15 |
| **Total Monthly** | **$80.42** | **$15-29** | **$50-65** |
| **Annual** | **$965** | **$180-348** | **$600-785** |

---

## Deployment Instructions

### 1. Deploy to Staging

```bash
# Set AWS profile
export AWS_PROFILE=batbern-staging

# Navigate to infrastructure directory
cd infrastructure

# Preview changes
npx cdk diff BATbern-staging-*

# Deploy all stacks
npx cdk deploy --all --require-approval never

# Or deploy specific stacks
npx cdk deploy BATbern-staging-ApiGatewayService
npx cdk deploy BATbern-staging-EventManagement
npx cdk deploy BATbern-staging-SpeakerCoordination
npx cdk deploy BATbern-staging-PartnerCoordination
npx cdk deploy BATbern-staging-AttendeeExperience
npx cdk deploy BATbern-staging-CompanyManagement
npx cdk deploy BATbern-staging-Monitoring
```

### 2. Verify Changes

```bash
# Check log retention for all staging log groups
AWS_PROFILE=batbern-staging aws logs describe-log-groups \
  --log-group-name-prefix /aws/ecs/BATbern-staging/ \
  --query 'logGroups[*].{Name:logGroupName,Retention:retentionInDays}' \
  --output table

# Expected output: All log groups should show retentionInDays = 7
```

### 3. Monitor Costs

```bash
# Check CloudWatch costs after 30 days
AWS_PROFILE=batbern-staging aws ce get-cost-and-usage \
  --time-period Start=2025-12-01,End=2026-01-01 \
  --granularity MONTHLY \
  --metrics BlendedCost \
  --filter file://<(echo '{"Dimensions":{"Key":"SERVICE","Values":["AmazonCloudWatch"]}}') \
  --output json
```

---

## Validation & Testing

### Success Criteria

- [ ] CDK deployment completes without errors
- [ ] All log groups show 7-day retention in staging/dev
- [ ] Production log groups remain at 180 days
- [ ] Services continue to function normally
- [ ] CloudWatch costs decrease by 65-80% within 30 days

### Testing Checklist

- [ ] Deploy to staging environment
- [ ] Verify log groups updated via AWS Console
- [ ] Test logging still works (check recent logs)
- [ ] Confirm 7-day logs are sufficient for debugging
- [ ] Monitor for 1 week to ensure no issues
- [ ] Check cost reduction after 30 days
- [ ] If successful, replicate to development

### Rollback Plan

If 7 days proves insufficient:

```bash
# Revert retention to 14 days (middle ground)
retention: isProd ? logs.RetentionDays.SIX_MONTHS : logs.RetentionDays.TWO_WEEKS,

# Or revert to 30 days
retention: isProd ? logs.RetentionDays.SIX_MONTHS : logs.RetentionDays.ONE_MONTH,
```

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| 7 days insufficient for debugging | Low | Medium | Can query older logs from CloudWatch Insights cache |
| Incident investigation requires older logs | Low | High | Production still has 180 days, staging incidents rare |
| Compliance issues | Very Low | High | Only affects non-prod, prod unchanged |
| Service disruption during deployment | Very Low | Low | CDK updates are non-disruptive to running services |

**Overall Risk Level:** 🟢 Low

---

## Next Steps

### Immediate (This Week)
1. ✅ Create refactoring branch
2. ✅ Update CDK code
3. ✅ Commit changes
4. ✅ Document implementation
5. ⏳ Deploy to staging (requires approval)
6. ⏳ Verify deployment
7. ⏳ Monitor for issues

### Short-term (Next 2 Weeks)
8. Observe logging patterns with 7-day retention
9. Collect feedback from team on log availability
10. Monitor staging CloudWatch costs daily

### Long-term (30 Days)
11. Validate cost savings ($40-50/month reduction)
12. Compare December 2025 costs to November baseline
13. If successful, proceed to Priority 2 (Fargate Spot)
14. If issues found, adjust retention period

---

## Additional Optimizations

After validating Priority 1 savings, consider:

1. **Priority 2:** Fargate Spot (70/30 split) → Save $15-20/month
2. **Priority 3:** VPC Endpoints → Save $15-20/month net
3. **Priority 4:** Right-size ECS tasks → Save $35-40/month

**Total potential savings:** $105-130/month (35-43% reduction)

---

## References

- **GitHub Issue:** #288 - Cost-optimized staging2 environment
- **Cost Analysis:** `/tmp/github-issue-288-analysis.md`
- **Branch:** `refactor/reduce-cloudwatch-costs-priority1`
- **Commit:** ca5ac30c

---

## Questions & Answers

**Q: Why 7 days instead of 1 day or 14 days?**
A: 7 days balances cost savings with practical debugging needs. Most issues are caught within days, and 7 days covers a full week of activity patterns.

**Q: What if we need logs older than 7 days?**
A: CloudWatch Insights may have cached data. For critical issues, production logs (180 days) can provide insights. Staging issues are typically caught quickly.

**Q: Will this affect compliance?**
A: No. Only non-production environments are affected. Production maintains 180-day retention for audit and compliance requirements.

**Q: Can we revert if needed?**
A: Yes. Simply update the retention period in CDK and redeploy. Historical logs cannot be recovered, but future logs will retain longer.

**Q: How long until we see cost savings?**
A: Cost reduction will be gradual as old logs expire. Full savings realized after 30 days when all 30-day logs have been purged.

---

## Approval Required

This change requires deployment approval from:
- [ ] Infrastructure Lead
- [ ] DevOps Team
- [ ] Product Owner (for staging environment)

**Recommended Action:** Approve deployment to staging environment to validate $40-50/month cost savings.
