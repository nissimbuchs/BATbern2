# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the AWS CDK infrastructure-as-code for the BATbern platform, a multi-environment polyglot monorepo supporting development, staging, and production environments across dedicated AWS accounts.

**Architecture**: Multi-tier AWS infrastructure with microservices on ECS Fargate, RDS PostgreSQL, S3/CloudFront, and Cognito authentication.

## Essential Commands

### Build and Test
```bash
npm install                    # Install dependencies
npm run build                  # Compile TypeScript
npm test                       # Run all tests
npm run test:watch             # Watch mode for tests
```

### Deployment Commands
```bash
# Synthesize CloudFormation templates (preview changes)
npm run synth:dev              # Development environment
npm run synth:staging          # Staging environment
npm run synth:prod             # Production environment

# Deploy infrastructure
npm run deploy:dev             # Deploy all stacks to development
npm run deploy:staging         # Deploy all stacks to staging
npm run deploy:prod            # Deploy all stacks to production (requires approval)

# Selective staging deployments (faster iterations)
npm run deploy:staging:microservices    # Deploy only microservices (6 concurrent)
npm run deploy:staging:infra           # Deploy only infrastructure
npm run deploy:staging:frontend        # Deploy only frontend
npm run deploy:staging:hotswap         # Fast deployment using hotswap for Lambda/ECS

# Layer-based deployments (optimized parallelization)
npm run deploy:staging:layer0-foundation     # CICD, DNS, Monitoring, SES (4 concurrent)
npm run deploy:staging:layer1-infrastructure # Network, Secrets, EventBus (3 concurrent)
npm run deploy:staging:layer2-data          # Database, Storage, Bastion (2 concurrent)
npm run deploy:staging:layer3-application   # Cognito, Cluster (2 concurrent)
npm run deploy:staging:layer4-services      # All microservices (6 concurrent)
npm run deploy:staging:layer5-gateway       # API Gateway, Frontend (2 concurrent)
npm run deploy:staging:all-layers           # Deploy all layers sequentially

# View differences before deploying
npm run diff:dev               # Compare development
npm run diff:staging           # Compare staging
npm run diff:prod              # Compare production

# Destroy infrastructure (dev/staging only)
npm run destroy:dev
npm run destroy:staging
```

### Running Single Tests
```bash
# Run specific stack test
npm test -- network-stack.test.ts
npm test -- database-stack.test.ts
npm test -- cognito-stack.test.ts

# Run specific Lambda trigger test
npm test -- lambda/pre-token-generation.test.ts
```

## Deployment Optimization Strategies

The deployment workflow has been optimized for speed and efficiency using a three-tier approach:

### Tier 1: Fast-Path Deployment (2-5 minutes) 🚀

**When**: Code-only changes (no infrastructure, Dockerfile, or migration changes)
**How**: Directly updates ECS task definitions, bypassing CloudFormation entirely
**Speed**: 90-95% faster than standard deployment

The GitHub Actions workflow automatically detects code-only changes and uses the fast-path:
```bash
# Conditions for fast-path:
# 1. No infrastructure changes (lib/stacks, bin/, package.json)
# 2. No Dockerfile changes
# 3. No database migrations (db/migration/V*.sql)
# 4. Only service code changes
```

**Manual fast-path deployment** (emergency use only):
```bash
./scripts/ci/update-ecs-task.sh batbern-staging <service-name> <image-tag>
```

### Tier 2: Hotswap Deployment (10-20 minutes) ⚡

**When**: Service changes without infrastructure changes
**How**: Uses CDK hotswap mode to update Lambda/ECS without CloudFormation changeset
**Speed**: 50-60% faster than standard deployment

Automatically used by GitHub Actions when:
- Service code changes detected
- No infrastructure changes
- Dockerfile or migrations present (prevents fast-path)

### Tier 3: Layer-Based Deployment (20-30 minutes) 🏗️

**When**: Infrastructure changes detected
**How**: Deploys stacks in layers with maximum parallelization per layer
**Speed**: 30-40% faster than sequential deployment

Used by GitHub Actions for infrastructure changes:
- Layer 0: Foundation (CICD, DNS, Monitoring, SES) - 4 concurrent
- Layer 1: Infrastructure (Network, Secrets, EventBus) - 3 concurrent
- Layer 2: Data (Database, Storage, Bastion) - 2 concurrent
- Layer 3: Application (Cognito, Cluster) - 2 concurrent
- Layer 4: Services (All 6 microservices) - 6 concurrent
- Layer 5: Gateway (API Gateway, Frontend) - 2 concurrent

### Deployment Decision Tree

```
┌─────────────────────────────────────┐
│   Change Detection                  │
└──────────┬──────────────────────────┘
           │
    ┌──────┴───────┐
    │ Infrastructure│  YES → Layer-Based Deployment (20-30 min)
    │   changes?    │
    └──────┬────────┘
           │ NO
    ┌──────┴────────┐
    │  Dockerfile or│  YES → Hotswap Deployment (10-20 min)
    │  migrations?  │
    └──────┬────────┘
           │ NO
    ┌──────┴────────┐
    │  Code-only    │  YES → Fast-Path Deployment (2-5 min) 🚀
    │  changes?     │
    └───────────────┘
```

### Optimization Features

1. **Increased Concurrency**: Microservices deploy with `--concurrency=6` (up from 3)
2. **Smart RDS Snapshots**: Only create snapshots when V*.sql migrations detected
3. **Mid-Deployment Re-Auth**: Prevents AWS token expiration on long deployments
4. **Direct IMAGE_TAG**: Eliminates GitHub API call overhead

### Testing Fast-Path Deployment

To test fast-path locally:
```bash
# Make code-only change to a service
cd services/event-management-service/src/main/java/...
# Edit some business logic

# Commit and push (GitHub Actions will detect and use fast-path)
git add .
git commit -m "fix(event): improve validation logic"
git push origin develop

# Watch workflow - should complete in 2-5 minutes
```

## Architecture Overview

### Stack Deployment Order

**First Time Setup:**
0. **CICD Stack** - GitHub Actions IAM roles and ECR repositories (deploy first for CI/CD)

**Core Infrastructure:**
1. **DNS Stack** - Route53 hosted zones, ACM certificates (staging/prod only, us-east-1 for CloudFront)
2. **Network Stack** - VPC, subnets, security groups, NAT gateways
3. **Secrets Stack** - Secrets Manager, KMS keys for database credentials
4. **Database Stack** - RDS PostgreSQL (depends on Network + Secrets)
5. **Storage Stack** - S3 buckets, CloudFront CDN with custom domain
6. **EventBus Stack** - EventBridge for domain events
7. **Monitoring Stack** - CloudWatch dashboards, alarms, GitHub Issues integration
8. **Cognito Stack** - User pools with Lambda triggers (depends on Network + Database)
9. **SES Stack** - Email templates for authentication workflows

**Application Layer (staging/prod only):**
10. **Cluster Stack** - Shared ECS Fargate cluster
11. **Microservices Stacks** - Event Management, Speaker Coordination, Partner Coordination, Attendee Experience, Company Management
12. **API Gateway Service Stack** - Spring Boot API Gateway on ECS with Service Connect
13. **API Gateway Stack** - AWS API Gateway proxy to Spring Boot (depends on Cognito)
14. **Frontend Stack** - React app on S3 + CloudFront (depends on DNS)
15. **AutoShutdown Stack** - Development cost optimization (scales ECS to 0 outside business hours)

**Note**: CDK automatically handles dependencies when using `--all` flag.

### Environment-Based Deployment

The infrastructure uses `DeploymentMode` to determine what to deploy:

- **Development** (`LOCAL` mode): No AWS infrastructure deployed. Services run locally in Docker or natively, using production Cognito/S3.
- **Production** (`CLOUD` mode, envName `staging`): Deploys full AWS infrastructure including ECS microservices, API Gateway, and Frontend. Serves www.batbern.ch.

This is controlled by `EnvironmentHelper.shouldDeployWebInfrastructure()` in `lib/config/environment-config.ts`.

## Multi-Environment Configuration

### AWS Account Mapping (Consolidated)
- **Development**: Local only (no AWS account; uses production Cognito/S3)
- **Production**: 188701360969 (eu-central-1) — CDK `envName: 'staging'`, `isProduction: true`
- **Management**: 510187933511 — domain registration only (profile `batbern-mgmt`)

> The former production account (422940799530) is decommissioned. CloudFormation stacks retain `BATbern-staging-*` names.

### Environment Configs
- `dev-config.ts` - Local development (no AWS infrastructure deployed)
- `staging-config.ts` - Production config (domains: batbern.ch, RDS deletion protection, 14-day backups)

### AWS Profile Setup
The npm scripts use the `batbern-staging` profile for all deployments (production runs in the staging account):

Configure profiles in `~/.aws/config`:
```ini
[profile batbern-staging]
role_arn = arn:aws:iam::188701360969:role/OrganizationAccountAccessRole
source_profile = YOUR_SOURCE_PROFILE
region = eu-central-1
```

## Key Architectural Patterns

### 1. DNS Architecture (Consolidated)
- **Production (staging account)**: Owns `batbern.ch` hosted zone (migrated from former production account)
- **Domains**: www.batbern.ch, api.batbern.ch, cdn.batbern.ch
- **Certificates**: Created in us-east-1 (CloudFront requirement) via DnsStack
- **Cross-Region**: Uses `crossRegionReferences: true` to reference us-east-1 certificates from eu-central-1 stacks
- **Domain Registration**: Managed in AWS management account (510187933511, profile `batbern-mgmt`)

### 2. ECS Service Connect (Microservices)
- All microservices use **Service Connect** for service-to-service discovery
- No ALB URLs needed for inter-service communication
- DNS names: `{service-name}.batbern-{env}` (e.g., `event-management.batbern-staging`)
- API Gateway Service uses Service Connect DNS to route to microservices

### 3. Cognito Lambda Triggers with Database Access
Cognito Lambda triggers (post-confirmation, pre-authentication, pre-token-generation) require:
- VPC access via `lambdaTriggersSecurityGroup` from NetworkStack
- Database connection via `databaseSecret` and `databaseEndpoint` from DatabaseStack
- This is why CognitoStack depends on both NetworkStack and DatabaseStack

### 4. Shared Resources
- **ECS Cluster**: Single Fargate cluster shared by all microservices (ClusterStack)
- **EventBridge**: Shared event bus for domain events (EventBusStack)
- **S3 Content Bucket**: Shared by Event Management and Company Management for file uploads
- **CloudFront Distribution**: Single CDN for all S3 content

### 5. GitHub Actions Integration
- **CICD Stack** creates IAM role with OIDC provider for GitHub Actions
- **ECR Repositories**: One per microservice (event-management, speaker-coordination, etc.)
- **Role ARN**: `arn:aws:iam::188701360969:role/batbern-staging-github-actions-role`

## Critical Development Standards

### Stack Dependencies
**ALWAYS** declare stack dependencies explicitly using `addDependency()`:
```typescript
databaseStack.addDependency(networkStack);
databaseStack.addDependency(secretsStack);
cognitoStack.addDependency(networkStack);
cognitoStack.addDependency(databaseStack);
```

This ensures proper CloudFormation stack creation order.

### Environment Context
**ALWAYS** pass environment via `--context environment={env}` flag:
```bash
cdk synth --context environment=staging
```

The app validates that AWS credentials match expected account for environment to prevent accidental cross-environment deployments.

### Cross-Region References
When referencing resources across regions (e.g., us-east-1 certificate in eu-central-1 stack):
```typescript
new FrontendStack(app, `${stackPrefix}-Frontend`, {
  certificateArn: dnsStack?.certificate.certificateArn,
  crossRegionReferences: true,  // Required for cross-region
});
```

### Conditional Stack Deployment
Use `EnvironmentHelper` for environment-specific stack creation:
```typescript
if (EnvironmentHelper.shouldDeployWebInfrastructure(config.envName)) {
  // Only create for staging/production
  const clusterStack = new ClusterStack(...);
}
```

### Testing Infrastructure
**CRITICAL**: Follow TDD for infrastructure changes:
1. Write test first in `test/unit/{stack-name}.test.ts`
2. Implement stack changes
3. Ensure tests pass

All stacks should have:
- Resource existence tests
- Tag verification tests
- Security configuration tests
- Dependency validation tests

## Project Structure

```
infrastructure/
├── bin/
│   └── batbern-infrastructure.ts    # CDK app entry point, stack orchestration
├── lib/
│   ├── config/
│   │   ├── environment-config.ts    # Interface, DeploymentMode, EnvironmentHelper
│   │   ├── dev-config.ts           # Development environment config
│   │   ├── staging-config.ts       # Staging environment config
│   │   └── prod-config.ts          # Production environment config
│   └── stacks/
│       ├── cicd-stack.ts           # ECR + GitHub Actions IAM
│       ├── dns-stack.ts            # Route53 + ACM (us-east-1)
│       ├── network-stack.ts        # VPC, security groups
│       ├── secrets-stack.ts        # Secrets Manager, KMS
│       ├── database-stack.ts       # RDS PostgreSQL
│       ├── storage-stack.ts        # S3, CloudFront
│       ├── event-bus-stack.ts      # EventBridge
│       ├── monitoring-stack.ts     # CloudWatch, GitHub Issues
│       ├── cognito-stack.ts        # User pools + Lambda triggers
│       ├── ses-stack.ts            # Email templates
│       ├── cluster-stack.ts        # ECS Fargate cluster
│       ├── *-stack.ts              # Microservice stacks
│       ├── api-gateway-service-stack.ts  # Spring Boot API Gateway
│       ├── api-gateway-stack.ts    # AWS API Gateway
│       ├── frontend-stack.ts       # React app
│       ├── bastion-stack.ts        # SSM-based DB access (dev/staging)
│       └── auto-shutdown-stack.ts  # Cost optimization (dev only)
├── lambda/                         # Lambda trigger code
│   ├── post-confirmation/          # Cognito post-confirmation trigger
│   ├── pre-authentication/         # Cognito pre-authentication trigger
│   └── pre-token-generation/       # Cognito pre-token-generation trigger
├── test/
│   ├── unit/                       # Stack unit tests
│   └── e2e/                        # Infrastructure E2E tests
├── cdk.json                        # CDK configuration
├── package.json                    # Dependencies and npm scripts
└── tsconfig.json                   # TypeScript configuration
```

## Troubleshooting

### Account Mismatch Error
If you see "ACCOUNT MISMATCH DETECTED":
- Ensure you're using npm scripts: `npm run deploy:staging`
- Or set correct profile: `export AWS_PROFILE=batbern-staging`

### CDK Bootstrap Required
If deployment fails with toolkit stack error:
```bash
AWS_PROFILE=batbern-staging cdk bootstrap aws://188701360969/eu-central-1
```

### Stack Dependencies Error
If CloudFormation fails with "resource not found":
- Check `addDependency()` calls in `bin/batbern-infrastructure.ts`
- Ensure dependent stacks are deployed first

### Cross-Region Certificate Error
If certificate reference fails:
- Verify `crossRegionReferences: true` in stack props
- Ensure DnsStack deployed in us-east-1
- Confirm certificate ARN export exists

### Service Connect Not Working
If microservices can't communicate:
- Verify all services in same cluster
- Check Service Connect namespace: `batbern-{env}`
- Ensure security groups allow traffic within VPC

## Testing Standards

All infrastructure changes must include tests in `test/unit/`:

```typescript
// Stack creation test
test('creates VPC with correct CIDR', () => {
  const stack = new NetworkStack(app, 'TestStack', { config });
  const template = Template.fromStack(stack);

  template.hasResourceProperties('AWS::EC2::VPC', {
    CidrBlock: config.vpc.cidr
  });
});

// Tag verification
test('applies environment tags', () => {
  const stack = new NetworkStack(app, 'TestStack', { config });
  const template = Template.fromStack(stack);

  template.hasResourceProperties('AWS::EC2::VPC', {
    Tags: Match.arrayWith([
      { Key: 'Environment', Value: config.envName }
    ])
  });
});
```

**Coverage target**: >90% for all stacks

## Related Documentation

- Parent project: `../CLAUDE.md` - Monorepo overview and development workflow
- Architecture: `../docs/architecture/` - System architecture documentation
- README: `README.md` - Detailed deployment guide and troubleshooting
