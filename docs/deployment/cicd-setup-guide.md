# CI/CD Pipeline Manual Setup Guide

**Story**: 1.4 - CI/CD Pipeline Implementation
**Purpose**: Complete the infrastructure setup to activate the CI/CD pipeline
**Status**: DEPRECATED - Use Automated Setup Instead

---

## ⚠️ Important Notice

**This guide is deprecated.** Use the automated setup instead:

📄 **[CI/CD Automated Setup Guide](./cicd-automated-setup.md)**

The new approach uses:
- ✅ **CDK** for AWS infrastructure (no manual console steps)
- ✅ **gh CLI** for GitHub configuration (automated secrets)
- ✅ **Shell scripts** for one-time setup

This document is kept for reference only.

---

## Overview

The GitHub Actions workflows are implemented but require external service configuration and secrets before they can run. This guide walks through all manual setup steps in the recommended order.

---

## Setup Checklist

- [ ] 1. GitHub Secrets Configuration
- [ ] 2. AWS ECR Repository Creation
- [ ] 3. GitHub Environment Protection Rules
- [ ] 4. AWS IAM Roles for OIDC (Optional Enhancement)
- [ ] 5. SonarQube Project Setup
- [ ] 6. Snyk Security Integration
- [ ] 7. CloudWatch Dashboards (Future Enhancement)
- [ ] 8. Notification Channels (Slack/PagerDuty)
- [ ] 9. Initial Git Version Tags

---

## 1. GitHub Secrets Configuration

### Required Secrets

Navigate to: **GitHub Repo → Settings → Secrets and variables → Actions**

#### AWS Credentials (Required)
```
AWS_ACCOUNT_ID          # Your AWS account ID (e.g., 123456789012)
AWS_ACCESS_KEY_ID       # IAM user access key for deployments
AWS_SECRET_ACCESS_KEY   # IAM user secret key
AWS_REGION              # Already in workflow as env var (eu-central-1)
```

**IAM Permissions Required**:
- ECR: Full access (push/pull images)
- ECS: Full access (deploy services)
- RDS: Create snapshots, describe instances
- Route53: Update DNS records
- CloudWatch: Write logs and metrics

#### Database Credentials (Required)
```
PROD_DB_URL             # jdbc:postgresql://host:5432/dbname
PROD_DB_USER            # Database username
PROD_DB_PASSWORD        # Database password
STAGING_DB_URL          # Staging database URL
STAGING_DB_USER         # Staging database username
STAGING_DB_PASSWORD     # Staging database password
DEV_DB_URL              # Dev database URL (optional)
DEV_DB_USER             # Dev database username (optional)
DEV_DB_PASSWORD         # Dev database password (optional)
```

#### External Services (Optional but Recommended)
```
SNYK_TOKEN              # From https://app.snyk.io/account (for security scanning)
SONAR_HOST_URL          # SonarQube server URL (e.g., https://sonarcloud.io)
SONAR_TOKEN             # SonarQube authentication token
CODECOV_TOKEN           # From https://codecov.io (for coverage reports)
SLACK_WEBHOOK_URL       # Slack webhook for deployment notifications
```

### Setup Commands

**AWS Credentials** (if using AWS CLI):
```bash
# Retrieve your AWS account ID
aws sts get-caller-identity --query Account --output text

# Create IAM user for CI/CD (if not exists)
aws iam create-user --user-name github-actions-cicd

# Attach required policies
aws iam attach-user-policy --user-name github-actions-cicd \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryFullAccess
aws iam attach-user-policy --user-name github-actions-cicd \
  --policy-arn arn:aws:iam::aws:policy/AmazonECS_FullAccess

# Create access key
aws iam create-access-key --user-name github-actions-cicd
```

---

## 2. AWS ECR Repository Creation

### Services Requiring ECR Repositories

The pipeline expects these ECR repositories to exist:

1. `shared-kernel` (foundation)
2. `api-gateway` (routing layer)
3. `event-management-service`
4. `speaker-coordination-service`
5. `partner-coordination-service`
6. `attendee-experience-service`
7. `company-management-service`

### Automated Setup Script

```bash
#!/bin/bash
# Create ECR repositories for all services

AWS_REGION="eu-central-1"
SERVICES=(
  "shared-kernel"
  "api-gateway"
  "event-management-service"
  "speaker-coordination-service"
  "partner-coordination-service"
  "attendee-experience-service"
  "company-management-service"
)

for service in "${SERVICES[@]}"; do
  echo "Creating ECR repository: $service"

  aws ecr create-repository \
    --repository-name "$service" \
    --region "$AWS_REGION" \
    --image-scanning-configuration scanOnPush=true \
    --encryption-configuration encryptionType=AES256 \
    --tags Key=Project,Value=BATbern Key=ManagedBy,Value=CICD \
    2>/dev/null || echo "Repository $service already exists"

  # Set lifecycle policy to keep only last 10 images
  aws ecr put-lifecycle-policy \
    --repository-name "$service" \
    --region "$AWS_REGION" \
    --lifecycle-policy-text '{
      "rules": [{
        "rulePriority": 1,
        "description": "Keep only last 10 images",
        "selection": {
          "tagStatus": "any",
          "countType": "imageCountMoreThan",
          "countNumber": 10
        },
        "action": {"type": "expire"}
      }]
    }'
done

echo "✅ ECR repositories created successfully"
```

**Save and run**:
```bash
chmod +x scripts/ci/setup-ecr-repositories.sh
./scripts/ci/setup-ecr-repositories.sh
```

---

## 3. GitHub Environment Protection Rules

GitHub Environments control deployment approvals and environment-specific secrets.

### Setup Steps

1. Navigate to: **GitHub Repo → Settings → Environments**

2. **Create "staging" environment**:
   - Click "New environment"
   - Name: `staging`
   - Configure:
     - ✅ Required reviewers: Add 1+ team leads
     - ✅ Wait timer: 0 minutes
     - Deployment branches: `develop` and `main`

3. **Create "production" environment**:
   - Click "New environment"
   - Name: `production`
   - Configure:
     - ✅ Required reviewers: Add 2+ senior team members
     - ✅ Wait timer: 10 minutes (optional safety delay)
     - Deployment branches: `main` only

### Environment-Specific Secrets (Optional)

You can override repository secrets per environment:
- Navigate to each environment
- Click "Add secret"
- Override database URLs, API endpoints, etc.

---

## 4. AWS IAM Roles for GitHub Actions OIDC (Optional Enhancement)

**Status**: Optional - More secure than access keys
**Benefit**: No long-lived credentials in GitHub Secrets

### Why OIDC?

Instead of storing AWS access keys, GitHub can assume IAM roles directly using OpenID Connect.

### Setup Steps

```bash
# 1. Create OIDC provider in AWS
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1

# 2. Create IAM role with trust policy
cat > github-actions-trust-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::510187933511:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:nissimbuchs/BATbern2:*"
        }
      }
    }
  ]
}
EOF

aws iam create-role \
  --role-name GitHubActionsDeploymentRole \
  --assume-role-policy-document file://github-actions-trust-policy.json

# 3. Attach policies
aws iam attach-role-policy \
  --role-name GitHubActionsDeploymentRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryFullAccess

aws iam attach-role-policy \
  --role-name GitHubActionsDeploymentRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonECS_FullAccess
```

### Workflow Changes (if using OIDC)

Replace `aws-actions/configure-aws-credentials@v4` step:
```yaml
- name: Configure AWS credentials
  uses: aws-actions/configure-aws-credentials@v4
  with:
    role-to-assume: arn:aws:iam::510187933511:role/GitHubActionsDeploymentRole
    aws-region: eu-central-1
```

---

## 5. SonarQube Project Setup

### Option A: SonarCloud (Recommended for Public/Open Source)

1. Go to https://sonarcloud.io
2. Sign in with GitHub
3. Click "Analyze new project"
4. Select your repository
5. Get your token:
   - My Account → Security → Generate Token
   - Copy token to GitHub Secret: `SONAR_TOKEN`
6. Copy your organization key and project key
7. Update workflows if needed (already configured)

### Option B: Self-Hosted SonarQube

```bash
# Run SonarQube with Docker
docker run -d --name sonarqube \
  -p 9000:9000 \
  -e SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true \
  sonarqube:lts-community

# Access at http://localhost:9000
# Default credentials: admin/admin (change immediately)
```

**Configure Quality Gate**:
1. Quality Gates → Create
2. Set conditions:
   - Coverage < 90% → FAILED
   - Duplicated Lines > 3% → FAILED
   - Maintainability Rating worse than A → FAILED
   - Reliability Rating worse than A → FAILED
   - Security Rating worse than A → FAILED
3. Set as default

**Add to GitHub Secrets**:
```
SONAR_HOST_URL=https://sonarcloud.io (or your server URL)
SONAR_TOKEN=your_generated_token
```

---

## 6. Snyk Security Integration

### Setup Steps

1. Create account: https://app.snyk.io/signup
2. Connect GitHub repository
3. Generate API token:
   - Account Settings → General → API Token
4. Add to GitHub Secrets: `SNYK_TOKEN=your_token_here`
5. Enable Gradle scanning in Snyk dashboard

### Configure Snyk Policies (Optional)

Create `.snyk` file in repository root:
```yaml
# Snyk policy file
version: v1.25.0

# Ignore specific vulnerabilities (with justification)
ignore:
  # Example: Ignore low severity issue in test dependencies
  'SNYK-JAVA-ORGSPRINGFRAMEWORK-12345':
    - '*':
        reason: 'Test dependency only, not in production'
        expires: '2025-12-31T00:00:00.000Z'

# Fail build on vulnerabilities
failThreshold: high
```

---

## 7. CloudWatch Dashboards (Future Enhancement)

**Status**: Future enhancement - not blocking pipeline activation

### Recommended Metrics

Create dashboard to monitor:
- Build success/failure rates
- Build duration trends
- Deployment frequency
- Lead time for changes
- Change failure rate
- Mean time to recovery (MTTR)

### Setup Command (AWS CLI)

```bash
aws cloudwatch put-dashboard \
  --dashboard-name BATbern-CICD-Pipeline \
  --dashboard-body file://cloudwatch-dashboard.json
```

**File**: `docs/deployment/cloudwatch-dashboard.json` (to be created)

---

## 8. Notification Channels

### Slack Integration

1. **Create Slack App**:
   - Go to https://api.slack.com/apps
   - Create New App → From scratch
   - Name: "BATbern CI/CD"
   - Select workspace

2. **Enable Incoming Webhooks**:
   - Features → Incoming Webhooks → Activate
   - Add New Webhook to Workspace
   - Select channel (e.g., #deployments)
   - Copy webhook URL

3. **Add to GitHub Secrets**:
   ```
   SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/WEBHOOK/URL
   ```

### PagerDuty Integration (Optional)

1. Create integration in PagerDuty
2. Copy integration key
3. Add GitHub Secret: `PAGERDUTY_INTEGRATION_KEY`

### Test Notification

```bash
# Test Slack webhook
curl -X POST "$SLACK_WEBHOOK_URL" \
  -H 'Content-Type: application/json' \
  -d '{
    "text": "✅ CI/CD Pipeline configured successfully!",
    "blocks": [{
      "type": "section",
      "text": {
        "type": "mrkdwn",
        "text": "*Test Notification*\nCI/CD setup is complete"
      }
    }]
  }'
```

---

## 9. Initial Git Version Tags

The pipeline uses semantic versioning with Git tags.

### Create Initial Tag

```bash
# Tag the current commit as v0.1.0 (initial version)
git tag -a v0.1.0 -m "Initial CI/CD pipeline release"
git push origin v0.1.0

# Verify
git describe --tags
```

### Future Versioning Strategy

- `v0.x.x` - Pre-production development
- `v1.x.x` - Production releases
- `v1.0.0-alpha.1` - Pre-release versions
- `v1.0.0+build.123` - Build metadata

---

## Validation Checklist

After completing setup, validate the pipeline:

### 1. Test Build Pipeline
```bash
# Push a commit to develop branch
git checkout develop
git commit --allow-empty -m "test: Validate CI/CD pipeline"
git push origin develop

# Check GitHub Actions tab for workflow run
```

### 2. Test Coverage Enforcement
```bash
# Build locally should enforce 90% coverage
cd shared-kernel
./gradlew clean check
```

### 3. Test ECR Access
```bash
# Login to ECR
aws ecr get-login-password --region eu-central-1 | \
  docker login --username AWS --password-stdin \
  510187933511.dkr.ecr.eu-central-1.amazonaws.com
```

### 4. Test Secrets
- Trigger workflow manually
- Check logs for "secret not found" errors
- Verify secrets are masked in logs

---

## Troubleshooting

### Build Fails with "ECR Repository Not Found"
**Solution**: Run ECR repository creation script (Section 2)

### "AWS Credentials Not Found"
**Solution**: Verify GitHub Secrets are named exactly:
- `AWS_ACCESS_KEY_ID` (not AWS_ACCESS_KEY)
- `AWS_SECRET_ACCESS_KEY`

### SonarQube Quality Gate Fails
**Solution**:
1. Check coverage is ≥90%
2. Fix code smells and security hotspots
3. Review SonarQube dashboard for specific issues

### Deployment Requires Approval But No Option Shown
**Solution**: Ensure reviewers are added to GitHub Environment protection rules

---

## Next Steps After Setup

1. ✅ Run first build on `develop` branch
2. ✅ Verify all quality gates pass
3. ✅ Test deployment to dev environment
4. ✅ Create deployment runbook for team
5. ✅ Schedule team training on CI/CD workflows

---

## Support

- GitHub Actions Documentation: https://docs.github.com/en/actions
- AWS ECR Guide: https://docs.aws.amazon.com/ecr/
- SonarQube Documentation: https://docs.sonarqube.org/
- Snyk Documentation: https://docs.snyk.io/

---

**Last Updated**: 2025-10-01
**Maintained By**: DevOps Team
