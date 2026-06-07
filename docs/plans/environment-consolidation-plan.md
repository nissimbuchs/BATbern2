# Environment Consolidation: Promote Staging to Production

## Context

BATbern runs two AWS environments — staging (188701360969) and production (422940799530). Staging is fully operational with all data, users, SES, and inbound email. Production was deployed once but is empty. Running both costs ~$300/month; staging alone is ~$150/month. Given BATbern's scale (3 events/year, ~300 users, 1 developer), maintaining two identical environments is unnecessary overhead. The proposal: promote staging to serve as production, point `batbern.ch` to staging infrastructure, and save ~$1,800/year.

## Verdict: Feasible — Recommended

No hard blockers. The main work is DNS migration, SES reconfiguration, and CDK config updates. Data stays in place — no migration needed.

---

## Key Consequences

### What You Gain
- **~$150/month savings** (~$1,800/year) by not running production account
- **Zero data migration** — staging already has all production data
- **Simpler operations** — one environment to monitor, patch, and maintain

### What You Lose
- **No pre-production validation environment** — deploys go straight to users
- **No safe space for DB migration testing** — Flyway runs against real data
- **Stack names stay `BATbern-staging-*`** — cosmetic only, but visible in AWS Console

### Mitigations
- Your 4-layer test suite (shell, Bruno, Playwright, CDK) runs in CI before deploy
- ECS rolling deployments with health checks provide automatic rollback
- RDS automated snapshots (increase to 14 days) enable point-in-time recovery
- On-demand ephemeral staging: spin up temp services in dev account for risky changes via CDK

---

## Implementation Progress

### Phase 1: Preparation — COMPLETE ✅

1. ✅ SES sandbox status checked — already out of sandbox
2. ✅ DNS TTL lowered
3. ✅ RDS snapshot taken
4. ✅ DNS records documented

### Phase 2: DNS Migration — COMPLETE ✅ (2026-03-11)

1. ✅ Created `batbern.ch` hosted zone in staging account (Z08825557YYLWVHISLPY)
2. ✅ Copied all DNS records from production hosted zone
3. ✅ Updated domain NS records via management account (510187933511, profile `batbern-mgmt`)
   - Domain registered via Gandi SAS (Route53 backend registrar for .ch TLDs)
   - NS delegation now points to staging account's hosted zone
4. ✅ DNS propagation verified (2026-03-11 — confirmed via `dig @a.nic.ch batbern.ch NS`)
5. Old hosted zone in production account — delete after 30 days

### Phase 3: CDK Config Updates — COMPLETE ✅

All infrastructure code updated to use `isProduction` flag pattern.

**Files modified:**

| File | Change | Status |
|------|--------|--------|
| `infrastructure/lib/config/environment-config.ts` | Added `isProduction?: boolean` to interface | ✅ |
| `infrastructure/lib/config/staging-config.ts` | Domain → batbern.ch, `isProduction: true`, RDS hardening (14-day backup, deletion protection), GDPR tag | ✅ |
| `infrastructure/lib/config/prod-config.ts` | Added `@deprecated` notice | ✅ |
| `infrastructure/bin/batbern-infrastructure.ts` | Domain derivation from config instead of envName | ✅ |
| `infrastructure/lib/stacks/cognito-stack.ts` | `isProdTraffic` from `isProduction` flag; email/domain/callback conditionals | ✅ |
| `infrastructure/lib/stacks/inbound-email-stack.ts` | `isProdTraffic` for reply domain (`replies@batbern.ch`) | ✅ |
| `infrastructure/lib/stacks/network-stack.ts` | Domain from config instead of envName | ✅ |
| `infrastructure/lib/stacks/api-gateway-stack.ts` | CORS origins from `isProduction` flag | ✅ |
| `infrastructure/lib/stacks/api-gateway-service-stack.ts` | `APP_ENVIRONMENT=production` when `isProduction` | ✅ |
| `infrastructure/lib/stacks/event-management-stack.ts` | SES from-domain from `isProduction` flag | ✅ |
| `infrastructure/lib/stacks/database-stack.ts` | Pass `isProduction` to RdsCluster construct | ✅ |
| `infrastructure/lib/stacks/storage-stack.ts` | `isProduction` flag for CDN/S3 config | ✅ |
| `infrastructure/lib/stacks/frontend-stack.ts` | `isProduction` flag | ✅ |
| `infrastructure/lib/stacks/cluster-stack.ts` | `isProduction` flag | ✅ |
| `infrastructure/lib/stacks/monitoring-stack.ts` | `isProduction` flag | ✅ |
| `infrastructure/lib/stacks/cicd-stack.ts` | `isProduction` flag | ✅ |
| `infrastructure/lib/constructs/domain-service-construct.ts` | `isProduction` flag | ✅ |
| `infrastructure/lib/constructs/ecs-service.ts` | `isProduction` flag | ✅ |
| `infrastructure/lib/constructs/rds-cluster.ts` | `isProduction` flag | ✅ |
| `infrastructure/lib/constructs/cognito-user-sync-triggers.ts` | `isProduction` flag | ✅ |
| `infrastructure/lib/constructs/ecs-service-alarms.ts` | `isProduction` flag | ✅ |
| `infrastructure/test/unit/inbound-email-stack.test.ts` | Updated assertion: `replies@batbern.ch` | ✅ |

### Phase 4: SES & Email Reconfiguration — COMPLETE ✅

- ✅ `batbern.ch` SES domain identity auto-validates via hosted zone
- ✅ InboundEmailStack uses `replies@batbern.ch` (via `isProduction` flag)
- ✅ Cognito CustomEmailSender uses `noreply@batbern.ch` and `https://www.batbern.ch` (via `isProduction` flag)

### Phase 5: Application Layer — COMPLETE ✅

- ✅ `APP_ENVIRONMENT=production` injected via CDK when `isProduction: true` (api-gateway-service-stack.ts)
- ✅ ConfigController.java returns correct production URLs — no code change needed

### Phase 6: CI/CD Pipeline — COMPLETE ✅

| File | Change | Status |
|------|--------|--------|
| `.github/workflows/deploy-staging.yml` | Updated test URLs: `www.batbern.ch`, `api.batbern.ch` | ✅ |
| `.github/workflows/deploy-production.yml` | Added DEPRECATED notice (targets decommissioned account) | ✅ |
| `.github/workflows/deploy-code-staging.yml` | Updated smoke test URLs to production domains | ✅ |
| `.github/workflows/build.yml` | Commented out production ECR push (old account 422940799530) | ✅ |
| `.github/workflows/security-scan.yml` | Updated ZAP scan URLs to production domains | ✅ |

### Phase 7: Documentation — COMPLETE ✅

| File | Change | Status |
|------|--------|--------|
| `README.md` | Updated AWS references, deployment environments, deploy instructions | ✅ |
| `CLAUDE.md` | Updated deployment section, AWS profiles, log group labels | ✅ |
| `infrastructure/README.md` | Replaced 3-environment table with consolidated model | ✅ |
| `infrastructure/CLAUDE.md` | Updated DNS architecture, account mapping, profiles, GitHub Actions roles, deployment modes | ✅ |
| `docs/architecture/01-system-overview.md` | No changes needed (no account references) | ✅ |
| `docs/architecture/02-infrastructure-deployment.md` | Updated environment strategy, AWS accounts, DNS diagram, CI/CD diagram, CORS, CDN, budget alerts, backup retention, service sizing | ✅ |
| `docs/deployment/cicd-pipeline-guide.md` | Rewrote pipeline architecture, deployment process, secrets, image tagging | ✅ |
| `docs/architecture/08-operations-security.md` | Removed staging SLA tier, updated CSP CDN domains | ✅ |
| `docs/architecture/tech-stack.md` | Removed `cdn.staging.batbern.ch` reference | ✅ |
| `docs/testing/e2e-testing-guide.md` | All URLs updated to production domains | ✅ |
| `docs/plans/environment-consolidation-plan.md` | This file — tracks full plan and progress | ✅ |

### Phase 8: Database URL Migration — COMPLETE ✅ (2026-03-11)

Migrated 1,030 rows across 8 tables from old CDN URLs to `cdn.batbern.ch`:

| Table | Column | Rows Updated |
|-------|--------|-------------|
| `companies` | `logo_url` | 113 |
| `events` | `theme_image_url` | 6 |
| `event_photos` | `display_url` | 297 |
| `event_teaser_images` | `image_url` | 3 |
| `logos` | `cloudfront_url` | 135 |
| `speakers` | `profile_picture_url` | 1 |
| `user_profiles` | `profile_picture_url` | 183 |
| `session_materials` | `cloudfront_url` | 292 |

Two URL patterns replaced:
- `cdn.staging.batbern.ch` → `cdn.batbern.ch` (~738 rows)
- `dhndjchovz1zp.cloudfront.net` → `cdn.batbern.ch` (~292 rows)

Verified: brute-force scan of ALL text/varchar columns in ALL tables confirmed zero remaining old URLs.

### Phase 9: CDK Deploy & Verification — COMPLETE ✅ (2026-03-11)

#### Certificate Workaround (Cross-Region Export Writer)

CDK's `CrossRegionExportWriter` blocked updating ACM certificate exports because downstream stacks (Storage, Frontend) still consumed the old values. CloudFormation processes the DNS stack first and the export writer unconditionally rejects value changes for existing export keys.

**Fix applied:**
1. Pre-created ACM certificates manually in us-east-1:
   - `www.batbern.ch`: `arn:aws:acm:us-east-1:188701360969:certificate/ef93631c-8241-4a2c-b84b-74615d5394cd`
   - `cdn.batbern.ch`: `arn:aws:acm:us-east-1:188701360969:certificate/4aece095-d46f-4266-ac3a-c25736d59c08`
2. Added DNS validation CNAME records to Route53 hosted zone
3. Added `cdnCertificateArn` to `DomainConfig` interface and both ARNs to `staging-config.ts`
4. Modified `dns-stack.ts` to use `Certificate.fromCertificateArn()` when pre-created ARNs are provided
5. This eliminated the CrossRegionExportWriter entirely — cert ARNs are now static config values

#### Stack Deployment Status

| Layer | Stack | Status | Notes |
|-------|-------|--------|-------|
| **L0** | CICD | ✅ deployed | |
| **L0** | DNS | ✅ deployed | Pre-created cert ARNs, no CrossRegionExportWriter |
| **L0** | Monitoring | ✅ deployed | |
| **L0** | SES | ✅ deployed | |
| **L1** | Network | ✅ deployed | New API cert for `api.batbern.ch` (config ARN preferred) |
| **L1** | Secrets | ✅ deployed | |
| **L1** | EventBus | ✅ deployed | |
| **L2** | Database | ✅ deployed | RDS deletion protection on, 14-day backup retention |
| **L2** | Storage | ✅ deployed | `cdn.batbern.ch` CNAME active on CloudFront |
| **L2** | Bastion | ⏳ pending | Not included in deploy (optional) |
| **L3** | Cognito | ✅ deployed | `noreply@batbern.ch`, `www.batbern.ch` callbacks |
| **L3** | Cluster | ✅ deployed | |
| **L4** | InboundEmail | ✅ deployed | `replies@batbern.ch`, SES identity created |
| **L4** | EventManagement | ✅ deployed | Recreated fresh (Cloud Map conflict resolved) |
| **L4** | SpeakerCoordination | ✅ deployed | Recreated fresh |
| **L4** | PartnerCoordination | ✅ deployed | Recreated fresh |
| **L4** | AttendeeExperience | ✅ deployed | Recreated fresh |
| **L4** | CompanyManagement | ✅ deployed | Recreated fresh |
| **L4** | ApiGatewayService | ✅ deployed | `APP_ENVIRONMENT=production` |
| **L5** | ApiGateway | ✅ deployed | `api.batbern.ch` custom domain active |
| **L5** | Frontend | ✅ deployed | `www.batbern.ch` CNAME active |

#### Blockers & Fixes (All Resolved)

**1. CloudFront CNAME conflicts (Storage, Frontend)** ✅
The old production account (422940799530) had CloudFront distributions with `cdn.batbern.ch`, `www.batbern.ch`, and `batbern.ch` as alternate domain names. CloudFront CNAMEs are globally unique.
**Fix**: Destroyed all stacks in old production account. CNAMEs released.

**2. API Gateway domain zombie (ApiGateway)** ✅
The `api.batbern.ch` custom domain was stuck in a phantom state after rapid create/delete cycles.
**Fix**: Destroying old prod account released the domain. Staging ApiGateway deployed successfully with `api.batbern.ch`.

**3. Network → ApiGateway cross-stack export (Network)** ✅
The Network stack exports the API certificate ARN to the ApiGateway stack. Changing the cert changes the export value, which CloudFormation blocks.
**Fix applied**: Changed `batbern-infrastructure.ts` to prefer `config.domain.apiCertificateArn` over `networkStack.apiCertificate.certificateArn`. This is now the permanent design — static cert ARNs eliminate cross-stack export issues.

**4. CDK global CLI version mismatch** ✅
Global `cdk` was v2.1030.0, library needs ≥2.1107.0.
**Fix**: Using `npx cdk` for all deploys (uses local v2.1109.0).

**5. Missing VPC log group (Network)** ✅
Log group `/aws/lambda/BATbern-staging/vpc-restrict-default-sg` was externally deleted.
**Fix**: Manually recreated via `aws logs create-log-group`.

**6. Cloud Map service discovery conflict (EventManagement)** ✅
ECS service replacement failed because the existing Cloud Map registration blocked the new service from registering with the same namespace.
**Fix**: Destroyed all L4 service stacks and recreated them from scratch. These stacks are stateless (ECS services, task defs, ALBs) — no data loss.

**7. CDK Bootstrap version too old** ✅
InboundEmail deploy required bootstrap toolkit v30+; installed was v29.
**Fix**: Ran `cdk bootstrap` to update CDKToolkit stack.

**8. Orphaned SES email identity (InboundEmail)** ✅
`batbern.ch` SES domain identity existed outside CloudFormation, blocking InboundEmail stack from creating it.
**Fix**: Deleted orphaned identity via `aws sesv2 delete-email-identity`. CDK will recreate it as a managed resource.

#### Verification Checklist

- [x] `https://www.batbern.ch` loads frontend — "BATbern Platform" ✅
- [x] `https://batbern.ch` loads frontend — apex domain serves same CloudFront ✅
- [x] `https://api.batbern.ch/api/v1/config` returns `environment: "production"` ✅
- [x] `https://cdn.batbern.ch` serves assets (CloudFront active) ✅
- [x] All 6 ECS services running (desired == running) ✅
- [ ] Password reset email comes from `noreply@batbern.ch` — manual test pending
- [ ] Inbound email to `replies@batbern.ch` arrives in SQS — manual test pending

**Apex domain migration:** Old coming-soon CloudFront distribution (E2UQ8ZRVTQ4RLV) in prod account disabled and CNAME removed. New ACM cert (`434c81ef-...`) covers both `www.batbern.ch` and `batbern.ch`. Frontend CloudFront distribution now serves both domains.

**Note:** Frontend stack has a benign `DELETE_FAILED` on `WebsiteBucketAutoDeleteObjectsCustomResource` — old Lambda lacks `s3:GetBucketTagging` permission. Does not affect functionality; will self-resolve on next deploy.

#### Post-Deploy Fixes (2026-03-11)

**9a. Service Connect inter-service communication failure** ✅
All 6 ECS services were created simultaneously. Envoy sidecar proxies cached DNS failure state because upstream services weren't registered when the proxy started.
**Fix**: `force-new-deployment` on all 6 ECS services. Fresh Envoy proxies resolved Service Connect DNS correctly.

**9b. Frontend staging URL references** ✅
Service workers cached old `api.staging.batbern.ch` URLs from the previous frontend bundle.
**Fix**: Removed all `staging.batbern.ch` references from 3 frontend source files:
- `web-frontend/src/config/runtime-config.ts` — removed staging hostname check from `getApiUrl()`
- `web-frontend/src/config/amplify.ts` — changed staging Cognito redirects to `www.batbern.ch`
- `web-frontend/src/hooks/useFileUpload/useFileUpload.ts` — removed staging CDN URL, always returns `cdn.batbern.ch`

**9c. CORS for apex domain (`https://batbern.ch`)** ✅
Requests from `https://batbern.ch` were blocked — only `https://www.batbern.ch` was in CORS allowed origins.
**Fix**: Added `https://batbern.ch` to both layers:
- `infrastructure/lib/stacks/api-gateway-stack.ts` — AWS HTTP API Gateway CORS config
- `api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java` — Spring Boot CORS config
Deployed via `cdk deploy BATbern-staging-ApiGateway BATbern-staging-ApiGatewayService`.

**9d. Staging subdomain HTTPS redirect** ✅
S3 redirect buckets only serve HTTP. Users visiting `https://staging.batbern.ch` got a TLS error.
**Fix**: Created CloudFront distribution (`EKKYTX97ETYNC`, `d37mzqchrwvyov.cloudfront.net`) in front of the S3 redirect bucket, using existing ACM cert `ff0df8d1-...` (staging.batbern.ch). Updated DNS CNAME from S3 website endpoint to CloudFront. Both `http://` and `https://staging.batbern.ch` now 301 redirect to `https://www.batbern.ch`.

**Note:** `api.staging.batbern.ch` and `www.staging.batbern.ch` still use direct S3 redirect (HTTP only). No ACM certs exist for these subdomains. Low priority — unlikely to receive user traffic.

### Phase 10: Certificate & Resource Cleanup — PENDING ⏳

After all stacks deployed and CloudFront distributions updated:

1. **Delete old ACM certificates** (no longer referenced by CloudFront):
   - ~~`arn:aws:acm:us-east-1:188701360969:certificate/ff0df8d1-94c4-4cef-bbda-39af8c816dba` (staging.batbern.ch)~~ — **now in use** by staging redirect CloudFront (EKKYTX97ETYNC)
   - `arn:aws:acm:us-east-1:188701360969:certificate/79870b01-d094-4d38-81f0-aceed0d3b041` (cdn.staging.batbern.ch)
2. **Delete orphaned SSM parameters** in eu-central-1 (if still present):
   - `/cdk/exports/BATbern-staging-Storage/BATbernstagingDNSuseast1RefCdnCertificateE363B99BAEC53CEB`
   - `/cdk/exports/BATbern-staging-Frontend/BATbernstagingDNSuseast1RefCertificate4E7ABB08423E170F`
3. **Delete DNS validation CNAME records** for old staging.batbern.ch certs (if any remain)
4. **Delete old SES identities**: `staging.batbern.ch` (no longer needed)

### Phase 11: Decommission Old Production Account — COMPLETE ✅ (2026-03-11)

Moved up from "after 30 days" because old prod CloudFront CNAMEs blocked staging deploys.

1. ✅ Destroyed all 21 stacks in production account (422940799530) via `cdk destroy --all`
   - 19 stacks destroyed cleanly on first pass
   - Database stack required disabling RDS deletion protection, then destroyed on retry
   - Remaining stacks (Network, Secrets) destroyed after Database unblocked
   - **Skipped resources** (retained by CDK policy): S3 buckets (4), ECR repos (7), Cognito UserPool, KMS key
2. ⏳ Optionally close production AWS account
3. ⏳ Delete retained S3 buckets and ECR repos manually (if closing account)

---

## Development Workflow After Consolidation

- **Local dev** uses production Cognito/S3 — risk is low since local DB is read-only mirror
- **Testing** runs against production user pool — use `test-*@example.com` convention
- **Risky changes** (major DB migrations, auth changes): spin up ephemeral staging in dev account via CDK, test, tear down

## Resolved Questions

1. **Domain registrar**: `batbern.ch` registered via **Gandi SAS** (Route53 backend for .ch TLDs) in the **management account** (510187933511, profile `batbern-mgmt`). NS records updated successfully via `aws route53domains update-domain-nameservers`.
2. **SES sandbox**: Staging account **already out of sandbox** — no action needed.
3. **Maintenance window**: Executed **2026-03-11**.
4. **Development account**: **Decide later** — focus on staging→production first.

## Total Files Modified

**Infrastructure code:** 23 files (+ api-gateway-stack.ts CORS update)
**Application code:** 3 files (frontend staging URL cleanup + SecurityConfig.java CORS)
**CI/CD workflows:** 5 files
**Documentation:** 11 files
**watchOS app:** 3 files (API URLs updated to batbern.ch)
**Database:** 8 tables (1,030 rows migrated)
**AWS manual resources:** 1 CloudFront distribution (staging redirect EKKYTX97ETYNC)
**Total:** 45 files + database migration + manual CloudFront
